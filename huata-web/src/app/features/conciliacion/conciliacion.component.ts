import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { ConciliacionRuta } from '../../core/models/models';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { AcopioTiempoRealService } from '../../core/services/acopio-tiempo-real.service';
import { AuthService } from '../../core/services/auth.service';
import { EstadoSocket } from '../../core/services/canal-tiempo-real';
import { ConciliacionService } from '../../core/services/conciliacion.service';
import { CierreAcopio, OperacionService, TotalesDia } from '../../core/services/operacion.service';
import { porZona } from './por-zona';

/** Estado de una ruta en la recepción del día, en palabras del cliente. */
export type EstadoLlegada = 'por-recibir' | 'cuadra' | 'diferencia';

/**
 * Recepción de rutas en planta (2.2.4, RF-53, RF-54): lo que recogió el acopiador contra lo que
 * llegó a planta, una ruta por fila. Planta solo anota los litros que llegaron (en la misma fila);
 * quién recibe es la cuenta que registra y lo pone el backend. Lo recogido sale de los recojos del día
 * y, mientras la fecha sea hoy, se actualiza solo por `/api/ws/acopio` sin recargar.
 */
@Component({
  selector: 'app-conciliacion',
  standalone: true,
  imports: [FormsModule, DecimalPipe, DatePipe],
  templateUrl: './conciliacion.component.html',
  styleUrl: './conciliacion.component.css',
})
export class ConciliacionComponent implements OnInit, OnDestroy {
  private readonly conciliacion = inject(ConciliacionService);
  private readonly acopioTiempoReal = inject(AcopioTiempoRealService);
  private readonly operacion = inject(OperacionService);
  private readonly notificaciones = inject(NotificacionesService);
  protected readonly auth = inject(AuthService);

  readonly filas = signal<ConciliacionRuta[]>([]);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly estadoSocket = signal<EstadoSocket>('desconectado');
  /** Rutas cuya fila acaba de cambiar en vivo, para resaltarlas un momento. */
  readonly recienActualizadas = signal<ReadonlySet<number>>(new Set());
  /** Qué rutas tienen abierto el detalle por proveedor. */
  readonly desplegadas = signal<ReadonlySet<number>>(new Set());

  // ---- Registrar la llegada de una ruta (en su propia fila) ----
  /** Ruta cuya llegada se está anotando; null = ninguna. */
  readonly registrando = signal<number | null>(null);
  readonly guardando = signal(false);
  litros: number | null = null;
  observacion = '';

  // ---- Cierre del día (V33): calculado de las rutas ----
  readonly totales = signal<TotalesDia | null>(null);
  readonly cierre = signal<CierreAcopio | null>(null);
  readonly cerrandoDia = signal(false);
  observacionCierre = '';

  fecha = hoy();

  private readonly destruir$ = new Subject<void>();
  private readonly temporizadores = new Map<number, ReturnType<typeof setTimeout>>();

  readonly totalEnRuta = computed(() => this.filas().reduce((s, f) => s + f.litrosEnRuta, 0));
  readonly totalEnPlanta = computed(() => this.filas().reduce((s, f) => s + (f.litrosRecibidosPlanta ?? 0), 0));
  readonly totalDiferencia = computed(() => this.filas().reduce((s, f) => s + (f.diferencia ?? 0), 0));
  readonly rutasSinCerrar = computed(() => this.filas().filter((f) => f.litrosRecibidosPlanta == null).length);
  readonly rutasRecibidas = computed(() => this.filas().length - this.rutasSinCerrar());
  readonly avance = computed(() => (this.filas().length ? (this.rutasRecibidas() / this.filas().length) * 100 : 0));
  /** La misma recepción agrupada por zona; se actualiza en vivo con cada fila. */
  readonly zonas = computed(() => porZona(this.filas()));

  puedeEscribir(): boolean {
    return this.auth.puede(PERMISO.plantaRecepcion);
  }

  /** Cerrar el día es una acción de administración (acopio.cerrar-jornada), no del acopiador. */
  puedeCerrarDia(): boolean {
    return this.auth.puede(PERMISO.acopioCerrarJornada);
  }

  /**
   * El canal solo tiene sentido sobre la jornada en curso: lo de ayer ya no cambia solo. Método y no
   * computed(): `fecha` es un campo ligado con ngModel, no una señal.
   */
  enVivo(): boolean {
    return this.fecha === hoy();
  }

  estado(f: ConciliacionRuta): EstadoLlegada {
    if (f.litrosRecibidosPlanta == null) return 'por-recibir';
    return f.diferencia === 0 ? 'cuadra' : 'diferencia';
  }

  /** Diferencia sobre lo recogido en el camino, en %. */
  porcentaje(f: ConciliacionRuta): number | null {
    return f.diferencia == null || !f.litrosEnRuta ? null : (f.diferencia / f.litrosEnRuta) * 100;
  }

  ngOnInit(): void {
    this.cargar();
    this.acopioTiempoReal.estado$.subscribe((estado) => this.estadoSocket.set(estado));
    this.acopioTiempoReal.conectar(this.destruir$).subscribe((evento) => this.aplicar(evento.conciliacion));
  }

  ngOnDestroy(): void {
    this.temporizadores.forEach((t) => clearTimeout(t));
    this.destruir$.next();
    this.destruir$.complete();
  }

  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    this.registrando.set(null);
    this.conciliacion
      .deFecha(this.fecha)
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (f) => this.filas.set(f),
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo cargar la recepción del día.'),
      });
    this.cargarCierre();
  }

  cambiarFecha(fecha: string): void {
    if (!fecha || fecha === this.fecha) return;
    this.fecha = fecha;
    this.cargar();
  }

    detalleVisible(rutaId: number): boolean {
    return this.desplegadas().has(rutaId);
  }

  alternarDetalle(rutaId: number): void {
    const abiertas = new Set(this.desplegadas());
    if (!abiertas.delete(rutaId)) abiertas.add(rutaId);
    this.desplegadas.set(abiertas);
  }

  resaltada(rutaId: number): boolean {
    return this.recienActualizadas().has(rutaId);
  }

  // ---------------------------------------------------------------- registrar llegada

  abrirRegistro(f: ConciliacionRuta): void {
    if (!this.puedeEscribir()) return;
    this.registrando.set(f.rutaId);
    // Al corregir se parte de lo ya anotado; si es la primera vez, vacío.
    this.litros = f.litrosRecibidosPlanta ?? null;
    this.observacion = f.observacion ?? '';
  }

  cancelarRegistro(): void {
    if (!this.guardando()) this.registrando.set(null);
  }

  registrar(f: ConciliacionRuta): void {
    if (this.guardando()) return;
    if (this.litros == null || this.litros < 0) {
      this.error.set('Escribe los litros que llegaron a planta.');
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    this.conciliacion
      .registrar({
        fecha: this.fecha,
        rutaId: f.rutaId,
        litrosRecibidosPlanta: this.litros,
        observacion: this.observacion.trim() || undefined,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (fila) => {
          this.notificaciones.exito(`Llegada de ${f.rutaNombre} registrada. Lo apto ya entró a inventario.`);
          this.registrando.set(null);
          // La respuesta trae la fila recalculada: se aplica en vez de recargar todo. Si no es la
          // fecha de hoy (el canal no aplica), se recarga para no dejarla desactualizada.
          if (this.enVivo()) {
            this.aplicar(fila);
            this.cargarCierre();
          } else {
            this.cargar();
          }
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo registrar la llegada.'),
      });
  }

  // ---------------------------------------------------------------- cierre del día

  cargarCierre(): void {
    if (!this.puedeCerrarDia()) return;
    this.operacion.totales(this.fecha).subscribe({ next: (t) => this.totales.set(t) });
    this.operacion.resumen(this.fecha).subscribe({ next: (r) => this.cierre.set(r.cierre ?? null) });
  }

  cerrarDia(): void {
    if (this.cerrandoDia()) return;
    this.cerrandoDia.set(true);
    this.error.set('');
    this.operacion
      .cerrar(this.fecha, this.observacionCierre.trim())
      .pipe(finalize(() => this.cerrandoDia.set(false)))
      .subscribe({
        next: (c) => {
          this.cierre.set(c);
          this.notificaciones.exito(`Día ${this.fecha} cerrado.`);
          this.observacionCierre = '';
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo cerrar el día.'),
      });
  }

  reabrirDia(): void {
    this.operacion.reabrir(this.fecha).subscribe({
      next: (c) => {
        this.cierre.set(c);
        this.notificaciones.exito(`Día ${this.fecha} reabierto.`);
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo reabrir el día.'),
    });
  }

  // ---------------------------------------------------------------- tiempo real

  /**
   * Reemplaza (o inserta) la fila que llegó por el canal. Viene recalculada por el servidor, así que
   * no se suma nada aquí: perder un mensaje no deja un total distinto del de la base. Si se está
   * mirando otra fecha, se descarta.
   */
  private aplicar(fila: ConciliacionRuta): void {
    if (!this.enVivo()) return;
    const filas = [...this.filas()];
    const i = filas.findIndex((f) => f.rutaId === fila.rutaId);
    // Sin nada que recibir (solo entregas directas, o se anuló su último recojo): la ruta sale de la
    // lista. Una entrega directa ya está en planta; no hay llegada que anotar.
    if (fila.requiereRecepcion === false) {
      if (i >= 0) {
        filas.splice(i, 1);
        this.filas.set(filas);
      }
      return;
    }
    if (i >= 0) {
      filas[i] = fila;
    } else {
      filas.push(fila);
      filas.sort((a, b) => a.rutaNombre.localeCompare(b.rutaNombre));
    }
    this.filas.set(filas);
    this.marcarReciente(fila.rutaId);
  }

  private marcarReciente(rutaId: number): void {
    this.recienActualizadas.set(new Set(this.recienActualizadas()).add(rutaId));
    clearTimeout(this.temporizadores.get(rutaId));
    this.temporizadores.set(
      rutaId,
      setTimeout(() => {
        const restantes = new Set(this.recienActualizadas());
        restantes.delete(rutaId);
        this.recienActualizadas.set(restantes);
        this.temporizadores.delete(rutaId);
      }, 6_000),
    );
  }
}

function hoy(): string {
  // Fecha operativa de Huata, no la del reloj del navegador: la jornada se corta a medianoche de Lima
  // igual que en el backend, así que un usuario en otra zona horaria ve el mismo día.
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
}
