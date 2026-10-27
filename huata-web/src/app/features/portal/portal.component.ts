import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { ConsultaPublica, IncidenciaPublica, ZonaCalidadPublica } from '../../core/models/models';
import { PortalService } from '../../core/services/portal.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

/**
 * Portal público (RF-43/RF-48/RF-49): calidad por zona y proveedores con incidencia confirmada hoy,
 * ambos leídos de {@code GET /api/v1/public/calidad/zonas} y {@code /public/incidencias/hoy}.
 *
 * <p>Antes esta pantalla mostraba una tabla completa de "controles de calidad" con litros, agua
 * detectada y pureza por proveedor — todo generado en {@code portal-demo.ts}, sin ningún respaldo
 * en el backend. Se retiró esa tabla al conectar el panel a datos reales: el backend no calcula
 * "pureza %" ni "agua en litros" por control, y publicar el detalle de cada entrega por proveedor
 * expondría más información operativa de la que pide RF-48/RF-49 (zona y, cuando hay incidencia,
 * el código del proveedor). Si el negocio quiere ese detalle público de vuelta, es una decisión de
 * producto/privacidad a confirmar (RNF-12), no algo que reintroducir en silencio.
 */
@Component({
  selector: 'app-portal',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, RouterLink, BadgeEstadoComponent],
  templateUrl: './portal.component.html',
  styleUrls: ['./portal.component.css', './portal-results.css', './portal-responsive.css'],
})
export class PortalComponent {
  readonly zonas = signal<ZonaCalidadPublica[]>([]);
  readonly incidencias = signal<IncidenciaPublica[]>([]);
  readonly cargandoResumen = signal(false);
  readonly errorResumen = signal('');

  codigo = '';
  readonly resultado = signal<ConsultaPublica | null>(null);
  readonly error = signal('');
  readonly cargando = signal(false);

  constructor(private readonly portalService: PortalService) {
    this.cargarResumen();
  }

  get totalControles(): number {
    return this.zonas().reduce((s, z) => s + z.controles, 0);
  }
  get totalAceptadas(): number {
    return this.zonas().reduce((s, z) => s + z.aceptadas, 0);
  }
  get zonasConProblema(): number {
    return this.zonas().filter((z) => z.estado !== 'APTA').length;
  }

  cargarResumen(): void {
    this.cargandoResumen.set(true);
    this.errorResumen.set('');
    this.portalService
      .incidenciasDeHoy()
      .pipe(finalize(() => this.cargandoResumen.set(false)))
      .subscribe({
        next: (r) => {
          this.zonas.set(r.zonas);
          this.incidencias.set(r.incidencias);
        },
        error: () => this.errorResumen.set('No se pudo cargar el resumen del día. Intenta nuevamente.'),
      });
  }

  buscar(): void {
    if (this.cargando()) return;
    this.error.set('');
    this.resultado.set(null);
    const codigo = this.codigo.trim().toUpperCase();
    if (!codigo) {
      this.error.set('Ingresa tu código de proveedor.');
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
