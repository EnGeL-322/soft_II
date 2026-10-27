import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ClienteVentas, VentaQueso } from '../../core/models/models';
import { QueseriaService } from '../../core/services/queseria.service';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { recargarEnVivo } from '../../core/services/cambios-tiempo-real.service';

/**
 * A quién se le vende: cada cliente con lo que compró, cuánto debe y su historial de compras de
 * cualquier producto. El cliente se escribe libre en la venta; el servidor agrupa "Bodega Rosa" y
 * "bodega rosa" como el mismo.
 */
@Component({
  selector: 'app-historial-clientes',
  standalone: true,
  imports: [PaginadorComponent, FormsModule, DecimalPipe],
  template: `
    <section class="card" style="margin-top: 20px">
      <div class="table-title">
        <div>
          <div class="kicker">Clientes</div>
          <h3>A quién se le vende</h3>
          <p>Cada cliente con lo que compró. Abre uno para ver su historial completo.</p>
        </div>
      </div>
      <div class="form-grid" style="margin-bottom: 12px">
        <label class="field"
          ><span>Buscar cliente</span
          ><input [(ngModel)]="buscar" name="buscarCliente" placeholder="Nombre del cliente"
        /></label>
        <label class="field"
          ><span>Desde</span><input type="date" [(ngModel)]="desde" name="desdeCli" (change)="cargar()"
        /></label>
        <label class="field"
          ><span>Hasta</span><input type="date" [(ngModel)]="hasta" name="hastaCli" (change)="cargar()"
        /></label>
      </div>
      @if (error()) {
        <p class="note" role="alert">{{ error() }}</p>
      }
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Cliente</th>
              <th class="num">Compras</th>
              <th class="num">Unidades</th>
              <th class="num">Total S/</th>
              <th class="num">Por cobrar S/</th>
              <th>Última compra</th>
              <th>Productos</th>
              <th class="no-print"></th>
            </tr>
          </thead>
          <tbody>
            @for (c of pag.ver(filtrados(), buscar + '|' + desde + '|' + hasta); track c.cliente) {
              <tr>
                <td><b>{{ c.cliente }}</b></td>
                <td class="num">{{ c.compras }}</td>
                <td class="num">{{ c.unidades }}</td>
                <td class="num">{{ c.total | number: '1.2-2' }}</td>
                <td class="num">
                  @if (c.porCobrar > 0) {
                    <span class="badge badge-ambar">{{ c.porCobrar | number: '1.2-2' }}</span>
                  } @else {
                    —
                  }
                </td>
                <td>{{ c.ultimaCompra }}</td>
                <td class="muted">{{ c.productos.join(', ') }}</td>
                <td class="no-print">
                  <button type="button" class="btn btn-soft" (click)="alternar(c.cliente)">
                    {{ abierto() === c.cliente ? 'Cerrar' : 'Ver historial' }}
                  </button>
                </td>
              </tr>
              @if (abierto() === c.cliente) {
                <tr>
                  <td colspan="8" style="background: #fbfdfb">
                    @if (productosDelHistorial().length > 1) {
                      <label class="field" style="max-width: 280px; margin-bottom: 8px"
                        ><span>Producto</span
                        ><select [(ngModel)]="filtroProducto" name="filtroProducto">
                          <option value="">Todos</option>
                          @for (p of productosDelHistorial(); track p) {
                            <option [value]="p">{{ p }}</option>
                          }
                        </select></label
                      >
                    }
                    <table>
                      <thead>
                        <tr>
                          <th>Fecha</th>
                          <th>Producto</th>
                          <th>Lote</th>
                          <th class="num">Unidades</th>
                          <th class="num">Precio</th>
                          <th class="num">Total</th>
                          <th>Pago</th>
                        </tr>
                      </thead>
                      <tbody>
                        @for (v of historialFiltrado(); track v.id) {
                          <tr [style.opacity]="v.anulada ? 0.55 : 1">
                            <td>{{ v.fecha }}</td>
                            <td>{{ v.productoNombre }}</td>
                            <td class="muted">{{ v.loteCodigo }}</td>
                            <td class="num">{{ v.unidades }}</td>
                            <td class="num">{{ v.precioUnitario | number: '1.2-2' }}</td>
                            <td class="num">{{ v.total | number: '1.2-2' }}</td>
                            <td>
                              @if (v.anulada) {
                                <span class="badge badge-gris" [title]="v.motivoAnulacion ?? ''">Anulada</span>
                              } @else if (v.estado === 'POR_COBRAR') {
                                <span class="badge badge-ambar">Por cobrar</span>
                              } @else {
                                <span class="badge badge-verde">Pagado</span>
                              }
                            </td>
                          </tr>
                        } @empty {
                          <tr><td colspan="7" class="muted">Cargando…</td></tr>
                        }
                      </tbody>
                    </table>
                  </td>
                </tr>
              }
            } @empty {
              <tr><td colspan="8" class="muted">No hay ventas en estas fechas.</td></tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginador
        [page]="pag.pagina(filtrados().length)"
        [size]="pag.tamano()"
        [total]="filtrados().length"
        (pageChange)="pag.irA($event)"
        (sizeChange)="pag.cambiarTamano($event)"
      />
    </section>
  `,
})
export class HistorialClientesComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de ventas, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['ventas'], () => this.cargar());
  readonly clientes = signal<ClienteVentas[]>([]);
  readonly pag = new PaginacionLocal();
  readonly historial = signal<VentaQueso[]>([]);
  readonly abierto = signal<string | null>(null);
  readonly error = signal('');
  buscar = '';
  desde = '';
  hasta = '';
  filtroProducto = '';

  constructor(private readonly queseria: QueseriaService) {}

  ngOnInit(): void {
    this.cargar();
  }

  /** Búsqueda sin distinguir mayúsculas ni tildes. */
  filtrados(): ClienteVentas[] {
    const q = normalizar(this.buscar);
    return q ? this.clientes().filter((c) => normalizar(c.cliente).includes(q)) : this.clientes();
  }

  readonly productosDelHistorial = computed(() =>
    [...new Set(this.historial().map((v) => v.productoNombre))].sort((a, b) => a.localeCompare(b, 'es')),
  );

  historialFiltrado(): VentaQueso[] {
    return this.filtroProducto
      ? this.historial().filter((v) => v.productoNombre === this.filtroProducto)
      : this.historial();
  }

  cargar(): void {
    this.error.set('');
    this.queseria.clientes(this.desde || undefined, this.hasta || undefined).subscribe({
      next: (c) => this.clientes.set(c),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo cargar la lista de clientes.'),
    });
  }

  alternar(cliente: string): void {
    if (this.abierto() === cliente) {
      this.abierto.set(null);
      return;
    }
    this.abierto.set(cliente);
    this.filtroProducto = '';
    this.historial.set([]);
    this.queseria.historialCliente(cliente).subscribe({
      next: (v) => this.historial.set(v),
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo cargar el historial.'),
    });
  }
}

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim()
    .toLowerCase();
}
