import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { RegistroAuditoria } from '../../core/models/models';
import { OperacionService, ResumenOperacion } from '../../core/services/operacion.service';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css', './dashboard-panels.css', './dashboard-tabs.css'],
})
export class DashboardComponent implements OnInit {
  readonly data = signal<ResumenOperacion | null>(null);
  readonly actividad = signal<RegistroAuditoria[]>([]);
  readonly error = signal('');
  readonly mensaje = signal('');
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  errorActividad = '';
  fecha = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  readonly hoy = this.fecha;
  litrosPlanta: number | null = null;
  observacion = '';
  tab: 'alertas' | 'rutas' | 'actividad' = 'alertas';
  private solicitud = 0;
  constructor(
    private readonly operacion: OperacionService,
    private readonly auditoria: AuditoriaService,
    protected readonly auth: AuthService,
  ) {}
  ngOnInit() {
    this.cargar();
    this.cargarActividad();
  }
  cargar() {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.fecha)) {
      this.error.set('Selecciona una fecha válida.');
      return;
    }
    const solicitud = ++this.solicitud;
    this.error.set('');
    this.mensaje.set('');
    this.cargando.set(true);
    this.data.set(null);
    this.operacion.resumen(this.fecha).subscribe({
      next: (d) => {
        if (solicitud !== this.solicitud) return;
        this.data.set(d);
        this.litrosPlanta = d.cierre?.litrosPlanta ?? null;
        this.observacion = d.cierre?.observacion ?? '';
        this.cargando.set(false);
      },
      error: () => {
        if (solicitud === this.solicitud) {
          this.error.set(
            'No se pudo cargar la operación. Comprueba la conexión y vuelve a intentar.',
          );
          this.cargando.set(false);
        }
      },
    });
  }
  cargarActividad() {
    if (!this.auth.tieneRol('ADMINISTRADOR')) return;
    this.auditoria.listar(0, 6).subscribe({
      next: (r) => {
        this.actividad.set(r.content);
        this.errorActividad = '';
      },
      error: () => (this.errorActividad = 'No se pudo cargar la actividad reciente.'),
    });
  }
  get cerrado() {
    return this.data()?.cierre?.cerrado ?? false;
  }
  get sistema() {
    return this.cerrado ? (this.data()?.cierre?.litrosSistema ?? 0) : (this.data()?.aceptados ?? 0);
  }
  get variacion() {
    return this.litrosPlanta === null ? null : this.litrosPlanta - this.sistema;
  }
  get alertas() {
    const d = this.data();
    if (!d) return [];
    const filas: { icono: string; titulo: string; detalle: string; ruta: string }[] = [];
    if (!this.cerrado)
      filas.push({
        icono: '⌁',
        titulo: 'Jornada pendiente de cierre',
        detalle: 'Registra el ingreso en planta y realiza la conciliación del día.',
        ruta: '#cierre',
      });
    if (d.entregasRechazadas)
      filas.push({
        icono: '!',
        titulo: `${d.entregasRechazadas} entregas rechazadas`,
        detalle: 'Revisa la calidad, la evidencia y el estado del proveedor.',
        ruta: '/calidad',
      });
    if (d.controlesObservados)
      filas.push({
        icono: '!',
        titulo: `${d.controlesObservados} recepciones observadas`,
        detalle: 'La liquidación debe considerar la tarifa reducida vigente.',
        ruta: '/calidad',
      });
    if (d.pendientesCalidad)
      filas.push({
        icono: '✓',
        titulo: `${d.pendientesCalidad} recepciones sin control de calidad`,
        detalle: 'Completa los controles antes de conciliar el día.',
        ruta: '/calidad',
      });
    if (d.liquidacionesPendientes)
      filas.push({
        icono: 'S/',
        titulo: `${d.liquidacionesPendientes} liquidaciones pendientes`,
        detalle: 'Pendientes de todos los periodos de pago.',
        ruta: '/liquidaciones',
      });
    return filas;
  }
  cerrar() {
    this.error.set('');
    this.mensaje.set('');
    if (this.guardando() || !this.data() || this.cerrado) return;
    if (
      this.litrosPlanta === null ||
      !Number.isFinite(this.litrosPlanta) ||
      this.litrosPlanta < 0
    ) {
      this.error.set('Ingresa una cantidad válida de litros recibidos en planta.');
      return;
    }
    if (this.data()!.pendientesCalidad > 0) {
      this.error.set('Completa los controles de calidad pendientes antes de cerrar.');
      return;
    }
    if (Math.abs(this.variacion ?? 0) > 0.01 && !this.observacion.trim()) {
      this.error.set('Explica la diferencia de litros en la observación del cierre.');
      return;
    }
    this.guardando.set(true);
    this.operacion
      .cerrar(this.fecha, this.litrosPlanta, this.observacion.trim())
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (cierre) => {
          this.data.update((d) => (d ? { ...d, cierre } : d));
          this.mensaje.set('Conciliación diaria cerrada y guardada.');
          this.cargarActividad();
        },
        error: () =>
          this.error.set(
            'No se pudo cerrar la conciliación. Actualiza los datos y revisa los controles pendientes.',
          ),
      });
  }
  reabrir() {
    if (this.guardando() || !this.cerrado) return;
    this.error.set('');
    this.mensaje.set('');
    this.guardando.set(true);
    this.operacion
      .reabrir(this.fecha)
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.cargar();
          this.cargarActividad();
        },
        error: () => this.error.set('No se pudo reabrir la conciliación.'),
      });
  }
}
