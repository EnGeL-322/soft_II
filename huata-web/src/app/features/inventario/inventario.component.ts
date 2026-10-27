import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import {
  LoteQueso,
  MateriaPrima,
  MovimientoInventario,
  PageResponse,
  Receta,
  UnidadMedida,
} from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { ProduccionService } from '../../core/services/produccion.service';
import { QueseriaService } from '../../core/services/queseria.service';
import { avisosDeVencimiento, recetaVigenteDe, stockPorProducto, usosPorMateriaPrima } from './inventario-resumen';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { paginaDisponible } from '../../shared/ui/paginacion-local';
import { PaginadorComponent, TAMANO_PAGINA_PREDETERMINADO } from '../../shared/ui/paginador.component';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';
import { IconoComponent } from '../../shared/ui/icono.component';

type Vista = 'insumos' | 'camara' | 'movimientos';

/** Qué pasó en un movimiento, dicho como lo diría una persona, y si sumó o restó. */
const MOVIMIENTOS: Record<string, { texto: string; entra: boolean }> = {
  ENTRADA_COMPRA: { texto: 'Compra', entra: true },
  ENTRADA_ACOPIO: { texto: 'Leche recibida en planta', entra: true },
  SALIDA_PRODUCCION_CONSUMO: { texto: 'Usado en producción', entra: false },
  ENTRADA_PRODUCCION_TERMINADO: { texto: 'Producido', entra: true },
  SALIDA_VENTA: { texto: 'Vendido', entra: false },
  ENTRADA_ANULACION_VENTA: { texto: 'Venta anulada', entra: true },
  AJUSTE_ENTRADA: { texto: 'Ajuste', entra: true },
  AJUSTE_SALIDA: { texto: 'Ajuste', entra: false },
};

/**
 * Inventario: lo que hay en el almacén (insumos y leche), lo que hay en cámara (producto terminado)
 * y sus movimientos. Una vista a la vez (pedido del cliente, 2026-10-27: "no lo entiende"); el stock
 * se calcula sobre los movimientos, no es un contador aparte.
 */
@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, IconoComponent, RouterLink],
  templateUrl: './inventario.component.html',
  styleUrl: './inventario.component.css',
})
export class InventarioComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de inventario, compras, produccion, ventas, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['inventario', 'compras', 'produccion', 'ventas'], () => {
    this.cargarMateriasPrimas();
    this.cargarKardex();
    this.cargarLotes();
  });
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly vista = signal<Vista>('insumos');
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly recetas = signal<Receta[]>([]);
  readonly lotes = signal<LoteQueso[]>([]);
  readonly usos = computed(() => usosPorMateriaPrima(this.recetas(), hoyLocal()));
  readonly terminado = computed(() => stockPorProducto(this.lotes()));
  /** Vencidos o que vencen en 3 días o menos, con stock: lo que hay que vender o retirar primero. */
  readonly vencimientos = computed(() => avisosDeVencimiento(this.lotes(), hoyLocal()));
  readonly kardex = signal<PageResponse<MovimientoInventario> | null>(null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');
  readonly busqueda = signal('');
  /** Fila con el menú "⋯" abierto. */
  readonly menuAbiertoId = signal<number | null>(null);

  page = 0;
  size: number = TAMANO_PAGINA_PREDETERMINADO;
  materiaPrimaFiltro: number | null = null;

  /** Llegando desde Recetas (?producto=): solo los insumos de esa receta, con cuánto lleva cada unidad. */
  readonly productoReceta = signal<number | null>(null);
  readonly recetaElegida = computed(() => {
    const id = this.productoReceta();
    return id == null ? null : recetaVigenteDe(this.recetas(), id, hoyLocal());
  });

  readonly insumosVisibles = computed(() => {
    const q = this.busqueda().trim().toLowerCase();
    const receta = this.recetaElegida();
    const deReceta = receta ? new Set(receta.items.map((i) => i.materiaPrimaId)) : null;
    return this.materiasPrimas()
      .filter((m) => !deReceta || deReceta.has(m.id))
      .filter((m) => !q || m.nombre.toLowerCase().includes(q))
      .sort((a, b) => Number(b.lecheAcopio ?? false) - Number(a.lecheAcopio ?? false) || a.nombre.localeCompare(b.nombre, 'es'));
  });
  readonly agotados = computed(() => this.materiasPrimas().filter((m) => !m.lecheAcopio && m.stock <= 0));
  readonly unidadesEnCamara = computed(() => this.terminado().reduce((s, p) => s + p.stock, 0));

  constructor(
    private readonly inventario: InventarioService,
    private readonly produccion: ProduccionService,
    private readonly queseria: QueseriaService,
    protected readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    const producto = Number(this.ruta.snapshot.queryParamMap.get('producto'));
    if (producto) this.productoReceta.set(producto);
    this.cargarMateriasPrimas();
    this.cargarKardex();
    // Cada bloque solo se pide si el rol puede leerlo: el backend lo rechazaria igual.
    if (this.auth.puede(PERMISO.recetasRead))
      this.produccion.recetas().subscribe({ next: (r) => this.recetas.set(r) });
    this.cargarLotes();
  }

  private cargarLotes(): void {
    if (this.puedeVerTerminado()) this.queseria.lotes().subscribe({ next: (l) => this.lotes.set(l) });
  }

  puedeVerTerminado(): boolean {
    return this.auth.puede(PERMISO.quesosRead) || this.auth.puede(PERMISO.produccionRead);
  }

  puedeEscribir(): boolean {
    return this.auth.puede(PERMISO.inventarioWrite);
  }

  puedeComprar(): boolean {
    return this.auth.puede(PERMISO.comprasWrite);
  }

  /** Cuánto lleva una unidad del producto de la receta elegida ("0.5 kg por unidad"). */
  porUnidad(materiaPrimaId: number): number | null {
    return this.recetaElegida()?.items.find((i) => i.materiaPrimaId === materiaPrimaId)?.cantidadPorUnidad ?? null;
  }

  verTodosLosInsumos(): void {
    this.productoReceta.set(null);
    this.router.navigate([], { relativeTo: this.ruta, queryParams: {} });
  }

  cambiarVista(v: Vista): void {
    this.vista.set(v);
    this.menuAbiertoId.set(null);
  }

  unidad(m: { unidadMedida: string } | undefined): string {
    return etiquetaUnidad(m?.unidadMedida);
  }

  usosDe(materiaPrimaId: number): string {
    return this.usos().get(materiaPrimaId)?.join(', ') ?? '';
  }

  // ---- Movimientos ----

  textoMovimiento(m: MovimientoInventario): string {
    return MOVIMIENTOS[m.tipo]?.texto ?? m.tipo;
  }

  entra(m: MovimientoInventario): boolean {
    return MOVIMIENTOS[m.tipo]?.entra ?? true;
  }

  /** "Sal" o "Queso paria · lote L2026-0012". */
  queMovio(m: MovimientoInventario): string {
    return m.materiaPrimaNombre ?? `Lote ${m.loteQuesoCodigo}`;
  }

  unidadMovimiento(m: MovimientoInventario): string {
    if (m.materiaPrimaId == null) return 'u';
    return this.unidad(this.materiasPrimas().find((x) => x.id === m.materiaPrimaId));
  }

  fechaCorta(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('es-PE', { day: 'numeric', month: 'short' }).replace('.', '');
  }

  cargarMateriasPrimas(): void {
    this.inventario.materiasPrimas().subscribe({ next: (m) => this.materiasPrimas.set(m) });
  }

  cargarKardex(): void {
    this.cargando.set(true);
    this.inventario
      .kardex({ materiaPrimaId: this.materiaPrimaFiltro ?? undefined, page: this.page, size: this.size })
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (p) => {
          const otra = paginaDisponible(p);
          if (otra !== null) return this.cambiarPagina(otra);
          this.kardex.set(p);
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar los movimientos.'),
      });
  }

  verMovimientosDe(id: number | null): void {
    this.materiaPrimaFiltro = id;
    this.page = 0;
    this.cargarKardex();
    this.cambiarVista('movimientos');
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargarKardex();
  }

  cambiarTamano(tamano: number): void {
    this.size = tamano;
    this.cambiarPagina(0);
  }

  // ---- Editar y eliminar ----

  readonly unidades: ReadonlyArray<{ valor: UnidadMedida; texto: string }> = [
    { valor: 'KG', texto: 'Kilos (kg)' },
    { valor: 'GRAMO', texto: 'Gramos (g)' },
    { valor: 'LITRO', texto: 'Litros' },
    { valor: 'ML', texto: 'Mililitros (ml)' },
    { valor: 'UNIDAD', texto: 'Unidades' },
  ];
  readonly editando = signal<MateriaPrima | null>(null);
  nombreEdicion = '';
  unidadEdicion: UnidadMedida = 'KG';
  costoEdicion: number | null = null;
  readonly errorEdicion = signal('');

  editarMateriaPrima(m: MateriaPrima): void {
    this.menuAbiertoId.set(null);
    this.editando.set(m);
    this.nombreEdicion = m.nombre;
    this.unidadEdicion = m.unidadMedida;
    this.costoEdicion = m.costoUnitarioReferencial ?? null;
    this.errorEdicion.set('');
  }

  cancelarEdicion(): void {
    this.editando.set(null);
  }

  guardarEdicion(): void {
    const m = this.editando();
    if (!m) return;
    if (!this.nombreEdicion.trim()) {
      this.errorEdicion.set('Escribe el nombre.');
      return;
    }
    this.guardando.set(true);
    this.errorEdicion.set('');
    this.inventario
      .actualizarMateriaPrima(m.id, {
        nombre: this.nombreEdicion.trim(),
        unidadMedida: this.unidadEdicion,
        costoUnitarioReferencial: this.costoEdicion ?? undefined,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.notificaciones.exito('Insumo actualizado.');
          this.editando.set(null);
          this.cargarMateriasPrimas();
        },
        error: (e) => this.errorEdicion.set(e.error?.message ?? 'No se pudo actualizar el insumo.'),
      });
  }

  /** Borrar exige escribir el nombre (pedido del cliente): un toque por error no borra nada. */
  async eliminarMateriaPrima(m: MateriaPrima): Promise<void> {
    this.menuAbiertoId.set(null);
    const escrito = await this.dialogo.pedirTexto({
      titulo: `¿Eliminar «${m.nombre}»?`,
      mensaje: 'Deja de aparecer en compras, recetas e inventario. Escribe su nombre para confirmar.',
      etiqueta: 'Nombre del insumo',
      obligatorio: true,
      confirmar: 'Eliminar',
      cancelar: 'Volver',
      tipo: 'peligro',
    });
    if (!escrito) return;
    if (escrito.trim().toLowerCase() !== m.nombre.trim().toLowerCase()) {
      this.notificaciones.error('El nombre no coincide: no se eliminó nada.');
      return;
    }
    this.inventario.inactivarMateriaPrima(m.id).subscribe({
      next: () => {
        this.notificaciones.exito(`Se eliminó «${m.nombre}».`);
        this.cargarMateriasPrimas();
      },
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo eliminar el insumo.'),
    });
  }

  // ---- Ajuste de stock (merma, conteo físico, leche descartada) ----

  /** Motivos frecuentes: solo rellenan el texto, que sigue siendo libre. */
  readonly motivosAjuste = ['Merma', 'Conteo físico', 'Leche descartada', 'Vencido o dañado', 'Corrección de registro'];
  readonly ajustando = signal<MateriaPrima | null>(null);
  ajusteTipo: 'SALIDA' | 'ENTRADA' = 'SALIDA';
  ajusteCantidad: number | null = null;
  ajusteMotivo = '';
  readonly errorAjuste = signal('');

  /** Stock que quedará si se guarda el ajuste, para que la persona vea el efecto antes. */
  stockResultante(): number | null {
    const m = this.ajustando();
    if (!m || !this.ajusteCantidad || this.ajusteCantidad <= 0) return null;
    return this.ajusteTipo === 'SALIDA' ? m.stock - this.ajusteCantidad : m.stock + this.ajusteCantidad;
  }

  abrirAjuste(m: MateriaPrima): void {
    this.menuAbiertoId.set(null);
    this.ajustando.set(m);
    this.ajusteTipo = 'SALIDA';
    this.ajusteCantidad = null;
    this.ajusteMotivo = '';
    this.errorAjuste.set('');
  }

  cerrarAjuste(): void {
    this.ajustando.set(null);
  }

  guardarAjuste(): void {
    const m = this.ajustando();
    if (!m) return;
    if (!this.ajusteCantidad || this.ajusteCantidad <= 0) {
      this.errorAjuste.set('Indica la cantidad.');
      return;
    }
    if (this.ajusteMotivo.trim().length < 3) {
      this.errorAjuste.set('Indica el motivo del ajuste.');
      return;
    }
    const resultante = this.stockResultante();
    if (resultante != null && resultante < 0) {
      this.errorAjuste.set(`No puedes sacar más de lo que hay (${m.stock} ${this.unidad(m)}).`);
      return;
    }
    this.guardando.set(true);
    this.errorAjuste.set('');
    this.inventario
      .ajustar({ materiaPrimaId: m.id, tipo: this.ajusteTipo, cantidad: this.ajusteCantidad, motivo: this.ajusteMotivo.trim() })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.notificaciones.exito(`Stock de ${m.nombre} ajustado.`);
          this.ajustando.set(null);
          this.cargarMateriasPrimas();
          this.cargarKardex();
        },
        error: (e) => this.errorAjuste.set(e.error?.message ?? 'No se pudo guardar el ajuste.'),
      });
  }
}

function hoyLocal(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
