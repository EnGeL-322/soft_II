import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, catchError, forkJoin, of, Subscription } from 'rxjs';
import {
  DESCRIPCIONES_NAV,
  ItemNav,
  NAVEGACION,
  permisosDeItem,
  PERMISO,
} from '../../core/config/navegacion';
import { BloquePanel, panelDe } from '../../core/config/paneles';
import {
  ConciliacionRuta,
  IncidenciaCalidad,
  Liquidacion,
  SolicitudCambioZona,
} from '../../core/models/models';
import { OperacionService, ResumenOperacion } from '../../core/services/operacion.service';
import { AuthService } from '../../core/services/auth.service';
import { ConciliacionService } from '../../core/services/conciliacion.service';
import { ProductoresService } from '../../core/services/productores.service';
import { CalidadService } from '../../core/services/calidad.service';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { IconoComponent } from '../../shared/ui/icono.component';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

/** Indicador del día. `ruta` solo existe si el rol puede abrir el listado que lo explica. */
export interface IndicadorPanel {
  etiqueta: string;
  valor: number;
  unidad?: string;
  decimales: string;
  tono: 'normal' | 'aviso' | 'error';
  ruta?: string;
  /** Filtro o pestaña de destino, p. ej. { tipo: 'proveedores' } en Usuarios. */
  query?: Record<string, string>;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink, IconoComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css'],
})
export class DashboardComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de acopio, calidad, pagos, proveedores, rutas, produccion, ventas, inventario, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['acopio', 'calidad', 'pagos', 'proveedores', 'rutas', 'produccion', 'ventas', 'inventario'], () => this.cargar(true));
  readonly data = signal<ResumenOperacion | null>(null);
  readonly conciliaciones = signal<ConciliacionRuta[] | null>(null);
  readonly solicitudes = signal<SolicitudCambioZona[] | null>(null);
  readonly incidencias = signal<IncidenciaCalidad[] | null>(null);
  readonly liquidaciones = signal<Liquidacion[] | null>(null);
  readonly error = signal('');
  readonly avisos = signal<string[]>([]);
  readonly cargando = signal(false);
  readonly actualizado = signal<Date | null>(null);
  readonly descripciones = DESCRIPCIONES_NAV;
  readonly panel = computed(() => panelDe(this.auth.roles()));
  readonly accesos = computed(() =>
    NAVEGACION.filter((item) => item.ruta !== 'dashboard' && permisosDeItem(item).some((p) => this.auth.puede(p))),
  );
  readonly accesosPrincipales = computed(() => {
    const roles = this.auth.roles();
    const prioridades = roles.includes('ADMINISTRADOR')
      ? ['recojo', 'rutas', 'usuarios', 'produccion-queso', 'pagos', 'venta-queso']
      : roles.includes('SECRETARIA')
        ? ['pagos', 'produccion-queso', 'usuarios', 'venta-queso', 'compras', 'inventario']
        : roles.includes('CALIDAD')
          ? ['recojo', 'calidad', 'conciliacion', 'usuarios', 'rutas']
          : roles.includes('PRODUCTOR_QUESO')
            ? ['produccion-queso', 'recetas', 'inventario']
            : ['recojo', 'rutas', 'usuarios', 'conciliacion', 'calidad'];
    const orden = (ruta: string) =>
      prioridades.includes(ruta) ? prioridades.indexOf(ruta) : prioridades.length;
    return [...this.accesos()].sort((a, b) => orden(a.ruta) - orden(b.ruta)).slice(0, 6);
  });
  /** Cifras de la fecha elegida, tal como las devuelve /operacion/{fecha}: nada se estima aquí. */
  readonly indicadores = computed<IndicadorPanel[]>(() => {
    const d = this.data();
    if (!d) return [];
    const enlace = (permiso: string, ruta: string) => (this.auth.puede(permiso) ? ruta : undefined);
    return [
      {
        etiqueta: 'Litros aceptados',
        valor: d.aceptados,
        unidad: 'L',
        decimales: '1.0-1',
        tono: 'normal',
        ruta: enlace(PERMISO.acopioRead, 'recojo'),
      },
      {
        etiqueta: 'Proveedores con entrega',
        valor: d.proveedores,
        decimales: '1.0-0',
        tono: 'normal',
        ruta: enlace(PERMISO.proveedoresRead, 'usuarios'),
        query: { tipo: 'proveedores' },
      },
      {
        etiqueta: 'Controles observados',
        valor: d.controlesObservados,
        decimales: '1.0-0',
        tono: d.controlesObservados > 0 ? 'aviso' : 'normal',
        ruta: enlace(PERMISO.calidadRead, 'calidad'),
      },
      {
        etiqueta: 'Entregas rechazadas',
        valor: d.entregasRechazadas,
        decimales: '1.0-0',
        tono: d.entregasRechazadas > 0 ? 'error' : 'normal',
        ruta: enlace(PERMISO.calidadRead, 'calidad'),
      },
    ];
  });
  readonly incidenciasAbiertas = computed(
    () => this.incidencias()?.filter((i) => i.estado !== 'CERRADA') ?? [],
  );
  readonly liquidacionesPendientes = computed(
    () => this.liquidaciones()?.filter((l) => l.estado !== 'CLOSED') ?? [],
  );
  readonly pendientes = computed(() =>
    [
      {
        ruta: 'recojo',
        permiso: PERMISO.acopioRead,
        cantidad: this.data()?.pendientesCalidad ?? 0,
        texto: 'Recepciones por revisar',
      },
      {
        ruta: 'calidad',
        permiso: PERMISO.calidadRead,
        cantidad: this.incidenciasAbiertas().length,
        texto: 'Incidencias de calidad abiertas',
      },
      {
        ruta: 'conciliacion',
        permiso: PERMISO.acopioRead,
        cantidad: this.conciliaciones()?.filter((c) => c.litrosRecibidosPlanta == null).length ?? 0,
        texto: 'Rutas pendientes de recibir en planta',
      },
      {
        ruta: 'solicitudes-zona',
        permiso: PERMISO.proveedoresWrite,
        cantidad: this.solicitudes()?.length ?? 0,
        texto: 'Solicitudes de cambio de zona',
      },
      {
        ruta: 'pagos',
        permiso: PERMISO.pagosRead,
        cantidad: this.liquidaciones()
          ? this.liquidacionesPendientes().length
          : (this.data()?.liquidacionesPendientes ?? 0),
        texto: 'Liquidaciones pendientes',
      },
    ].filter((pendiente) => pendiente.cantidad > 0 && this.auth.puede(pendiente.permiso)),
  );
  readonly hoy = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  fecha = this.hoy;
  private readonly destroyRef = inject(DestroyRef);
  private carga?: Subscription;

  constructor(
    private readonly operacion: OperacionService,
    protected readonly auth: AuthService,
    private readonly conciliacion: ConciliacionService,
    private readonly productores: ProductoresService,
    private readonly calidad: CalidadService,
    private readonly liquidacionesService: LiquidacionesService,
  ) {}
  ngOnInit() {
    this.cargar();
  }
  etiquetaDe(item: ItemNav): string {
    return item.etiquetaEscritura && item.permisoEscritura && this.auth.puede(item.permisoEscritura)
      ? item.etiquetaEscritura
      : item.etiqueta;
  }
  muestra(bloque: BloquePanel): boolean {
    if (!this.panel().bloques.includes(bloque)) return false;
    switch (bloque) {
      case 'calidadDelDia':
        return this.auth.puede(PERMISO.calidadRead);
      case 'liquidacionesDelCiclo':
        return this.auth.puede(PERMISO.pagosRead);
      case 'produccionDelDia':
        return this.auth.puede(PERMISO.produccionRead) || this.auth.puede(PERMISO.quesosRead);
      case 'acopioPorRuta':
        return this.auth.puede(PERMISO.acopioRead);
      case 'cadenaProductiva':
        return (
          this.auth.puede(PERMISO.produccionRead) ||
          this.auth.puede(PERMISO.quesosRead) ||
          this.auth.puede(PERMISO.acopioRead)
        );
      default:
        return true;
    }
  }
  private consulta<T>(peticion: Observable<T>, aviso: string): Observable<T | null> {
    return peticion.pipe(
      catchError(() => {
        this.avisos.update((avisos) => [...avisos, aviso]);
        return of(null);
      }),
    );
  }
  /** `silencioso`: la recarga en vivo conserva lo que se ve hasta tener lo nuevo (sin parpadeo). */
  cargar(silencioso = false) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(this.fecha) || this.fecha > this.hoy) {
      if (!silencioso) this.error.set('Selecciona una fecha válida, hasta hoy.');
      return;
    }
    if (silencioso && this.cargando()) return;
    this.carga?.unsubscribe();
    this.error.set('');
    this.avisos.set([]);
    if (!silencioso) {
      this.cargando.set(true);
      this.actualizado.set(null);
      this.data.set(null);
      this.solicitudes.set(null);
      this.conciliaciones.set(null);
      this.incidencias.set(null);
      this.liquidaciones.set(null);
    }
    this.carga = forkJoin({
      dia: this.operacion.resumen(this.fecha).pipe(catchError(() => of(null))),
      planta: this.auth.puede(PERMISO.acopioRead)
        ? this.consulta(
            this.conciliacion.deFecha(this.fecha),
            'No se pudo consultar la llegada a planta.',
          )
        : of(null),
      solicitudes: this.auth.puede(PERMISO.proveedoresWrite)
        ? this.consulta(
            this.productores.solicitudesZona('PENDIENTE'),
            'No se pudieron consultar los cambios de zona.',
          )
        : of(null),
      incidencias: this.auth.puede(PERMISO.calidadRead)
        ? this.consulta(
            this.calidad.incidencias(),
            'No se pudieron consultar las incidencias de calidad.',
          )
        : of(null),
      liquidaciones: this.muestra('liquidacionesDelCiclo')
        ? this.consulta(
            this.liquidacionesService.listar(undefined, undefined, 0, 200),
            'No se pudieron consultar las liquidaciones.',
          )
        : of(null),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((r) => {
        this.data.set(r.dia);
        this.conciliaciones.set(r.planta);
        this.solicitudes.set(r.solicitudes);
        this.incidencias.set(r.incidencias);
        this.liquidaciones.set(r.liquidaciones?.content ?? null);
        if (!r.dia)
          this.error.set(
            'No se pudo consultar la fecha. Puedes abrir las secciones o intentar actualizar.',
          );
        this.cargando.set(false);
        this.actualizado.set(new Date());
      });
  }

  exportar() {
    const d = this.data();
    if (!d) return;
    const filas = [
      ['Fecha', 'Ruta', 'Litros aceptados', 'Recepciones'],
      ...d.rutas.map((r) => [d.fecha, r.nombre, r.litros, r.recepciones]),
    ];
    const csv = filas
      .map((f) =>
        f
          .map(
            (v) =>
              '"' +
              String(v)
                // Inyección de fórmulas (OWASP CSV Injection): Excel ejecuta lo que empiece así.
                .replace(/^[=+@\t\r-]/, "'$&")
                .replace(/"/g, '""') +
              '"',
          )
          .join(';'),
      )
      .join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' }),
    );
    const a = document.createElement('a');
    a.href = url;
    a.download = `Huata_acopio_${d.fecha}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
