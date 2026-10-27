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
import { ActivatedRoute } from '@angular/router';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

interface FilaCompraItem {
  materiaPrimaId: number | null;
  cantidad: number | null;
  costoUnitario: number | null;
}

/** Compras de materia prima/insumos (4.3): cada compra entra automáticamente al inventario. */
@Component({
  selector: 'app-compras',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe, BuscadorComponent],
  templateUrl: './compras.component.html',
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
  readonly items = signal<FilaCompraItem[]>([
    { materiaPrimaId: null, cantidad: null, costoUnitario: null },
  ]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly formularioAbierto = signal(false);
  readonly error = signal('');

  proveedorCompraId: number | null = null;
  fecha = hoy();
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
    this.inventario.materiasPrimas().subscribe({ next: (m) => this.materiasPrimas.set(m) });
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
        next: () => {
          this.nombreProveedor = '';
          this.contactoProveedor = '';
          this.cargarProveedores();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo crear el proveedor.'),
      });
  }

  agregarItem(): void {
    this.items.update((f) => [...f, { materiaPrimaId: null, cantidad: null, costoUnitario: null }]);
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
    this.items.update((f) => f.filter((_, i) => i !== index));
  }

  registrarCompra(): void {
    const items = this.items()
      .filter(
        (i): i is { materiaPrimaId: number; cantidad: number; costoUnitario: number } =>
          i.materiaPrimaId != null && i.cantidad != null && i.costoUnitario != null,
      )
      .map((i) => ({
        materiaPrimaId: i.materiaPrimaId,
        cantidad: i.cantidad,
        costoUnitario: i.costoUnitario,
      }));
    if (!this.proveedorCompraId || items.length === 0) {
      this.error.set('Selecciona un proveedor y agrega al menos un ítem.');
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    this.compraService
      .registrar({
        proveedorCompraId: this.proveedorCompraId,
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
          this.items.set([{ materiaPrimaId: null, cantidad: null, costoUnitario: null }]);
          this.documento = '';
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
