import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { Component, DestroyRef, OnInit, computed, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, of } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import {
  CapacidadItem,
  CapacidadProduccion,
  LoteQueso,
  MateriaPrima,
  OrdenProduccion,
  OrigenLeche,
  Producto,
  TrabajadorProduccion,
} from '../../core/models/models';
import { InventarioService } from '../../core/services/inventario.service';
import { AuthService } from '../../core/services/auth.service';
import { QueseriaService, ManoObraItemRequest } from '../../core/services/queseria.service';
import { ProduccionService } from '../../core/services/produccion.service';
import {
  ReporteQuesoService,
  ReporteQuesoSemanal,
} from '../../core/services/reporte-queso.service';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { IconoComponent } from '../../shared/ui/icono.component';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

interface FilaManoObra {
  trabajadorId: number | null;
  monto: number | null;
}

/** Cómo está un insumo frente a lo que se quiere producir. */
export type EstadoInsumo = 'alcanza' | 'limita' | 'falta';

/** Lo que se va a descontar de un insumo, para verlo antes de guardar. */
export interface ConsumoPrevisto {
  item: CapacidadItem;
  descontar: number;
  queda: number;
}

/** Una fila de la tabla "Del almacén": lo que hay y, con una cantidad escrita, lo que se usa. */
export interface FilaInsumo {
  item: CapacidadItem;
  estado: EstadoInsumo;
  usa: number | null;
  queda: number | null;
}

/** Lo que dice la tarjeta de cada producto antes de elegirlo. */
export interface TarjetaProducto {
  producto: Producto;
  texto: string;
  tono: 'ok' | 'aviso' | 'falta' | 'cargando';
}

/** Vistas que abre el menú lateral (`?vista=`), en el mismo orden que sus subitems. */
type Pestana = 'ordenes' | 'registrar' | 'historial' | 'reportes';

/** Cuántas producciones se ven sin pedir "Ver todas". */

/**
 * "Producción": se toca la tarjeta del producto (cada una ya dice cuánto alcanza), se escribe cuántas
 * se pusieron y cuántas salieron, y se guarda. Lo que se descuenta lo calcula el servidor con la
 * receta (no se escribe a mano); aquí solo se muestra en una tabla antes de guardar. Al guardar, el
 * formulario queda listo para la siguiente producción y lo de hoy se ve debajo. Los reportes van en
 * su propia pestaña.
 */
@Component({
  selector: 'app-produccion-queso',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, BuscadorComponent, RouterLink, IconoComponent],
  templateUrl: './produccion-queso.component.html',
  styles: `
    .pq-layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 20px; align-items: start; }
    .pq-pasos { display: flex; flex-direction: column; gap: 22px; }
    .pq-paso { display: flex; flex-direction: column; gap: 10px; }
    .pq-paso-titulo { display: flex; align-items: center; gap: 10px; margin: 0; font-size: 15px; font-weight: 700; color: var(--marino, var(--text)); }
    .pq-paso-num { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: var(--azul, var(--green)); color: #fff; font-size: 13px; }
    .pq-max { display: flex; gap: 8px; }
    .pq-max input { flex: 1; min-width: 0; }
    .pq-estado-insumo { font-weight: 700; display: inline-block; width: 18px; }
    tr[data-estado='alcanza'] .pq-estado-insumo { color: var(--ok); }
    tr[data-estado='limita'] td { background: var(--warn-bg) !important; }
    tr[data-estado='limita'] .pq-estado-insumo { color: var(--warn); }
    tr[data-estado='falta'] td { background: var(--err-bg) !important; }
    tr[data-estado='falta'] .pq-estado-insumo { color: var(--err); }
    .pq-hoy { list-style: none; margin: 0; padding: 0; }
    .pq-hoy li { display: flex; justify-content: space-between; gap: 12px; padding: 12px 20px; border-bottom: 1px solid #e3eaf3; }
    .pq-hoy li:last-child { border-bottom: 0; }
    .pq-hoy strong { display: block; color: var(--marino, var(--text)); }
    .pq-hoy small { color: var(--muted); }
    .pq-hoy .pq-hoy-num { text-align: right; font-weight: 700; font-size: 18px; color: var(--marino, var(--text)); }
    .pq-filtros { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; }
    .pq-filtros .pd-campo { width: 180px; }
    @media (max-width: 1100px) { .pq-layout { grid-template-columns: 1fr; } }
  `,
})
export class ProduccionQuesoComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de produccion, ventas, inventario, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['produccion', 'ventas', 'inventario'], () => {
    this.cargarLotes();
    this.cargarLeche();
    if (this.panelAbierto()) this.cargarTarjetas();
    if (this.pestana() === 'reportes') this.cargarReporte();
  });
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly pestana = signal<Pestana>('historial');

  // ---- Lo que se ve siempre ----
  readonly lotes = signal<LoteQueso[]>([]);
  readonly pagLotes = new PaginacionLocal();
  readonly cargando = signal(false);
  readonly lecheDisponible = signal<number | null>(null);
  readonly loteAbiertoId = signal<number | null>(null);
  readonly error = signal('');

  // ---- Registrar producción ----
  readonly panelAbierto = signal(false);
  readonly productos = signal<Producto[]>([]);
  readonly trabajadores = signal<TrabajadorProduccion[]>([]);
  readonly materias = signal<MateriaPrima[]>([]);
  readonly capacidad = signal<CapacidadProduccion | null>(null);
  readonly cargandoCapacidad = signal(false);
  readonly guardando = signal(false);
  readonly manoObraFilas = signal<FilaManoObra[]>([]);
  /** Cuánto alcanza de cada producto, para sus tarjetas. Sin entrada = todavía cargando. */
  readonly capacidades = signal<ReadonlyMap<number, CapacidadProduccion | null>>(new Map());
  /** El último lote guardado: se muestra arriba del formulario ya limpio. */
  readonly ultimoGuardado = signal<LoteQueso | null>(null);

  // ---- Órdenes de producción (V61) ----
  readonly ordenes = signal<OrdenProduccion[]>([]);
  readonly cargandoOrdenes = signal(false);
  /** La orden que el registro en curso va a cumplir (abierto desde «Producir»). */
  readonly ordenActiva = signal<OrdenProduccion | null>(null);
  readonly formOrdenAbierto = signal(false);
  readonly guardandoOrden = signal(false);
  readonly errorOrden = signal('');
  readonly filtroOrdenes = signal<'activas' | 'todas'>('activas');
  ordenProductoId: number | null = null;
  ordenCantidad: number | null = null;
  ordenFecha = hoy();
  ordenObservaciones = '';

  readonly ordenesVisibles = computed(() =>
    this.filtroOrdenes() === 'activas' ? this.ordenes().filter((o) => o.estado === 'PENDIENTE') : this.ordenes(),
  );
  readonly resumenOrdenes = computed(() => {
    const pendientes = this.ordenes().filter((o) => o.estado === 'PENDIENTE');
    return {
      pendientes: pendientes.length,
      listas: pendientes.filter((o) => o.lista).length,
      esperando: pendientes.filter((o) => o.lista === false).length,
      terminadasHoy: this.ordenes().filter((o) => o.estado === 'TERMINADA' && this.lotes().some((l) => l.id === o.loteId && l.fecha === hoy())).length,
    };
  });
  private versionCapacidad = 0;

  productoId: number | null = null;
  /** Lo que se pone a producir: sobre esto se descuenta la receta. */
  cantidad: number | null = null;
  /** Opcional: si salieron menos. Vacío = salió lo mismo que se puso a producir. */
  salieron: number | null = null;
  fecha = hoy();
  responsable = '';
  observaciones = '';
  origenLeche: OrigenLeche = 'ACOPIO_DEL_DIA';
  quesosReparados: number | null = null;
  quesosRecorte: number | null = null;
  recorteKg: number | null = null;
  /** Vacío = la calcula el sistema con los días que dura el producto. */
  fechaVencimiento = '';

  // ---- Reportes (pestaña aparte) ----
  reporteDesde = '';
  reporteHasta = '';
  readonly reporte = signal<ReporteQuesoSemanal | null>(null);
  readonly cargandoReporte = signal(false);
  readonly descargandoReporte = signal(false);
  readonly errorReporte = signal('');

  readonly opcProductos = computed<OpcionBuscador[]>(() =>
    this.productos().map((p) => ({ valor: p.id, etiqueta: p.nombre, detalle: p.unidadVenta })),
  );
  readonly opcTrabajadores = computed<OpcionBuscador[]>(() =>
    this.trabajadores().map((t) => ({ valor: t.id, etiqueta: t.nombre })),
  );

  readonly tarjetas = computed<TarjetaProducto[]>(() =>
    this.productos().map((producto) => ({ producto, ...estadoTarjeta(this.capacidades().get(producto.id)) })),
  );
  /**
   * Producto de la nueva orden: lista que se despliega hacia abajo y se filtra escribiendo (las
   * tarjetas se extendían a lo ancho). Cada opción dice su código y cuánto alcanza hoy.
   */
  readonly opcProductosOrden = computed<OpcionBuscador[]>(() =>
    this.tarjetas().map((t) => ({
      valor: t.producto.id,
      etiqueta: t.producto.nombre,
      detalle: [t.producto.codigo, t.texto].filter(Boolean).join(' · '),
    })),
  );
  readonly lotesDeHoy = computed(() => this.lotes().filter((l) => l.fecha === hoy()));

  /** Historial: busca por producto, código de lote o responsable. */
  readonly filtroHistorial = signal('');
  readonly lotesFiltrados = computed(() => {
    const q = normalizar(this.filtroHistorial());
    if (!q) return this.lotes();
    return this.lotes().filter((l) =>
      normalizar(`${l.productoNombre} ${l.codigo} ${l.responsable}`).includes(q),
    );
  });

  readonly stockTotal = computed(() => this.lotes().reduce((a, l) => a + l.stock, 0));
  readonly producidoHoy = computed(() =>
    this.lotes()
      .filter((l) => l.fecha === hoy())
      .reduce((a, l) => a + l.unidadesProducidas, 0),
  );

  constructor(
    private readonly queseria: QueseriaService,
    private readonly produccion: ProduccionService,
    protected readonly auth: AuthService,
    private readonly inventario: InventarioService,
    private readonly reporteQueso: ReporteQuesoService,
  ) {}

  ngOnInit(): void {
    this.cargarLotes();
    this.cargarLeche();
    this.cargarOrdenes();
    this.ruta.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.abrirVista(params.get('vista'), Number(params.get('orden')) || null));
  }

  /**
   * Sin vista abre la primera del menú: Órdenes de producción. `?orden=` abre Registrar con esa
   * orden ya puesta (producto y cantidad), para cumplirla.
   */
  private abrirVista(vista: string | null, ordenId: number | null = null): void {
    if (vista === 'reportes' && this.puedeVerReporte()) {
      this.abrirReportes();
    } else if (vista === 'historial') {
      this.pestana.set('historial');
    } else if (vista === 'registrar' && this.puedeRegistrar()) {
      this.pestana.set('registrar');
      if (!this.panelAbierto()) this.abrirRegistro();
      if (ordenId) this.prepararDesdeOrden(ordenId);
    } else {
      this.pestana.set('ordenes');
    }
  }

  puedeRegistrar(): boolean {
    return this.auth.puede(PERMISO.produccionWrite) || this.auth.puede(PERMISO.quesosWrite);
  }

  puedeVerReporte(): boolean {
    return this.auth.puede(PERMISO.quesosRead);
  }

  puedeVerCompras(): boolean {
    return this.auth.puede(PERMISO.comprasRead);
  }

  puedeVerConfiguracion(): boolean {
    return this.auth.puede(PERMISO.parametrosRead);
  }

  /** Días que dura el producto elegido (Configuración → Precios y calidad). */
  vidaUtilElegida(): number | null {
    return this.productos().find((p) => p.id === this.productoId)?.vidaUtilDias ?? null;
  }

  puedeVerRecetas(): boolean {
    return this.auth.puede(PERMISO.recetasRead);
  }

  cargarLotes(): void {
    this.cargando.set(true);
    this.queseria
      .lotes()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (l) => this.lotes.set(l),
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar las producciones.'),
      });
  }

  /** La tarjeta de arriba: leche que llegó del acopio y todavía está en el almacén. */
  private cargarLeche(): void {
    this.produccion.capacidad(null).subscribe({
      next: (c) => this.lecheDisponible.set(c.lecheDisponible ?? null),
      error: () => this.lecheDisponible.set(null),
    });
  }

  // ---------------------------------------------------------------- Abrir y cerrar el registro

  abrirRegistro(): void {
    this.panelAbierto.set(true);
    this.error.set('');
    if (!this.responsable) this.responsable = this.auth.nombreCompleto();
    if (!this.productos().length) {
      this.produccion.productos().subscribe({
        next: (p) => {
          this.productos.set(p);
          this.cargarTarjetas();
          if (this.productoId == null && p.length === 1) this.elegirProducto(p[0].id);
        },
        error: () => this.error.set('No se pudo cargar el catálogo de productos.'),
      });
    } else {
      this.cargarTarjetas();
    }
    if (!this.trabajadores().length && this.auth.puede(PERMISO.produccionRead)) {
      this.produccion.trabajadores().subscribe({ next: (t) => this.trabajadores.set(t), error: () => {} });
    }
    // Solo para el costo aproximado; sin permiso de inventario el costo exacto lo da el servidor.
    if (!this.materias().length && this.auth.puede(PERMISO.inventarioRead)) {
      this.inventario.materiasPrimas().subscribe({ next: (m) => this.materias.set(m), error: () => {} });
    }
  }

  cerrarRegistro(): void {
    this.panelAbierto.set(false);
    this.ultimoGuardado.set(null);
    this.limpiar();
  }

  /**
   * Una consulta de capacidad por producto: son pocos y cada tarjeta dice cuánto alcanza sin tener que
   * elegirlo. Si una falla, su tarjeta queda sin número y el producto se puede elegir igual.
   */
  cargarTarjetas(): void {
    const productos = this.productos();
    if (!productos.length) return;
    forkJoin(
      productos.map((p) =>
        this.produccion.capacidad(p.id, this.fecha).pipe(catchError(() => of(null))),
      ),
    ).subscribe((caps) => this.capacidades.set(new Map(productos.map((p, i) => [p.id, caps[i]]))));
  }

  limpiar(): void {
    this.ordenActiva.set(null);
    this.productoId = null;
    this.capacidad.set(null);
    this.cantidad = null;
    this.salieron = null;
    this.observaciones = '';
    this.quesosReparados = null;
    this.quesosRecorte = null;
    this.recorteKg = null;
    this.fechaVencimiento = '';
    this.manoObraFilas.set([]);
  }

  // ---------------------------------------------------------------- Paso 1: producto y cuánto alcanza

  elegirProducto(id: number | null): void {
    if (id != null && id === this.productoId) return;
    this.ultimoGuardado.set(null);
    this.productoId = id;
    this.cantidad = null;
    this.salieron = null;
    this.cargarCapacidad();
  }

  cargarCapacidad(): void {
    const version = ++this.versionCapacidad;
    this.capacidad.set(null);
    this.error.set('');
    if (this.productoId == null) return;
    this.cargandoCapacidad.set(true);
    this.produccion
      .capacidad(this.productoId, this.fecha)
      .pipe(finalize(() => version === this.versionCapacidad && this.cargandoCapacidad.set(false)))
      .subscribe({
        next: (c) => {
          if (version !== this.versionCapacidad) return;
          this.capacidad.set(c);
          if (c.lecheDisponible != null) this.lecheDisponible.set(c.lecheDisponible);
        },
        error: (e) => {
          if (version === this.versionCapacidad)
            this.error.set(e.error?.message ?? 'No se pudo calcular cuánto se puede producir.');
        },
      });
  }

  maximo(): number | null {
    return this.capacidad()?.maximoProducible ?? null;
  }

  usarMaximo(): void {
    const max = this.maximo();
    if (max && max > 0) this.cantidad = max;
  }

  /** Nombre de lo que se vende, en plural cuando es "unidad" ("15 unidades"). */
  unidadVenta(n: number | null = 2): string {
    const u = this.capacidad()?.unidadVenta ?? 'unidad';
    if (u === 'unidad') return n === 1 ? 'unidad' : 'unidades';
    return u;
  }

  unidad(item: CapacidadItem): string {
    return etiquetaUnidad(item.unidadMedida);
  }

  /**
   * Con una cantidad escrita, un insumo falta si no alcanza para ella; sin cantidad, si no alcanza ni
   * para una. El limitante se marca aparte porque es el que decide el máximo.
   */
  estadoInsumo(item: CapacidadItem): EstadoInsumo {
    const pedido = this.cantidad && this.cantidad > 0 ? this.cantidad : 1;
    if (item.alcanzaPara < pedido) return 'falta';
    return item.limitante ? 'limita' : 'alcanza';
  }

  insumosQueFaltan(): CapacidadItem[] {
    return (this.capacidad()?.items ?? []).filter((i) => this.estadoInsumo(i) === 'falta');
  }

  /** El insumo comprable que falta (la leche no se compra: llega del acopio). */
  insumoPorComprar(): CapacidadItem | null {
    return this.insumosQueFaltan().find((i) => !i.esLeche) ?? null;
  }

  /** Falta leche: entra al almacén cuando planta recibe las rutas o una entrega directa. */
  faltaLeche(): boolean {
    return this.insumosQueFaltan().some((i) => i.esLeche);
  }

  /**
   * Lo que hay que comprar para la cantidad escrita (o para una unidad), redondeado hacia arriba a
   * 3 decimales. Abre Compras con el insumo y la cantidad ya puestos.
   */
  compraSugerida(
    i: CapacidadItem | null = this.insumoPorComprar(),
  ): { materiaPrimaId: number; cantidad: number } | null {
    if (!i) return null;
    const pedido = this.cantidad && this.cantidad > 0 ? this.cantidad : 1;
    const falta = i.cantidadPorUnidad * pedido - Math.max(0, i.stockActual);
    return { materiaPrimaId: i.materiaPrimaId, cantidad: Math.ceil(Math.max(falta, 0) * 1000) / 1000 };
  }

  limitante(): CapacidadItem | null {
    return this.capacidad()?.items.find((i) => i.limitante) ?? null;
  }

  excedeMaximo(): boolean {
    const max = this.maximo();
    return this.cantidad != null && max != null && this.cantidad > max;
  }

  // ---------------------------------------------------------------- Paso 2: lo que se descuenta

  /** Receta × cantidad, para verlo antes de guardar. El servidor hace la misma cuenta al guardar. */
  consumoPrevisto(): ConsumoPrevisto[] {
    const cantidad = this.cantidad ?? 0;
    if (cantidad <= 0) return [];
    return (this.capacidad()?.items ?? []).map((item) => {
      const descontar = redondear(item.cantidadPorUnidad * cantidad);
      return { item, descontar, queda: redondear(item.stockActual - descontar) };
    });
  }

  /** La tabla "Del almacén": cada insumo con su estado y, si ya hay cantidad, cuánto usa y cuánto queda. */
  filasInsumos(): FilaInsumo[] {
    const previsto = this.consumoPrevisto();
    return (this.capacidad()?.items ?? []).map((item, i) => ({
      item,
      estado: this.estadoInsumo(item),
      usa: previsto[i]?.descontar ?? null,
      queda: previsto[i]?.queda ?? null,
    }));
  }

  /** Costo aproximado con el costo de referencia de cada insumo; null si alguno no lo tiene. */
  costoMaterias(): number | null {
    const previsto = this.consumoPrevisto();
    if (!previsto.length) return null;
    let total = 0;
    for (const c of previsto) {
      const costo = this.materias().find((m) => m.id === c.item.materiaPrimaId)?.costoUnitarioReferencial;
      if (costo == null) return null;
      total += c.descontar * costo;
    }
    return total;
  }

  costoManoObra(): number {
    return this.manoObraFilas().reduce((s, f) => s + (f.monto ?? 0), 0);
  }

  costoEstimado(): number | null {
    const materias = this.costoMaterias();
    return materias == null ? null : materias + this.costoManoObra();
  }

  /** Sobre lo que salió de verdad; si no se dijo, sobre lo que se puso a producir. */
  costoPorUnidad(): number | null {
    const total = this.costoEstimado();
    const unidades = this.unidadesQueSalen();
    return total != null && unidades ? total / unidades : null;
  }

  unidadesQueSalen(): number | null {
    return this.salieron || this.cantidad || null;
  }

  // ---------------------------------------------------------------- Guardar

  /** Lo que impide guardar, en palabras cortas. */
  faltantes(): string[] {
    const c = this.capacidad();
    const f: string[] = [];
    if (!this.productoId) f.push('elige el producto');
    else if (c && !c.tieneReceta) f.push('falta la receta');
    else if (c && !c.recetaLlevaLeche) f.push('la receta no dice cuánta leche lleva');
    if (!this.cantidad || this.cantidad <= 0) f.push('escribe cuántas vas a producir');
    else if (this.excedeMaximo()) f.push(`no alcanza para más de ${this.maximo()}`);
    if (this.salieron != null && this.salieron <= 0) f.push('lo que salió debe ser mayor a cero');
    if (!this.responsable.trim()) f.push('falta quién estuvo a cargo');
    if (this.productoId && this.productos().length && !this.vencimientoPrevisto())
      f.push('falta configurar cuántos días dura el producto');
    if ((this.quesosReparados ?? 0) + (this.quesosRecorte ?? 0) > (this.salieron ?? this.cantidad ?? 0))
      f.push('los que salieron mal no pueden ser más de los que salieron');
    return f;
  }

  puedeGuardar(): boolean {
    return !!this.capacidad() && this.faltantes().length === 0 && !this.guardando();
  }

  agregarManoObra(): void {
    this.manoObraFilas.update((f) => [...f, { trabajadorId: null, monto: null }]);
  }

  quitarManoObra(index: number): void {
    this.manoObraFilas.update((f) => f.filter((_, i) => i !== index));
  }

  guardar(): void {
    const c = this.capacidad();
    if (!c?.recetaId || !this.productoId || !this.puedeGuardar()) {
      this.error.set('Para guardar: ' + this.faltantes().join(', ') + '.');
      return;
    }
    const manoObra: ManoObraItemRequest[] = this.manoObraFilas()
      .filter((f): f is { trabajadorId: number; monto: number } => f.trabajadorId != null && f.monto != null)
      .map((f) => ({ trabajadorId: f.trabajadorId, monto: f.monto }));
    if (
      this.manoObraFilas().some((f) => f.trabajadorId == null || f.monto == null || f.monto < 0) ||
      new Set(manoObra.map((f) => f.trabajadorId)).size !== manoObra.length
    ) {
      this.error.set('En "Quiénes trabajaron", elige a cada persona una vez y escribe su pago (o quita la fila vacía).');
      return;
    }

    const cantidad = this.cantidad!;
    this.guardando.set(true);
    this.error.set('');
    this.queseria
      .crearLote({
        fecha: this.fecha,
        productoId: this.productoId,
        origenLeche: this.origenLeche,
        cantidadObjetivo: cantidad,
        unidadesProducidas: this.salieron || cantidad,
        responsable: this.responsable.trim(),
        observaciones: this.observaciones.trim() || undefined,
        recetaId: c.recetaId,
        manoObra,
        quesosReparados: this.quesosReparados ?? undefined,
        quesosRecorte: this.quesosRecorte ?? undefined,
        recorteKg: this.recorteKg ?? undefined,
        fechaVencimiento: this.fechaVencimiento || undefined,
        ordenId: this.ordenActiva()?.id,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (lote) => {
          this.notificaciones.exito(
            `Listo: ${lote.unidadesProducidas} de ${lote.productoNombre} guardados para vender (${lote.codigo}).`,
          );
          // El formulario queda abierto y limpio para la siguiente producción del día.
          this.ultimoGuardado.set(lote);
          const cumplida = this.ordenActiva();
          this.limpiar();
          if (cumplida) {
            this.cargarOrdenes();
            // Sin ?orden= en la dirección: volver a entrar no debe precargar una orden ya cumplida.
            this.router.navigate([], { queryParams: { vista: 'registrar' }, replaceUrl: true });
          }
          this.cargarLotes();
          this.cargarLeche();
          this.cargarTarjetas();
          window.scrollTo({ top: 0, behavior: 'smooth' });
        },
        error: (e) => {
          this.error.set(e.error?.message ?? 'No se pudo guardar la producción.');
          // El stock pudo cambiar mientras se llenaba el formulario: se vuelve a mirar el almacén.
          this.cargarCapacidad();
        },
      });
  }

  /** Vencimiento que se aplicará: el escrito o fecha + días que dura el producto. */
  vencimientoPrevisto(): string | null {
    if (this.fechaVencimiento) return this.fechaVencimiento;
    const dias = this.productos().find((p) => p.id === this.productoId)?.vidaUtilDias;
    if (!dias || !this.fecha) return null;
    const d = new Date(this.fecha + 'T12:00:00');
    d.setDate(d.getDate() + dias);
    return d.toISOString().slice(0, 10);
  }

  // ---------------------------------------------------------------- Órdenes de producción

  cargarOrdenes(): void {
    if (!this.auth.puede(PERMISO.produccionRead) && !this.auth.puede(PERMISO.quesosRead)) return;
    this.cargandoOrdenes.set(true);
    this.produccion
      .ordenes()
      .pipe(finalize(() => this.cargandoOrdenes.set(false)))
      .subscribe({
        next: (o) => this.ordenes.set(o),
        error: (e) => this.error.set(mensajeDeError(e, 'No se pudieron cargar las órdenes de producción.')),
      });
  }

  abrirNuevaOrden(): void {
    this.errorOrden.set('');
    this.ordenProductoId = null;
    this.ordenCantidad = null;
    this.ordenFecha = hoy();
    this.ordenObservaciones = '';
    this.formOrdenAbierto.set(true);
    // Las tarjetas de producto dicen cuánto alcanza: las mismas del registro.
    if (!this.productos().length) {
      this.produccion.productos().subscribe({
        next: (p) => {
          this.productos.set(p);
          this.cargarTarjetas();
        },
      });
    } else {
      this.cargarTarjetas();
    }
  }

  /**
   * Antes de crear la orden: lo que necesita de cada insumo frente a lo que hay. Solo informa; la
   * orden se crea igual (si falta, Compras recibe la sugerencia).
   */
  previaOrden(): { nombre: string; unidad: string; necesita: number; hay: number; alcanza: boolean }[] {
    const c = this.ordenProductoId != null ? this.capacidades().get(this.ordenProductoId) : null;
    const cantidad = this.ordenCantidad ?? 0;
    if (!c || cantidad <= 0) return [];
    return c.items.map((i) => {
      const necesita = redondear(i.cantidadPorUnidad * cantidad);
      return {
        nombre: i.materiaPrimaNombre,
        unidad: etiquetaUnidad(i.unidadMedida),
        necesita,
        hay: i.stockActual,
        alcanza: i.stockActual >= necesita,
      };
    });
  }

  sinRecetaParaOrden(): boolean {
    const c = this.ordenProductoId != null ? this.capacidades().get(this.ordenProductoId) : undefined;
    return !!c && !c.tieneReceta;
  }

  guardarOrden(): void {
    if (!this.ordenProductoId || !this.ordenCantidad || this.ordenCantidad < 1) {
      this.errorOrden.set('Elige el producto y escribe cuántas producir.');
      return;
    }
    this.guardandoOrden.set(true);
    this.errorOrden.set('');
    this.produccion
      .crearOrden({
        productoId: this.ordenProductoId,
        cantidad: this.ordenCantidad,
        fechaPlan: this.ordenFecha || undefined,
        observaciones: this.ordenObservaciones.trim() || undefined,
      })
      .pipe(finalize(() => this.guardandoOrden.set(false)))
      .subscribe({
        next: (o) => {
          this.formOrdenAbierto.set(false);
          this.notificaciones.exito(
            o.lista
              ? `Orden ${o.codigo} lista para producir.`
              : `Orden ${o.codigo} registrada. Faltan insumos: Compras ya tiene la sugerencia.`,
          );
          this.cargarOrdenes();
        },
        error: (e) => this.errorOrden.set(mensajeDeError(e, 'No se pudo crear la orden.')),
      });
  }

  /** «Producir»: abre el registro con el producto y la cantidad de la orden. */
  producirOrden(o: OrdenProduccion): void {
    this.router.navigate([], { queryParams: { vista: 'registrar', orden: o.id } });
  }

  private prepararDesdeOrden(id: number): void {
    const aplicar = (o: OrdenProduccion | undefined) => {
      if (!o || o.estado !== 'PENDIENTE') {
        this.notificaciones.error('Esa orden ya no está pendiente.');
        return;
      }
      this.ordenActiva.set(o);
      this.elegirProducto(o.productoId);
      this.cantidad = o.cantidad;
    };
    const ya = this.ordenes().find((o) => o.id === id);
    if (ya) {
      aplicar(ya);
      return;
    }
    this.produccion.ordenes().subscribe({
      next: (lista) => {
        this.ordenes.set(lista);
        aplicar(lista.find((o) => o.id === id));
      },
    });
  }

  async anularOrden(o: OrdenProduccion): Promise<void> {
    const motivo = await this.dialogo.pedirTexto({
      titulo: `¿Anular la orden ${o.codigo}?`,
      mensaje: `${o.cantidad} ${o.productoNombre}. Deja de contar para las sugerencias de compra. Queda registrada con el motivo.`,
      etiqueta: 'Motivo de la anulación',
      obligatorio: true,
      confirmar: 'Anular orden',
      cancelar: 'Volver',
      tipo: 'peligro',
    });
    if (!motivo) return;
    this.produccion.anularOrden(o.id, motivo).subscribe({
      next: () => {
        this.notificaciones.exito(`Orden ${o.codigo} anulada.`);
        this.cargarOrdenes();
      },
      error: (e) => this.notificaciones.error(mensajeDeError(e, 'No se pudo anular la orden.')),
    });
  }

  /** Estado de una orden para su chip. */
  estadoOrden(o: OrdenProduccion): { texto: string; tono: 'ok' | 'aviso' | 'falta' | 'info' | 'neutro' } {
    if (o.estado === 'TERMINADA') return { texto: 'Terminada', tono: 'info' };
    if (o.estado === 'ANULADA') return { texto: 'Anulada', tono: 'neutro' };
    return o.lista ? { texto: 'Lista para producir', tono: 'ok' } : { texto: 'Esperando insumos', tono: 'aviso' };
  }

  /** "Falta 2 kg de Fruta y 1 más": lo que la frena, en una línea. */
  faltaDeOrden(o: OrdenProduccion): string {
    const faltan = o.lineas.filter((l) => l.falta > 0);
    if (!faltan.length) return '';
    const primero = faltan[0];
    const resto = faltan.length > 1 ? ` y ${faltan.length - 1} más` : '';
    return `Falta ${redondear(primero.falta)} ${etiquetaUnidad(primero.unidadMedida)} de ${primero.materiaPrimaNombre}${resto}`;
  }

  /** Chip del historial: agotado, vencido, vence pronto (3 días o menos) o disponible. */
  estadoLote(lote: LoteQueso): { texto: string; tono: 'ok' | 'aviso' | 'falta' | 'neutro' } {
    if (lote.stock <= 0) return { texto: 'Agotado', tono: 'neutro' };
    if (lote.fechaVencimiento) {
      const dias = diasEntre(hoy(), lote.fechaVencimiento);
      if (dias < 0) return { texto: 'Vencido', tono: 'falta' };
      if (dias === 0) return { texto: 'Vence hoy', tono: 'aviso' };
      if (dias <= 3) return { texto: `Vence en ${dias} d`, tono: 'aviso' };
    }
    return { texto: 'Disponible', tono: 'ok' };
  }

  alternarDetalle(lote: LoteQueso): void {
    this.loteAbiertoId.set(this.loteAbiertoId() === lote.id ? null : lote.id);
  }

  /** Solo se anula un lote del que no salió nada: si ya se vendió, primero se anulan esas ventas. */
  puedeAnular(lote: LoteQueso): boolean {
    return this.puedeRegistrar() && lote.stock === lote.unidadesProducidas;
  }

  /** Anula un lote registrado por error: los insumos vuelven al almacén y el producto sale de cámara. */
  async anularLote(lote: LoteQueso): Promise<void> {
    const motivo = await this.dialogo.pedirTexto({
      titulo: `¿Anular el lote ${lote.codigo}?`,
      mensaje:
        `${lote.unidadesProducidas} ${lote.productoNombre}. La leche y los insumos que usó vuelven al ` +
        'almacén y el producto sale de cámara. Queda registrado con el motivo.',
      etiqueta: 'Motivo de la anulación',
      obligatorio: true,
      confirmar: 'Anular lote',
      cancelar: 'Volver',
      tipo: 'peligro',
    });
    if (!motivo) return;
    this.queseria.anularLote(lote.id, motivo).subscribe({
      next: () => {
        this.notificaciones.exito(`Lote ${lote.codigo} anulado. Los insumos volvieron al almacén.`);
        this.loteAbiertoId.set(null);
        this.cargarLotes();
        this.cargarLeche();
      },
      error: (e) => this.notificaciones.error(mensajeDeError(e, 'No se pudo anular el lote.')),
    });
  }

  // ---------------------------------------------------------------- Reportes

  abrirReportes(): void {
    this.pestana.set('reportes');
    if (!this.reporte() && !this.cargandoReporte()) this.cargarReporte();
  }

  /** Sin fechas, el backend usa el ciclo jueves-miércoles vigente (RN-04). */
  cargarReporte(): void {
    this.cargandoReporte.set(true);
    this.errorReporte.set('');
    this.reporteQueso
      .semanal(this.reporteDesde || undefined, this.reporteHasta || undefined)
      .pipe(finalize(() => this.cargandoReporte.set(false)))
      .subscribe({
        next: (r) => {
          this.reporte.set(r);
          this.reporteDesde = r.desde;
          this.reporteHasta = r.hasta;
        },
        error: (e) =>
          this.errorReporte.set(e.error?.message ?? 'No se pudo generar el reporte semanal.'),
      });
  }

  descargarReporteExcel(): void {
    this.descargandoReporte.set(true);
    this.errorReporte.set('');
    this.reporteQueso
      .descargarExcel(this.reporteDesde || undefined, this.reporteHasta || undefined)
      .pipe(finalize(() => this.descargandoReporte.set(false)))
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `Huata_Reporte_Queso_${this.reporteDesde || 'ciclo'}_${this.reporteHasta || 'actual'}.xlsx`;
          a.click();
          URL.revokeObjectURL(url);
        },
        error: (e) =>
          this.errorReporte.set(e.error?.message ?? 'No se pudo descargar el reporte semanal.'),
      });
  }
}

/** Texto y color de la tarjeta de un producto según cuánto alcanza. */
function estadoTarjeta(c: CapacidadProduccion | null | undefined): Omit<TarjetaProducto, 'producto'> {
  if (c === undefined) return { texto: 'Mirando el almacén…', tono: 'cargando' };
  if (c === null) return { texto: 'Tócalo para ver cuánto alcanza', tono: 'aviso' };
  if (!c.tieneReceta) return { texto: 'Sin receta', tono: 'falta' };
  if (!c.recetaLlevaLeche) return { texto: 'La receta no dice cuánta leche lleva', tono: 'falta' };
  if (!c.maximoProducible) return { texto: 'No alcanza para producir', tono: 'falta' };
  const unidad = c.unidadVenta && c.unidadVenta !== 'unidad' ? c.unidadVenta : 'unidades';
  return { texto: `Alcanza para ${c.maximoProducible} ${unidad}`, tono: 'ok' };
}

/** Tres decimales: lo que se lee en una balanza, sin arrastrar el error del punto flotante. */
function redondear(n: number): number {
  return Math.round(n * 1000) / 1000;
}

/** Días de una fecha ISO a otra (negativo si la segunda ya pasó). */
function diasEntre(desde: string, hasta: string): number {
  return Math.round((Date.parse(hasta + 'T12:00:00') - Date.parse(desde + 'T12:00:00')) / 86400000);
}

/** Sin tildes ni mayúsculas, para buscar "yogur" y encontrar "Yogúr". */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
}

/** ISO local, no UTC: con toISOString() la fecha se corre un día según la zona horaria. */
function hoy(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
