import { DecimalPipe } from '@angular/common';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { Component, DestroyRef, OnInit, computed, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { PERMISO } from '../../core/config/navegacion';
import { tarifaQueRige } from '../../core/config/precios';
import {
  CondicionVenta,
  LoteQueso,
  MedioCobro,
  ResumenVentasQueso,
  TarifaQueso,
  TipoComprador,
  VentaQueso,
} from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { QueseriaService } from '../../core/services/queseria.service';
import { TarifasQuesoComponent } from './tarifas-queso.component';
import { HistorialClientesComponent } from './historial-clientes.component';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';
import { productosParaVender, repartirFefo } from './reparto-fefo';

/**
 * "Registrar venta" de Secretaría (vistas-por-roles.html, NAVS.secretaria).
 *
 * <p><b>El precio no se escribe aquí.</b> Lo pone la tarifa vigente según el tipo de comprador
 * (público, mayorista o el propio proveedor de leche) y se edita en Configuración. La pantalla lo
 * muestra para que la persona vea lo que va a cobrar, pero quien lo decide es el backend: si el
 * formulario pudiera enviarlo, cada nota de venta llevaría el suyo.
 *
 * <p><b>El stock también viene del backend</b>, y por la misma razón: es el número que decide si la
 * venta se acepta.
 */
/** Vistas que abre el menú lateral (`?vista=`), en el mismo orden que sus subitems. */
type VistaVentas = 'cobros' | 'registrar' | 'clientes' | 'precios';

@Component({
  selector: 'app-venta-queso',
  standalone: true,
  imports: [PaginadorComponent, 
    FormsModule,
    DecimalPipe,
    TarifasQuesoComponent,
    BuscadorComponent,
    HistorialClientesComponent,
    RouterLink,
  ],
  templateUrl: './venta-queso.component.html',
})
export class VentaQuesoComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de ventas, produccion, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['ventas', 'produccion'], () => this.cargar());
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly dialogo = inject(DialogoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  /** Solo para mostrar y para resolver la tarifa/margen en pantalla: el servidor no la recibe, la
   *  pone él mismo al guardar (ver VentaQuesoRequest). El campo en el HTML está deshabilitado, así
   *  que en la práctica solo cambia si el propio componente lo hace (o una prueba). */
  fecha = hoy();
  /** Se elige el producto, no el lote: sale primero lo que vence antes (pedido del cliente, 2026-10-27). */
  productoId: number | null = null;
  cliente = '';
  unidades: number | null = null;
  tipoComprador: TipoComprador = 'PUBLICO_GENERAL';
  condicion: CondicionVenta = 'CONTADO';
  medioCobro: MedioCobro = 'EFECTIVO';

  readonly lotes = signal<LoteQueso[]>([]);
  readonly ventas = signal<VentaQueso[]>([]);
  readonly pag = new PaginacionLocal();

  readonly resumen = signal<ResumenVentasQueso | null>(null);
  readonly guardando = signal(false);
  readonly vista = signal<VistaVentas>('cobros');
  readonly error = signal('');

  readonly tarifas = signal<TarifaQueso[]>([]);
  readonly todosLotes = signal<LoteQueso[]>([]);
  readonly productosEnCamara = computed(() => productosParaVender(this.lotes()));

  producto() {
    return this.productosEnCamara().find((p) => p.productoId === this.productoId) ?? null;
  }

  /** De qué lotes saldrá la venta: primero lo que vence antes. */
  reparto() {
    return repartirFefo(this.lotes(), this.productoId, this.unidades);
  }

  /** Periodo de precios que rige para el producto elegido en la fecha de la venta. */
  tarifa() {
    return tarifaQueRige(this.tarifas(), this.productoId ?? undefined, this.fecha);
  }

  /** Precio para el comprador elegido. Es visualización: el backend lo vuelve a resolver. */
  precio() {
    const t = this.tarifa();
    if (!t) {
      return 0;
    }
    return {
      PUBLICO_GENERAL: t.precioPublicoGeneral,
      MAYORISTA: t.precioMayorista,
      PROVEEDOR: t.precioProveedor,
    }[this.tipoComprador];
  }

  total() {
    return (this.unidades ?? 0) * this.precio();
  }
  /** Precio menos el costo promedio de los lotes de los que saldrá la venta. */
  margenUnidad() {
    const tomas = this.reparto();
    const conCosto = tomas.filter((t) => t.costoUnitario != null);
    if (!conCosto.length || conCosto.length !== tomas.length || this.precio() <= 0) return null;
    const unidades = tomas.reduce((s, t) => s + t.unidades, 0);
    const costo = tomas.reduce((s, t) => s + t.unidades * (t.costoUnitario ?? 0), 0) / unidades;
    return Math.round((this.precio() - costo) * 100) / 100;
  }
  margenVenta(v: VentaQueso) {
    const costo = this.todosLotes().find((l) => l.id === v.loteId)?.costoUnitario;
    return costo != null ? v.total - costo * v.unidades : null;
  }

  constructor(
    private readonly queseria: QueseriaService,
    private readonly auth: AuthService,
  ) {}

  readonly opcProductos = computed<OpcionBuscador[]>(() =>
    this.productosEnCamara().map((p) => ({
      valor: p.productoId,
      etiqueta: `${p.productoNombre} · ${p.stock} en cámara`,
      detalle: p.proximoVencimiento ? `el próximo vence ${p.proximoVencimiento}` : undefined,
    })),
  );

  ngOnInit(): void {
    this.cargar();
    this.ruta.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const vista = params.get('vista');
      const permitida =
        vista === 'clientes' || vista === 'precios' || (vista === 'registrar' && this.puedeVender());
      this.vista.set(permitida ? (vista as VistaVentas) : 'cobros');
    });
  }

  puedeVender(): boolean {
    return this.auth.puede(PERMISO.ventasRegistrar);
  }

  puedeCobrar(): boolean {
    return this.auth.puede(PERMISO.ventasCobrar);
  }

  puedeAnular(): boolean {
    return this.auth.puede(PERMISO.ventasAnular);
  }

  cargar(): void {
    this.queseria.tarifas().subscribe({
      next: (t) => this.tarifas.set(t),
      error: () => this.error.set('No se pudo consultar la lista de precios por período.'),
    });
    this.queseria.lotes().subscribe({
      next: (l) => this.todosLotes.set(l),
      error: () => this.notificaciones.error('No se pudieron consultar los costos de los lotes.'),
    });
    this.queseria.lotesConStock().subscribe({
      next: (l) => {
        this.lotes.set(l);
        const productos = this.productosEnCamara();
        if (this.productoId == null || !productos.some((p) => p.productoId === this.productoId)) {
          this.productoId = productos.length === 1 ? productos[0].productoId : null;
        }
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudieron cargar los lotes.'),
    });
    this.queseria.ventas().subscribe({ next: (v) => this.ventas.set(v) });
    this.queseria.resumenVentas().subscribe({ next: (r) => this.resumen.set(r) });
  }

  registrarVenta(): void {
    const producto = this.producto();
    if (
      this.guardando() ||
      !producto ||
      !this.unidades ||
      !Number.isInteger(this.unidades) ||
      this.unidades < 1 ||
      this.unidades > producto.stock ||
      this.precio() <= 0 ||
      !this.cliente.trim()
    ) {
      this.error.set(
        'Revisa el cliente, el stock disponible, las unidades enteras y que el producto tenga precio para esta fecha.',
      );
      return;
    }
    this.guardando.set(true);
    this.error.set('');
    this.queseria
      .venderPorProducto({
        productoId: producto.productoId,
        cliente: this.cliente.trim(),
        unidades: this.unidades,
        tipoComprador: this.tipoComprador,
        condicion: this.condicion,
        medioCobro: this.medioCobro,
      })
      .pipe(finalize(() => this.guardando.set(false)))
      .subscribe({
        next: (ventas) => {
          // La venta nueva se ve en la lista, con su estado de cobro.
          this.router.navigate([], { relativeTo: this.ruta, queryParams: { vista: 'cobros' } });
          const total = ventas.reduce((s, v) => s + v.total, 0);
          const lotes = ventas.map((v) => v.loteCodigo).join(', ');
          this.notificaciones.exito(
            `Venta registrada: ${this.unidades} u de ${producto.productoNombre} (de ${lotes}) por S/ ${total.toFixed(2)} (${
              ventas[0]?.estado === 'COBRADO' ? 'cobrada' : 'por cobrar'
            }).`,
          );
          this.unidades = null;
          this.cargar();
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo registrar la venta.'),
      });
  }

  marcarCobrada(v: VentaQueso): void {
    this.queseria.marcarCobrada(v.id).subscribe({
      next: () => this.cargar(),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo marcar como cobrada.'),
    });
  }

  async anular(v: VentaQueso): Promise<void> {
    const motivo = await this.dialogo.pedirTexto({
      titulo: '¿Anular la venta?',
      mensaje: `${v.unidades} u a ${v.cliente}. Las unidades vuelven al stock del lote.`,
      etiqueta: 'Motivo',
      obligatorio: true,
      confirmar: 'Anular venta',
      tipo: 'peligro',
    });
    if (!motivo?.trim()) {
      return;
    }
    this.queseria.anularVenta(v.id, motivo.trim()).subscribe({
      next: () => {
        this.notificaciones.exito('Venta anulada: las unidades vuelven al stock del lote.');
        this.cargar();
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo anular la venta.'),
    });
  }

  etiquetaComprador(tipo: TipoComprador): string {
    return {
      PUBLICO_GENERAL: 'Público en general',
      MAYORISTA: 'Mayorista',
      PROVEEDOR: 'Proveedor de leche',
    }[tipo];
  }

  etiquetaCondicion(c: CondicionVenta): string {
    return c === 'CONTADO' ? 'Contado' : 'Crédito 7 días';
  }
}

/** ISO local, no UTC: con toISOString() la fecha se corre un día según la zona horaria. */
function hoy(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
