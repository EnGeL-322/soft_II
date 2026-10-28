import { Component, DestroyRef, HostListener, OnInit, computed, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import { MateriaPrima, Producto, Receta, TrabajadorProduccion } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { InventarioService } from '../../core/services/inventario.service';
import { ProduccionService } from '../../core/services/produccion.service';
import {
  cantidadLegible,
  ingredientesEnPalabras,
  recetaProgramada,
  recetaVigente,
} from './recetas-texto';
import {
  EstadoFila,
  FilaIngrediente,
  UNIDADES,
  estadoDeFila,
  filaVacia,
  filasDeReceta,
  filasRepetidas,
  itemsParaGuardar,
  problemaDeFila,
} from './editor-receta';
import { buscarChoque, nombresIguales } from '../../core/util/nombres-parecidos';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';
import { IconoComponent } from '../../shared/ui/icono.component';


/**
 * "Recetas y productos": qué fabrica la planta (queso, yogur...) y qué lleva cada producto.
 *
 * <p>Pensada para quien no conoce la jerga: una tabla de productos donde cada fila dice en palabras
 * qué lleva ("6 litros de Leche fresca"), y la receta se cambia desde esa fila en un panel lateral
 * que ya trae la receta actual. Los ingredientes se escriben a mano: si ya existen se usan (con su unidad),
 * si se parecen a otro se pregunta, y si son nuevos se crean junto con la receta en una sola
 * operación del servidor. Compras será quien los ingrese al inventario cuando lleguen a planta.
 */
@Component({
  selector: 'app-recetas',
  standalone: true,
  imports: [FormsModule, IconoComponent, RouterLink],
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
  private readonly ruta = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Catálogo (los productos que se venden) y Recetas (qué lleva cada uno) son dos vistas de la misma
   * pantalla (`?vista=`), como en el prototipo aprobado: separadas a la vista, un solo lugar en el código.
   */
  readonly vista = signal<'catalogo' | 'recetas'>('catalogo');
  /** «Nueva receta»: primero se elige el producto. */
  readonly eligiendoProducto = signal(false);
  readonly hoy = hoy();
  readonly trabajadores = signal<TrabajadorProduccion[]>([]);
  readonly guardandoTrabajador = signal(false);
  nombreTrabajador = '';
  readonly recetas = signal<Receta[]>([]);
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly filas = signal<FilaIngrediente[]>([filaVacia()]);
  /** Error del editor de receta: se muestra dentro de la ventana, donde la persona está mirando. */
  readonly errorReceta = signal('');
  /** Se intentó guardar: desde ahí se marcan las filas con problemas. */
  readonly intentoGuardar = signal(false);
  readonly unidades = UNIDADES;

  /** Estado de cada fila (existente, nueva, parecida a otra, dada de baja) y su problema, si tiene. */
  readonly analisis = computed(() => {
    const materias = this.materiasPrimas();
    const filas = this.filas();
    const repetidas = filasRepetidas(filas);
    return filas.map((fila, i) => {
      const estado = estadoDeFila(fila, materias);
      const problema = problemaDeFila(fila, estado, repetidas.has(i));
      // Repetido, dado de baja o casi igual a otro se avisa en cuanto pasa; la cantidad, al guardar.
      const inmediato =
        repetidas.has(i) || estado.tipo === 'baja' || (estado.tipo === 'nuevo' && estado.parecido != null);
      return { estado, problema, inmediato };
    });
  });

  /** Para la lista de sugerencias: solo lo que hoy está en el inventario. */
  readonly activas = computed(() => this.materiasPrimas().filter((m) => m.activo !== false));

  readonly resumenReceta = computed(() => {
    const a = this.analisis();
    const usados = a.filter((x) => x.estado.tipo !== 'vacia').length;
    const nuevos = a.filter((x) => x.estado.tipo === 'nuevo' || x.estado.tipo === 'reactivar').length;
    return { usados, nuevos, problemas: a.filter((x) => x.problema).length };
  });
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

  /** Formas de venta frecuentes, como atajo al escribir "Se vende por". */
  readonly formasVenta = ['unidad', 'kg', 'botella 1 L', 'vaso', 'balde 4 kg'];

  /** Busca por nombre, sin tildes ni mayúsculas. */
  readonly busqueda = signal('');
  readonly productosVisibles = computed(() => {
    const q = sinTildes(this.busqueda());
    return q ? this.productos().filter((p) => sinTildes(p.nombre).includes(q)) : this.productos();
  });

  /** Los números de arriba: cuántos se producen, cuántos no tienen receta y cuántos están pausados. */
  readonly resumen = computed(() => {
    const productos = this.productos();
    const recetas = this.recetas();
    const activos = productos.filter((p) => p.activo);
    return {
      activos: activos.length,
      pausados: productos.length - activos.length,
      sinReceta: activos.filter((p) => !recetaVigente(recetas, p.id, this.hoy)).length,
    };
  });

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
    this.cargarMateriasPrimas();
    this.ruta.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => this.vista.set(p.get('vista') === 'recetas' ? 'recetas' : 'catalogo'));
  }

  /** Productos que se producen y todavía no tienen receta: los que ofrece «Nueva receta». */
  readonly sinReceta = computed(() =>
    this.productos().filter((p) => p.activo && !recetaVigente(this.recetas(), p.id, this.hoy)),
  );

  nuevaReceta(): void {
    const pendientes = this.sinReceta();
    if (pendientes.length === 1) {
      this.abrirReceta(pendientes[0]);
    } else if (pendientes.length) {
      this.eligiendoProducto.set(true);
    } else {
      this.notificaciones.info('Todos los productos ya tienen receta. Para cambiar una, usa «Editar» en su fila.');
    }
  }

  elegirProductoParaReceta(p: Producto): void {
    this.eligiendoProducto.set(false);
    this.abrirReceta(p);
  }

  private cargarMateriasPrimas(): void {
    // Con las dadas de baja: así una fila con su nombre dice "se reactivará" y no "nuevo".
    this.inventario.materiasPrimas(true).subscribe({ next: (m) => this.materiasPrimas.set(m) });
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

  unidad(u: string | null | undefined): string {
    return etiquetaUnidad(u);
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

  /**
   * Otro producto con el mismo nombre o casi igual (regla del cliente: "yogurd" junto a "yogurt" no
   * se permite). Se calcula al escribir para frenar antes de guardar; el servidor lo vuelve a validar.
   */
  choqueProducto(): { producto: Producto; igual: boolean } | null {
    const nombre = this.nombreProducto.trim();
    if (!nombre) return null;
    const propio = this.formularioProducto();
    const otros = this.productos().filter((p) => p.id !== propio);
    const producto = buscarChoque(nombre, otros, (p) => p.nombre);
    return producto ? { producto, igual: nombresIguales(nombre, producto.nombre) } : null;
  }

  /** "Usar el existente": cierra el alta y abre la receta del producto que ya estaba. */
  irAProducto(p: Producto): void {
    this.formularioProducto.set(null);
    if (p.activo) this.abrirReceta(p);
    else this.notificaciones.info(`«${p.nombre}» está pausado: usa «Reanudar» en su tarjeta para volver a producirlo.`);
  }

  guardarProducto(): void {
    if (!this.puedeEscribir() || this.guardandoProducto()) return;
    if (this.choqueProducto()) return;
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
   * cantidad, no volver a escribir todo. Los nombres salen de la receta guardada, así un ingrediente
   * dado de baja se ve (y se avisa) en vez de quedar como una fila en blanco.
   */
  abrirReceta(p: Producto): void {
    this.limpiarMensajesDeError();
    this.errorReceta.set('');
    this.intentoGuardar.set(false);
    const actual = this.recetaActual(p);
    this.recetaDe.set(p);
    this.vigenteDesde = this.hoy;
    this.rendimientoLitrosPorUnidad = actual?.rendimientoLitrosPorUnidad ?? null;
    this.observaciones = '';
    this.filas.set(filasDeReceta(actual));
    // El inventario pudo cambiar desde que se abrió la página (otra pestaña, otra persona).
    this.cargarMateriasPrimas();
    enfocar('item-nombre-0');
  }

  cerrarReceta(): void {
    if (this.guardando()) return;
    this.recetaDe.set(null);
  }

  /** Las filas nuevas van al final, como en cualquier lista: la persona sigue leyendo hacia abajo. */
  agregarItem(): void {
    this.filas.update((f) => [...f, filaVacia()]);
    enfocar(`item-nombre-${this.filas().length - 1}`);
  }

  /** Cambia un campo de una fila sin mutar el arreglo (las señales derivadas se recalculan). */
  cambiarFila(index: number, cambios: Partial<FilaIngrediente>): void {
    this.errorReceta.set('');
    this.filas.update((f) => f.map((fila, i) => (i === index ? { ...fila, ...cambios } : fila)));
  }

  /** «¿Quisiste decir sal?»: usa el existente en vez de crear un segundo insumo por un error de tipeo. */
  usarParecido(index: number, materia: MateriaPrima): void {
    this.cambiarFila(index, { nombre: materia.nombre });
  }

  volverAUsar(index: number): void {
    this.cambiarFila(index, { reactivar: true });
  }

  estado(index: number): EstadoFila {
    return this.analisis()[index]?.estado ?? { tipo: 'vacia' };
  }

  problema(index: number): string | null {
    return this.analisis()[index]?.problema ?? null;
  }

  /** Unidad en la que se lee la cantidad de la fila: la del inventario, o la elegida si es nuevo. */
  unidadDeFila(index: number): string {
    const est = this.estado(index);
    const fila = this.filas()[index];
    if (est.tipo === 'existente') return etiquetaUnidad(est.materia.unidadMedida);
    if (est.tipo === 'baja' || est.tipo === 'reactivar') return etiquetaUnidad(est.unidad);
    return etiquetaUnidad(fila?.unidad);
  }

  /** Stock de un ingrediente existente: al escribir la receta ya se ve si hay o hay que comprar. */
  stockDe(m: MateriaPrima): string {
    return `${cantidadLegible(m.stock ?? 0)} ${etiquetaUnidad(m.unidadMedida)}`;
  }

  /** Solo lo que nunca se produjo: con producciones, el historial se conserva y se pausa. */
  sePuedeEliminar(p: Producto): boolean {
    return !p.lotesRegistrados;
  }

  async eliminarProducto(p: Producto): Promise<void> {
    if (!this.sePuedeEliminar(p)) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Eliminar «${p.nombre}»?`,
      mensaje: 'Se borra para siempre junto con su receta y sus precios de venta. No se puede deshacer.',
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
      // En aviso flotante: el mensaje de arriba de la página no se ve desde la tarjeta.
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo eliminar el producto.'),
    });
  }

  siguienteCampoReceta(evento: Event, id: string): void {
    evento.preventDefault();
    enfocar(id);
  }

  /** Enter en la cantidad: pasa a la fila siguiente, o crea una si era la última. */
  siguienteFila(evento: Event, index: number): void {
    evento.preventDefault();
    if (index === this.filas().length - 1) this.agregarItem();
    else enfocar(`item-nombre-${index + 1}`);
  }

  @HostListener('document:keydown', ['$event'])
  teclaRapida(evento: KeyboardEvent): void {
    if (!this.recetaDe()) return;
    if (evento.key === 'Escape') {
      this.cerrarReceta();
    } else if (evento.key === 'Enter' && (evento.ctrlKey || evento.metaKey)) {
      evento.preventDefault();
      this.guardar();
    }
  }

  quitarItem(index: number): void {
    this.errorReceta.set('');
    this.filas.update((f) => (f.length === 1 ? [filaVacia()] : f.filter((_, i) => i !== index)));
  }

  /**
   * Guarda en una sola petición: los ingredientes nuevos se crean (o reactivan) en el servidor junto
   * con la receta. Antes se creaban uno por uno desde aquí y, si la receta fallaba, quedaban insumos
   * sueltos en el inventario.
   */
  guardar(): void {
    const producto = this.recetaDe();
    if (!producto || this.guardando()) return;
    this.intentoGuardar.set(true);
    const { usados, problemas } = this.resumenReceta();
    if (usados === 0) {
      this.errorReceta.set('Agrega al menos un ingrediente con su cantidad.');
      enfocar('item-nombre-0');
      return;
    }
    if (problemas > 0) {
      const primera = this.analisis().findIndex((x) => x.problema);
      this.errorReceta.set(
        problemas === 1 ? 'Revisa la fila marcada antes de guardar.' : `Revisa las ${problemas} filas marcadas antes de guardar.`,
      );
      enfocar(`item-nombre-${primera}`);
      return;
    }
    this.guardando.set(true);
    this.errorReceta.set('');
    this.produccion
      .crearReceta({
        productoId: producto.id,
        vigenteDesde: this.vigenteDesde,
        rendimientoLitrosPorUnidad: this.rendimientoLitrosPorUnidad ?? undefined,
        observaciones: this.observaciones.trim() || undefined,
        items: itemsParaGuardar(this.filas(), this.materiasPrimas()),
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: () => {
          this.recetaDe.set(null);
          this.notificaciones.exito(
            this.vigenteDesde <= this.hoy
              ? `Se guardó la receta de "${producto.nombre}". Desde hoy se produce con ella.`
              : `Se guardó la receta de "${producto.nombre}". Se empezará a usar el ${this.vigenteDesde}.`,
          );
          this.cargarMateriasPrimas();
          // Siguiente paso natural: ver si hay lo que la receta pide y comprar lo que falte.
          if (this.auth.puede(PERMISO.inventarioRead)) {
            this.router.navigate(['/inventario'], { queryParams: { producto: producto.id } });
            return;
          }
          this.cargar();
        },
        error: (e) => this.errorReceta.set(e.error?.message ?? 'No se pudo guardar la receta.'),
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

function sinTildes(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}
