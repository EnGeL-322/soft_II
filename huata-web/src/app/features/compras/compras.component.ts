import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { Component, DestroyRef, OnInit, computed, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import { Compra, MateriaPrima, Necesidad, PageResponse, ProveedorCompra, Recepcion } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { ComprasService } from '../../core/services/compras.service';
import { InventarioService } from '../../core/services/inventario.service';
import { ProduccionService } from '../../core/services/produccion.service';
import { usosPorMateriaPrima } from '../inventario/inventario-resumen';
import { Receta } from '../../core/models/models';
import { paginaDisponible } from '../../shared/ui/paginacion-local';
import { PaginadorComponent, TAMANO_PAGINA_PREDETERMINADO } from '../../shared/ui/paginador.component';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';
import { IconoComponent } from '../../shared/ui/icono.component';
import {
  FilaCompraItem,
  FilaRecepcion,
  avisoDePrecio,
  filaCompraVacia,
  filaEnBlanco,
  filasDeRecepcion,
  filasRecibidas,
  porQueNoAparece,
  problemaDeFila,
  problemaDeRecepcion,
} from './compra-formulario';

/** Compras de materia prima/insumos (4.3): cada compra entra automáticamente al inventario. */
@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, BuscadorComponent, IconoComponent, RouterLink],
  templateUrl: './compras.component.html',
  styles: `
    .cp-fila { grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr) minmax(0, 1fr) 96px 36px; }
    .cp-subtotal { align-self: center; padding-top: 18px; text-align: right; }
    .cp-quitar { margin-top: 24px; }
    .cp-recepcion { grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1fr); align-items: end; }
    .cp-llego { display: flex; gap: 10px; align-items: flex-start; padding: 12px 14px; border: 1px solid #e3eaf3; border-radius: 12px; background: #f7f9fc; cursor: pointer; }
    .cp-llego input { margin-top: 3px; width: 18px; height: 18px; }
    .cp-llego small { display: block; color: var(--muted); font-size: 12.5px; }
    .cp-marcar { display: inline-flex; gap: 6px; align-items: center; font-size: 13px; font-weight: 600; white-space: nowrap; }
    .cp-mensaje { grid-column: 1 / -1; margin: -2px 0 0; font-size: 13px; font-weight: 600; }
    .cp-mensaje.falta { color: var(--err); }
    .cp-mensaje.aviso { color: var(--warn); }
    @media (max-width: 760px) {
      .cp-fila { grid-template-columns: 1fr 1fr; }
      .cp-fila > .pd-campo:first-child { grid-column: 1 / -1; }
      .cp-subtotal { padding-top: 0; text-align: left; }
      .cp-quitar { margin-top: 0; justify-self: end; align-self: center; }
    }
  `,
})
export class ComprasComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de compras, inventario, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['compras', 'inventario', 'produccion'], () => {
    this.cargarProveedores();
    this.cargarCompras();
    this.cargarBandeja();
  });
  private readonly destroyRef = inject(DestroyRef);

  /** Compras y Recepciones: dos vistas de la misma pantalla (`?vista=`), como en el prototipo aprobado. */
  readonly vista = signal<'compras' | 'recepciones'>('compras');

  // ---- Bandeja de recepción y sugerencias (V61) ----
  /** Compras que todavía no llegan del todo. */
  readonly pendientes = signal<Compra[]>([]);
  readonly recepciones = signal<Recepcion[]>([]);
  /** Lo que falta comprar para las órdenes de producción pendientes. */
  readonly necesidades = signal<Necesidad[]>([]);
  /** Insumos de la sugerencia marcados para comprar en bloque. */
  readonly marcadas = signal<ReadonlySet<number>>(new Set());
  /** La compra que se está recibiendo (formulario de recepción abierto). */
  readonly recibiendo = signal<Compra | null>(null);
  readonly filasRecepcion = signal<FilaRecepcion[]>([]);
  readonly guardandoRecepcion = signal(false);
  readonly errorRecepcion = signal('');
  fechaRecepcion = hoy();
  documentoRecepcion = '';
  /**
   * La compra al contado que llega con la factura entra al almacén en el mismo acto. Por defecto no:
   * la compra queda pendiente y la recepción confirma lo que realmente llegó (prototipo aprobado).
   */
  recibirAhora = false;

  readonly porComprar = computed(() => this.necesidades().filter((n) => !n.esLeche && n.aComprar > 0));
  readonly lecheQueFalta = computed(() => this.necesidades().find((n) => n.esLeche) ?? null);
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly produccion = inject(ProduccionService);
  /** null = no se pudieron leer las recetas (sin permiso): entonces se ofrecen todos los insumos. */
  readonly recetas = signal<Receta[] | null>(null);
  readonly proveedores = signal<ProveedorCompra[]>([]);
  readonly materiasPrimas = signal<MateriaPrima[]>([]);
  readonly compras = signal<PageResponse<Compra> | null>(null);
  readonly items = signal<FilaCompraItem[]>([filaCompraVacia()]);
  /** Tras el primer intento de guardar se marcan las filas incompletas (antes, solo al tocarlas). */
  readonly intentoGuardar = signal(false);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly formularioAbierto = signal(false);
  /** Alta rápida de un proveedor de insumos, dentro del formulario de compra. */
  readonly nuevoProveedorAbierto = signal(false);
  readonly error = signal('');

  /** Los números de arriba, sobre la página que se está viendo (las anuladas no suman). */
  readonly resumen = computed(() => {
    const compras = this.compras()?.content ?? [];
    const activas = compras.filter((c) => c.activo);
    return {
      total: this.compras()?.totalElements ?? 0,
      monto: activas.reduce((s, c) => s + c.total, 0),
      anuladas: compras.length - activas.length,
    };
  });

  proveedorCompraId: number | null = null;
  fecha = hoy();
  readonly hoy = hoy();

  /** Mensaje de la lista de insumos cuando lo escrito no aparece: dice por qué y dónde se arregla. */
  readonly porQueNoAparece = (texto: string) => porQueNoAparece(texto, this.materiasPrimas());
  documento = '';
  page = 0;
  size: number = TAMANO_PAGINA_PREDETERMINADO;

  nombreProveedor = '';
  contactoProveedor = '';

  constructor(
    private readonly compraService: ComprasService,
    private readonly inventario: InventarioService,
    protected readonly auth: AuthService,
  ) {}

  readonly opcProveedoresCompra = computed<OpcionBuscador[]>(() =>
    this.proveedores().map((p) => ({ valor: p.id, etiqueta: `${p.codigo} · ${p.nombre}` })),
  );

  /**
   * Solo se compra lo que alguna receta vigente usa (pedido del cliente, 2026-10-27): comprar algo que
   * ningún producto lleva es plata parada. La leche no se compra: llega del acopio.
   */
  readonly insumosComprables = computed(() => {
    const recetas = this.recetas();
    const enRecetas = recetas ? usosPorMateriaPrima(recetas, hoy()) : null;
    return this.materiasPrimas().filter((m) => !m.lecheAcopio && (!enRecetas || enRecetas.has(m.id)));
  });

  readonly opcMateriasPrimas = computed<OpcionBuscador[]>(() => {
    const usos = this.recetas() ? usosPorMateriaPrima(this.recetas()!, hoy()) : null;
    return this.insumosComprables().map((m) => ({
      valor: m.id,
      etiqueta: m.nombre,
      detalle: usos?.get(m.id)?.length ? `Para ${usos.get(m.id)!.join(', ')}` : etiquetaUnidad(m.unidadMedida),
    }));
  });

  ngOnInit(): void {
    this.cargarProveedores();
    this.inventario.materiasPrimas().subscribe({
      next: (m) => {
        this.materiasPrimas.set(m);
        // Si Producción abrió Compras con el insumo puesto, ahora ya se conoce su último precio.
        this.items().forEach((f) => this.ultimoPrecio(f));
      },
    });
    if (this.auth.puede(PERMISO.recetasRead)) {
      this.produccion.recetas().subscribe({ next: (r) => this.recetas.set(r), error: () => this.recetas.set(null) });
    }
    this.cargarCompras();
    this.cargarBandeja();
    this.prellenarDesdeProduccion();
    this.ruta.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((p) => this.vista.set(p.get('vista') === 'recepciones' ? 'recepciones' : 'compras'));
  }

  /** Pendientes de recepción, historial de recepciones y sugerencias de compra. */
  cargarBandeja(): void {
    this.compraService.pendientes().subscribe({ next: (p) => this.pendientes.set(p), error: () => {} });
    this.compraService.recepciones().subscribe({ next: (r) => this.recepciones.set(r), error: () => {} });
    this.produccion.necesidades().subscribe({
      next: (n) => {
        this.necesidades.set(n);
        // Por defecto todo marcado, como en el prototipo: lo normal es comprar el bloque completo.
        this.marcadas.set(new Set(n.filter((x) => !x.esLeche && x.aComprar > 0).map((x) => x.materiaPrimaId)));
      },
      error: () => this.necesidades.set([]),
    });
  }

  alternarMarcada(id: number): void {
    this.marcadas.update((s) => {
      const nuevo = new Set(s);
      if (nuevo.has(id)) nuevo.delete(id);
      else nuevo.add(id);
      return nuevo;
    });
  }

  marcarTodas(todas: boolean): void {
    this.marcadas.set(new Set(todas ? this.porComprar().map((n) => n.materiaPrimaId) : []));
  }

  /**
   * «Agregar bloque a compra»: una sola compra con una línea por insumo marcado, con lo que falta
   * (redondeado hacia arriba) y el último precio pagado. Proveedor y precios los confirma la persona.
   */
  comprarBloque(): void {
    const elegidas = this.porComprar().filter((n) => this.marcadas().has(n.materiaPrimaId));
    if (!elegidas.length) {
      this.notificaciones.info('Marca al menos un insumo de la sugerencia.');
      return;
    }
    this.items.set(
      elegidas.map((n) => ({
        materiaPrimaId: n.materiaPrimaId,
        cantidad: Math.ceil(n.aComprar * 1000) / 1000,
        costoUnitario: n.costoReferencial && n.costoReferencial > 0 ? n.costoReferencial : null,
      })),
    );
    this.intentoGuardar.set(false);
    this.abrirFormulario();
  }

  estadoCompra(c: Compra): { texto: string; tono: 'ok' | 'aviso' | 'falta' | 'info' } {
    if (!c.activo) return { texto: 'Anulada', tono: 'falta' };
    if (c.estado === 'PENDIENTE_RECEPCION') return { texto: 'Por recibir', tono: 'aviso' };
    if (c.estado === 'RECIBIDA_PARCIAL') return { texto: 'Recibida en parte', tono: 'info' };
    return { texto: 'En almacén', tono: 'ok' };
  }

  // ---------------------------------------------------------------- Recepción

  abrirRecepcion(c: Compra): void {
    this.recibiendo.set(c);
    this.filasRecepcion.set(filasDeRecepcion(c, (u) => etiquetaUnidad(u)));
    this.fechaRecepcion = hoy();
    this.documentoRecepcion = c.documento ?? '';
    this.errorRecepcion.set('');
  }

  cerrarRecepcion(): void {
    this.recibiendo.set(null);
  }

  problemaRecepcion(f: FilaRecepcion): string | null {
    return problemaDeRecepcion(f, this.fechaRecepcion);
  }

  totalRecepcion(): number {
    const c = this.recibiendo();
    if (!c) return 0;
    return filasRecibidas(this.filasRecepcion()).reduce(
      (s, f) => s + (f.cantidad ?? 0) * (c.items.find((i) => i.id === f.compraItemId)?.costoUnitario ?? 0),
      0,
    );
  }

  guardarRecepcion(): void {
    const c = this.recibiendo();
    if (!c || this.guardandoRecepcion()) return;
    const filas = this.filasRecepcion();
    if (filas.some((f) => this.problemaRecepcion(f))) {
      this.errorRecepcion.set('Corrige las líneas marcadas.');
      return;
    }
    const llegaron = filasRecibidas(filas);
    if (!llegaron.length) {
      this.errorRecepcion.set('Escribe cuánto llegó de al menos un insumo.');
      return;
    }
    if (this.fechaRecepcion > hoy()) {
      this.errorRecepcion.set('La fecha no puede ser futura.');
      return;
    }
    this.guardandoRecepcion.set(true);
    this.errorRecepcion.set('');
    this.compraService
      .recibir(c.id, {
        fecha: this.fechaRecepcion,
        documento: this.documentoRecepcion.trim() || undefined,
        items: llegaron.map((f) => ({
          compraItemId: f.compraItemId,
          cantidad: f.cantidad!,
          lote: f.lote.trim() || undefined,
          fechaVencimiento: f.fechaVencimiento || undefined,
        })),
      })
      .pipe(finalize(() => this.guardandoRecepcion.set(false)))
      .subscribe({
        next: (actualizada) => {
          this.recibiendo.set(null);
          this.notificaciones.exito(
            actualizada.estado === 'RECIBIDA'
              ? `${actualizada.codigo} recibida completa. El stock ya se actualizó.`
              : `Recepción registrada. De ${actualizada.codigo} todavía falta llegar una parte.`,
          );
          this.cargarCompras();
          this.cargarBandeja();
        },
        error: (e) => this.errorRecepcion.set(mensajeDeError(e, 'No se pudo registrar la recepción.')),
      });
  }

  /**
   * Producción abre Compras con el insumo que falta y cuánto (?materiaPrimaId=&cantidad=): el
   * formulario se abre con esa línea puesta; el costo y el proveedor los completa la persona.
   */
  private prellenarDesdeProduccion(): void {
    const params = this.ruta.snapshot.queryParamMap;
    const materiaPrimaId = Number(params.get('materiaPrimaId'));
    if (!materiaPrimaId || !this.puedeEscribir()) return;
    const cantidad = Number(params.get('cantidad'));
    this.items.set([{ materiaPrimaId, cantidad: cantidad > 0 ? cantidad : null, costoUnitario: null }]);
    this.ultimoPrecio(this.items()[0]);
    this.formularioAbierto.set(true);
  }

  /** Anula una compra registrada por error: lo que entró por ella sale del almacén. */
  async anular(c: Compra): Promise<void> {
    const motivo = await this.dialogo.pedirTexto({
      titulo: '¿Anular esta compra?',
      mensaje:
        `${c.proveedorCompraNombre} · S/ ${c.total.toFixed(2)}. Lo que entró por ella sale del almacén. ` +
        'Si parte ya se usó en producción no se podrá anular: corrígelo con un ajuste en Inventario.',
      etiqueta: 'Motivo de la anulación',
      obligatorio: true,
      confirmar: 'Anular compra',
      cancelar: 'Volver',
      tipo: 'peligro',
    });
    if (!motivo) return;
    this.compraService.anular(c.id, motivo).subscribe({
      next: () => {
        this.notificaciones.exito('Compra anulada. El inventario ya se actualizó.');
        this.cargarCompras();
      },
      error: (e) => this.notificaciones.error(mensajeDeError(e, 'No se pudo anular la compra.')),
    });
  }

  puedeEscribir(): boolean {
    return this.auth.puede(PERMISO.comprasWrite);
  }

  cargarProveedores(): void {
    this.compraService.proveedores().subscribe({ next: (p) => this.proveedores.set(p) });
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargarCompras();
  }

  cambiarTamano(tamano: number): void {
    this.size = tamano;
    this.cambiarPagina(0);
  }

  cargarCompras(): void {
    this.cargando.set(true);
    this.compraService
      .listar({ page: this.page, size: this.size })
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (p) => {
          const otra = paginaDisponible(p);
          if (otra !== null) return this.cambiarPagina(otra);
          this.compras.set(p);
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar las compras.'),
      });
  }

  crearProveedor(): void {
    if (!this.nombreProveedor.trim()) return;
    this.compraService
      .crearProveedor({
        nombre: this.nombreProveedor.trim(),
        contacto: this.contactoProveedor.trim() || undefined,
      })
      .subscribe({
        next: (p) => {
          this.nombreProveedor = '';
          this.contactoProveedor = '';
          this.nuevoProveedorAbierto.set(false);
          // El proveedor recién creado queda elegido: es para esta compra que se creó.
          this.proveedorCompraId = p.id;
          this.cargarProveedores();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo crear el proveedor.'),
      });
  }

  abrirFormulario(): void {
    this.error.set('');
    this.formularioAbierto.set(true);
  }

  cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.intentoGuardar.set(false);
    this.nuevoProveedorAbierto.set(false);
    this.error.set('');
  }

  /** "Leche fresca, Sal y 2 más": lo comprado en una línea para la tabla. */
  detalle(c: Compra): string {
    const nombres = (c.items ?? []).map((i) => i.materiaPrimaNombre);
    if (nombres.length <= 2) return nombres.join(', ');
    return `${nombres.slice(0, 2).join(', ')} y ${nombres.length - 2} más`;
  }

  agregarItem(): void {
    this.items.update((f) => [...f, filaCompraVacia()]);
  }

  /**
   * Lo que impide guardar esta fila. Se muestra si ya se intentó guardar o si la fila tiene un
   * insumo elegido (un repetido se avisa apenas se elige, no recién al guardar).
   */
  problema(index: number): string | null {
    const filas = this.items();
    const p = problemaDeFila(filas, index, this.materiasPrimas());
    if (!p) return null;
    if (this.intentoGuardar()) return p;
    return filas.findIndex((o, i) => i < index && o.materiaPrimaId != null && o.materiaPrimaId === filas[index].materiaPrimaId) >= 0
      ? p
      : null;
  }

  avisoPrecio(item: FilaCompraItem): string | null {
    const materia = this.materiasPrimas().find((m) => m.id === item.materiaPrimaId);
    return avisoDePrecio(item, materia, this.unidadDe(item.materiaPrimaId));
  }

  /** Al elegir el insumo, si el precio está vacío se propone el último que se pagó. */
  alElegirInsumo(item: FilaCompraItem, id: number | null): void {
    item.materiaPrimaId = id;
    this.ultimoPrecio(item);
    this.items.update((f) => [...f]);
  }

  private ultimoPrecio(item: FilaCompraItem): void {
    if (item.costoUnitario != null) return;
    const ref = this.materiasPrimas().find((m) => m.id === item.materiaPrimaId)?.costoUnitarioReferencial;
    if (ref != null && ref > 0) item.costoUnitario = ref;
  }

  /** Unidad de la materia prima elegida, junto a la cantidad comprada. */
  unidadDe(materiaPrimaId: number | null): string {
    return etiquetaUnidad(this.materiasPrimas().find((m) => m.id === materiaPrimaId)?.unidadMedida);
  }

  subtotal(item: FilaCompraItem): number {
    return (item.cantidad ?? 0) * (item.costoUnitario ?? 0);
  }

  totalCompra(): number {
    return this.items().reduce((s, i) => s + this.subtotal(i), 0);
  }

  quitarItem(index: number): void {
    this.items.update((f) => (f.length === 1 ? [filaCompraVacia()] : f.filter((_, i) => i !== index)));
  }

  registrarCompra(): void {
    if (this.guardando()) return;
    this.intentoGuardar.set(true);
    const filas = this.items();
    const conProblema = filas.map((_, i) => problemaDeFila(filas, i, this.materiasPrimas())).filter((p) => p != null);
    const faltas: string[] = [];
    if (!this.proveedorCompraId) faltas.push('elige el proveedor');
    if (!this.fecha) faltas.push('pon la fecha');
    else if (this.fecha > hoy()) faltas.push('la fecha no puede ser futura');
    if (conProblema.length) faltas.push(conProblema.length === 1 ? 'corrige la fila marcada' : `corrige las ${conProblema.length} filas marcadas`);
    if (faltas.length) {
      this.error.set('Para registrar: ' + faltas.join(', ') + '.');
      return;
    }
    // Validado arriba: las filas que quedan tienen insumo, cantidad y precio.
    const items = filas
      .filter((f) => !filaEnBlanco(f))
      .map((f) => ({ materiaPrimaId: f.materiaPrimaId!, cantidad: f.cantidad!, costoUnitario: f.costoUnitario! }));
    this.guardando.set(true);
    this.error.set('');
    this.compraService
      .registrar({
        proveedorCompraId: this.proveedorCompraId!,
        fecha: this.fecha,
        documento: this.documento.trim() || undefined,
        items,
        recibirAhora: this.recibirAhora,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (c) => {
          this.formularioAbierto.set(false);
          this.notificaciones.exito(
            c.estado === 'RECIBIDA'
              ? `${c.codigo} registrada y recibida por S/ ${c.total.toFixed(2)}. El stock ya se actualizó.`
              : `${c.codigo} registrada por S/ ${c.total.toFixed(2)}. Queda por recibir: el stock sube al registrar la recepción.`,
          );
          this.recibirAhora = false;
          this.cargarBandeja();
          this.items.set([filaCompraVacia()]);
          this.intentoGuardar.set(false);
          this.documento = '';
          this.fecha = hoy();
          this.cargarCompras();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo registrar la compra.'),
      });
  }
}

function hoy(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
