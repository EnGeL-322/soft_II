import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ConsultaPublica } from '../../core/models/models';
import { PortalService } from '../../core/services/portal.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { CONTROLES_DEMO, ControlDemo } from './portal-demo';

@Component({
  selector: 'app-portal',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, BadgeEstadoComponent],
  templateUrl: './portal.component.html',
  styleUrls: ['./portal.component.css', './portal-results.css', './portal-responsive.css'],
})
export class PortalComponent {
  readonly controles = CONTROLES_DEMO;
  readonly zonas = [...new Set(this.controles.map((r) => r.zona))];
  readonly resumen = this.resumir(this.controles);
  readonly tarjetas = ['Quillallaka', 'I Collana', 'Faon y Yasin'].map((zona) => {
    const registros = this.controles.filter((r) => r.zona === zona);
    const retirados = registros.filter((r) => r.resultado === 'Retirado').length;
    const observados = registros.filter((r) => r.resultado === 'Observado').length;
    return {
      zona,
      ...this.resumir(registros),
      controles: registros.length,
      ultimo: registros[0].fecha.slice(5),
      retirados,
      observados,
      aptos: registros.filter((r) => r.resultado === 'Apto').length,
      estado: retirados ? 'Retirado' : observados ? 'Observado' : 'Apto',
    };
  });
  zona = '';
  estado = '';
  pagina = 1;
  porPagina = 5;
  codigo = '';
  readonly resultado = signal<ConsultaPublica | null>(null);
  readonly error = signal('');
  readonly cargando = signal(false);
  readonly consultaDemo = signal(false);

  constructor(private readonly portalService: PortalService) {}

  get filtrados() {
    return this.controles.filter(
      (r) => (!this.zona || r.zona === this.zona) && (!this.estado || r.resultado === this.estado),
    );
  }
  get paginas() {
    return Math.max(1, Math.ceil(this.filtrados.length / this.porPagina));
  }
  get numerosPagina() {
    return Array.from({ length: this.paginas }, (_, i) => i + 1);
  }
  get inicio() {
    return this.filtrados.length ? (this.pagina - 1) * this.porPagina + 1 : 0;
  }
  get fin() {
    return Math.min(this.pagina * this.porPagina, this.filtrados.length);
  }
  get visibles() {
    return this.filtrados.slice((this.pagina - 1) * this.porPagina, this.pagina * this.porPagina);
  }

  private resumir(registros: ControlDemo[]) {
    const leche = registros.reduce((s, r) => s + r.leche, 0);
    return {
      leche,
      agua: registros.reduce((s, r) => s + r.agua, 0),
      pureza: leche ? registros.reduce((s, r) => s + r.pureza * r.leche, 0) / leche : 0,
    };
  }

  buscar(): void {
    if (this.cargando()) return;
    this.error.set('');
    this.resultado.set(null);
    this.consultaDemo.set(false);
    const codigo = this.codigo.trim().toUpperCase();
    if (!codigo) {
      this.error.set('Ingresa tu código de proveedor.');
      return;
    }
    const demo = this.controles.filter((r) => r.codigo === codigo);
    if (demo.length) {
      this.consultaDemo.set(true);
      this.resultado.set({
        codigo,
        zona: demo[0].zona,
        estado: demo[0].resultado,
        historial: demo.map((r) => ({ fecha: r.fecha, resultado: r.resultado, litros: r.leche })),
      });
      return;
    }
    this.cargando.set(true);
    this.portalService
      .consultar(codigo)
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (r) => this.resultado.set(r),
        error: (e) =>
          this.error.set(
            e.status === 404
              ? 'No se encontró ese código de proveedor.'
              : 'No se pudo realizar la consulta. Intenta nuevamente en unos momentos.',
          ),
      });
  }
}
