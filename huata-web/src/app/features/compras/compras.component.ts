import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { Component, OnInit, computed, signal, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { etiquetaUnidad } from '../../core/config/unidades';
import { Compra, MateriaPrima, PageResponse, ProveedorCompra } from '../../core/models/models';
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
  avisoDePrecio,
  filaCompraVacia,
  filaEnBlanco,
  porQueNoAparece,
  problemaDeFila,
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
  private readonly _enVivo = recargarEnVivo(['compras', 'inventario'], () => {
    this.cargarProveedores();
    this.cargarCompras();
  });
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
    this.prellenarDesdeProduccion();
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
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (c) => {
          this.formularioAbierto.set(false);
          this.notificaciones.exito(
            `Compra registrada por S/ ${c.total.toFixed(2)}. El inventario ya se actualizó.`,
          );
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
