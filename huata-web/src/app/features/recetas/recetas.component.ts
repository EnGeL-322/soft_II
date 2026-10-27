import { Component, HostListener, OnInit, computed, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import { MateriaPrima, Producto, Receta, TrabajadorProduccion, UnidadMedida } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { ProduccionService } from '../../core/services/produccion.service';
import {
  cantidadLegible,
  ingredientesEnPalabras,
  recetaProgramada,
  recetaVigente,
} from './recetas-texto';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

interface FilaItem {
  materiaPrimaId: number | null;
  cantidadPorUnidad: number | null;
  nombre: string;
  unidadMedida: UnidadMedida;
}

const FILA_VACIA = (): FilaItem => ({ materiaPrimaId: null, cantidadPorUnidad: null, nombre: '', unidadMedida: 'GRAMO' });

/**
 * "Recetas y productos": qué fabrica la planta (queso, yogur...) y qué lleva cada producto.
 *
 * <p>Pensada para quien no conoce la jerga: cada producto es una tarjeta que dice en palabras qué
 * lleva ("6 litros de Leche fresca"), y la receta se cambia desde esa tarjeta con un editor que ya
 * trae la receta actual. Los ingredientes se pueden crear desde el mismo recetario, sin tener
 * stock todavía: Compras será quien los ingrese al inventario cuando lleguen a planta.
 */
@Component({
  selector: 'app-recetas',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './recetas.component.html',
  styleUrl: './recetas.component.css',
})
export class RecetasComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de produccion, inventario, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['produccion', 'inventario'], () => {
    this.cargarProductos();
    this.cargar();
  });
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly router = inject(Router);
  readonly hoy = hoy();
  readonly trabajadores = signal<TrabajadorProduccion[]>([]);
  readonly guardandoTrabajador = signal(false);
  nombreTrabajador = '';
  readonly recetas = signal<Receta[]>([]);
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly items = signal<FilaItem[]>([FILA_VACIA()]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');

  // ---- Productos ----
  readonly productos = signal<Producto[]>([]);
  readonly guardandoProducto = signal(false);
  /** Formulario de producto: 'nuevo', el id del que se edita, o null (cerrado). */
  readonly formularioProducto = signal<'nuevo' | number | null>(null);
  nombreProducto = '';
  unidadVenta = 'unidad';
  descripcionProducto = '';
  /** Cuántos días dura desde que se produce; vacío = sin vencimiento. */
  vidaUtilDias: number | null = null;

  // ---- Editor de receta ----
  /** Producto cuya receta se está escribiendo; null = editor cerrado. */
  readonly recetaDe = signal<Producto | null>(null);
  vigenteDesde = hoy();
  rendimientoLitrosPorUnidad: number | null = null;
  observaciones = '';

  /** Historial completo, de la versión más nueva a la más vieja. */
  readonly historial = computed(() =>
    [...this.recetas()].sort(
      (a, b) =>
        a.productoNombre.localeCompare(b.productoNombre, 'es') ||
        b.vigenteDesde.localeCompare(a.vigenteDesde),
    ),
  );

  constructor(
    private readonly produccion: ProduccionService,
    private readonly inventario: InventarioService,
    protected readonly auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.cargarProductos();
    this.cargar();
    this.cargarTrabajadores();
    this.inventario.materiasPrimas().subscribe({ next: (m) => this.materiasPrimas.set(m) });
  }

  puedeEscribir(): boolean {
    return this.auth.puede(PERMISO.recetasWrite);
  }

  // ---- Lectura de recetas por producto ----

  recetaActual(p: Producto): Receta | null {
    return recetaVigente(this.recetas(), p.id, this.hoy);
  }

  recetaFutura(p: Producto): Receta | null {
    return recetaProgramada(this.recetas(), p.id, this.hoy);
  }

  ingredientes(r: Receta): string[] {
    return ingredientesEnPalabras(r);
  }

  unidadDe(materiaPrimaId: number | null): string {
    return etiquetaUnidad(this.materiasPrimas().find((m) => m.id === materiaPrimaId)?.unidadMedida);
  }

  cantidad(n: number): string {
    return cantidadLegible(n);
  }

  fechaDeVencimiento(): string | null {
    if (!this.vidaUtilDias || this.vidaUtilDias < 1) return null;
    const fecha = new Date(`${this.hoy}T00:00:00`);
    fecha.setDate(fecha.getDate() + Math.round(this.vidaUtilDias));
    return fecha.toLocaleDateString('es-PE', { day: '2-digit', month: 'long', year: 'numeric' });
  }

  siguienteCampo(evento: Event, id: string): void {
    evento.preventDefault();
    enfocar(id);
  }

  // ---- Productos ----

  cargarProductos(): void {
    this.produccion.productos(this.puedeEscribir()).subscribe({
      next: (p) => this.productos.set(p),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo cargar la lista de productos.'),
    });
  }

  nuevoProducto(): void {
    this.limpiarMensajes();
    this.formularioProducto.set('nuevo');
    this.nombreProducto = '';
    this.unidadVenta = 'unidad';
    this.descripcionProducto = '';
    this.vidaUtilDias = null;
    enfocar('pr-nombre');
  }

  editarProducto(p: Producto): void {
    this.limpiarMensajes();
    this.formularioProducto.set(p.id);
    this.nombreProducto = p.nombre;
    this.unidadVenta = p.unidadVenta;
    this.descripcionProducto = p.descripcion ?? '';
    this.vidaUtilDias = p.vidaUtilDias ?? null;
    enfocar('pr-nombre');
  }

  cancelarProducto(): void {
    this.formularioProducto.set(null);
  }

  guardarProducto(): void {
    if (!this.puedeEscribir() || this.guardandoProducto()) return;
    if (!this.nombreProducto.trim() || !this.unidadVenta.trim()) {
      this.error.set('Escribe el nombre del producto y cómo se vende (por unidad, por botella…).');
      return;
    }
    const datos = {
      nombre: this.nombreProducto.trim(),
      unidadVenta: this.unidadVenta.trim(),
      descripcion: this.descripcionProducto.trim() || undefined,
      vidaUtilDias: this.vidaUtilDias && this.vidaUtilDias > 0 ? Math.round(this.vidaUtilDias) : undefined,
    };
    const actual = this.formularioProducto();
    const esNuevo = actual === 'nuevo' || actual == null;
    this.guardandoProducto.set(true);
    this.limpiarMensajes();
    (esNuevo
      ? this.produccion.crearProducto(datos)
      : this.produccion.actualizarProducto(actual as number, datos)
    )
      .pipe(finalize(() => this.guardandoProducto.set(false)))
      .subscribe({
        next: (p) => {
          this.formularioProducto.set(null);
          this.cargarProductos();
          if (esNuevo) {
            // Siguiente paso natural: decir qué lleva. Se abre el editor sin que lo busquen.
            this.notificaciones.exito(`Listo, se creó "${p.nombre}". Ahora escribe qué lleva cada unidad.`);
            this.abrirReceta(p);
          } else {
            this.notificaciones.exito(`Se guardaron los cambios de "${p.nombre}".`);
          }
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo guardar el producto.'),
      });
  }

  guardarProductoConEnter(evento: Event): void {
    evento.preventDefault();
    this.guardarProducto();
  }

  cambiarEstadoProducto(p: Producto): void {
    if (!this.puedeEscribir()) return;
    this.limpiarMensajes();
    this.produccion.cambiarEstadoProducto(p.id, !p.activo).subscribe({
      next: (r) => {
        this.notificaciones.exito(
          r.activo
            ? `"${r.nombre}" se vuelve a producir.`
            : `"${r.nombre}" ya no se produce. Lo que quede guardado se puede seguir vendiendo.`,
        );
        if (!r.activo && this.recetaDe()?.id === r.id) this.recetaDe.set(null);
        this.cargarProductos();
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo cambiar el producto.'),
    });
  }

  // ---- Recetas ----

  cargar(): void {
    this.cargando.set(true);
    this.produccion
      .recetas()
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (r) => this.recetas.set(r),
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar las recetas.'),
      });
  }

  /**
   * Abre el editor con la receta que rige hoy ya cargada: cambiar una receta suele ser corregir una
   * cantidad, no volver a escribir todo.
   */
  abrirReceta(p: Producto): void {
    this.limpiarMensajesDeError();
    const actual = this.recetaActual(p);
    this.recetaDe.set(p);
    this.vigenteDesde = this.hoy;
    this.rendimientoLitrosPorUnidad = actual?.rendimientoLitrosPorUnidad ?? null;
    this.observaciones = '';
    this.items.set(
      actual && actual.items.length
        ? actual.items.map((i) => ({
            materiaPrimaId: i.materiaPrimaId,
            cantidadPorUnidad: i.cantidadPorUnidad,
            nombre: this.materiasPrimas().find((m) => m.id === i.materiaPrimaId)?.nombre ?? '',
            unidadMedida: this.materiasPrimas().find((m) => m.id === i.materiaPrimaId)?.unidadMedida ?? 'GRAMO',
          }))
        : [FILA_VACIA()],
    );
    enfocar('editor-receta');
  }

  cerrarReceta(): void {
    this.recetaDe.set(null);
  }

  agregarItem(): void {
    this.items.update((f) => [FILA_VACIA(), ...f]);
    enfocar('item-nombre-0');
  }

  async eliminarProducto(p: Producto): Promise<void> {
    const ok = await this.dialogo.confirmar({
      titulo: `¿Eliminar «${p.nombre}»?`,
      mensaje: 'Se elimina definitivamente con su receta. Esta acción no se puede deshacer.',
      confirmar: 'Eliminar',
      tipo: 'peligro',
    });
    if (!ok) return;
    this.limpiarMensajes();
    this.produccion.eliminarProducto(p.id).subscribe({
      next: () => {
        this.productos.update((productos) => productos.filter((producto) => producto.id !== p.id));
        this.notificaciones.exito(`Se eliminó "${p.nombre}".`);
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo eliminar el producto.'),
    });
  }

  siguienteCampoReceta(evento: Event, id: string): void { evento.preventDefault(); enfocar(id); }
  guardarFila(evento: Event): void {
    evento.preventDefault();
    if ((evento as KeyboardEvent).shiftKey) { this.agregarItem(); return; }
    this.guardar();
  }

  @HostListener('document:keydown', ['$event'])
  teclaRapida(evento: KeyboardEvent): void {
    if (evento.key === 'Enter' && evento.shiftKey && this.recetaDe() && !(evento.target instanceof HTMLSelectElement)) {
      evento.preventDefault(); this.agregarItem();
    }
  }

  guardarConEnter(evento: Event): void {
    evento.preventDefault();
    this.guardar();
  }

  quitarItem(index: number): void {
    this.items.update((f) => f.filter((_, i) => i !== index));
  }

  /** Una materia prima elegida dos veces en la misma receta: se avisa antes de guardar. */
  repetida(index: number): boolean {
    const id = this.items()[index]?.materiaPrimaId;
    return id != null && this.items().findIndex((i) => i.materiaPrimaId === id) !== index;
  }

  guardar(): void {
    const producto = this.recetaDe();
    if (!producto) return;
    const pendiente = this.items().find((item) => item.materiaPrimaId == null);
    if (pendiente) {
      const nombre = pendiente.nombre.trim();
      if (!nombre) {
        this.error.set('Escribe el nombre del ingrediente antes de guardarlo.');
        return;
      }
      const existente = this.materiasPrimas().find((m) => m.nombre.trim().toLocaleLowerCase('es') === nombre.toLocaleLowerCase('es'));
      if (existente) {
        this.items.update((filas) => filas.map((fila) => fila === pendiente ? { ...fila, materiaPrimaId: existente.id, unidadMedida: existente.unidadMedida } : fila));
        this.guardar();
        return;
      }
      this.guardando.set(true);
      this.inventario.crearMateriaPrima({ nombre, unidadMedida: pendiente.unidadMedida })
        .pipe(finalize(() => this.guardando.set(false)))
        .subscribe({
          next: (ingrediente) => {
            this.materiasPrimas.update((actuales) => [...actuales, ingrediente]);
            this.items.update((filas) => filas.map((fila) => fila === pendiente ? { ...fila, materiaPrimaId: ingrediente.id } : fila));
            this.guardar();
          },
          error: (e) => this.error.set(e.error?.message ?? 'No se pudo crear el ingrediente.'),
        });
      return;
    }
    const completas = this.items().filter(
      (i): i is FilaItem & { materiaPrimaId: number; cantidadPorUnidad: number } =>
        i.materiaPrimaId != null && i.cantidadPorUnidad != null && i.cantidadPorUnidad > 0,
    );
    if (completas.length === 0) {
      this.error.set('Agrega al menos una materia prima con su cantidad (mayor que cero).');
      return;
    }
    if (this.items().some((_, i) => this.repetida(i))) {
      this.error.set('Hay una materia prima repetida. Déjala una sola vez con la cantidad total.');
      return;
    }
    if (completas.length < this.items().length) {
      this.error.set('Hay filas sin completar. Elige la materia prima y su cantidad, o quita la fila.');
      return;
    }
    this.guardando.set(true);
    this.limpiarMensajes();
    this.produccion
      .crearReceta({
        productoId: producto.id,
        vigenteDesde: this.vigenteDesde,
        rendimientoLitrosPorUnidad: this.rendimientoLitrosPorUnidad ?? undefined,
        observaciones: this.observaciones.trim() || undefined,
        items: completas,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.notificaciones.exito(
            this.vigenteDesde <= this.hoy
              ? `Se guardó la receta de "${producto.nombre}". Desde hoy se produce con ella.`
              : `Se guardó la receta de "${producto.nombre}". Se empezará a usar el ${this.vigenteDesde}.`,
          );
          // Siguiente paso natural: ver si hay lo que la receta pide y comprar lo que falte.
          if (this.auth.puede(PERMISO.inventarioRead)) {
            this.router.navigate(['/inventario'], { queryParams: { producto: producto.id } });
            return;
          }
          this.cargar();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo guardar la receta.'),
      });
  }

  // ---- Trabajadores ----

  cargarTrabajadores() {
    if (!this.auth.puede(PERMISO.produccionRead)) return;
    this.produccion.trabajadores().subscribe({
      next: (t) => this.trabajadores.set(t),
      error: () => this.error.set('No se pudo cargar la lista de trabajadores.'),
    });
  }

  agregarTrabajador() {
    if (!this.puedeEscribir() || !this.nombreTrabajador.trim() || this.guardandoTrabajador())
      return;
    this.guardandoTrabajador.set(true);
    this.produccion
      .crearTrabajador(this.nombreTrabajador.trim())
      .pipe(finalize(() => this.guardandoTrabajador.set(false)))
      .subscribe({
        next: () => {
          this.nombreTrabajador = '';
          this.cargarTrabajadores();
          this.notificaciones.exito('Se agregó a la persona. Ya puedes elegirla al registrar una producción.');
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo agregar a la persona.'),
      });
  }

  private limpiarMensajes(): void {
    this.error.set('');
  }

  private limpiarMensajesDeError(): void {
    this.error.set('');
  }
}

/** Lleva la vista al elemento (y lo enfoca si es un campo) después de que Angular lo dibuje. */
function enfocar(id: string): void {
  setTimeout(() => {
    const el = document.getElementById(id);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement || el instanceof HTMLSelectElement) {
      el.focus();
    } else {
      el?.querySelector<HTMLInputElement>('input, textarea, select')?.focus();
    }
  });
}

function hoy(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
