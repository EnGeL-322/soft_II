import { Component, OnInit, computed, input, output, signal, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { EstadoPeriodo, estadoPeriodo } from '../../core/config/precios';
import { Producto, TarifaQueso } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { ProduccionService } from '../../core/services/produccion.service';
import { QueseriaService } from '../../core/services/queseria.service';
import { VerificacionService } from '../../core/services/verificacion.service';
import { BuscadorComponent, OpcionBuscador } from '../../shared/ui/buscador.component';
import { PaginacionLocal } from '../../shared/ui/paginacion-local';
import { PaginadorComponent } from '../../shared/ui/paginador.component';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';

/**
 * Lista de precios por producto y período. Un período con fecha de fin es una promoción: rige solo
 * entre sus fechas y después vuelve el precio anterior, sin que nadie tenga que reponerlo.
 */
@Component({
  selector: 'app-tarifas-queso',
  standalone: true,
  imports: [PaginadorComponent, DecimalPipe, FormsModule, BuscadorComponent],
  template: `
    <section class="card" style="margin-top:20px">
      <div class="table-title">
        <div>
          <div class="kicker">Comercialización</div>
          <h3>Lista de precios por producto y período</h3>
          <p>
            Precio por unidad según producto, comprador y fechas. Un período con fecha de fin es una
            promoción: al terminar vuelve a regir el precio normal.
          </p>
        </div>
        <div class="actions" style="min-width:240px">
          <app-buscador
            [opciones]="opcFiltro()"
            [(ngModel)]="filtroProductoId"
            name="filtro-producto"
            etiqueta="Filtrar por producto"
            placeholder="Todos los productos"
          />
        </div>
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Producto</th>
              <th>Período</th>
              <th>Desde</th>
              <th>Hasta</th>
              <th class="num">Público S/</th>
              <th class="num">Mayorista S/</th>
              <th class="num">Proveedor S/</th>
              <th>Hoy</th>
            </tr>
          </thead>
          <tbody>
            @for (t of pag.ver(visibles(), '' + filtroProductoId); track t.id) {
              <tr>
                <td>{{ t.productoNombre }}</td>
                <td>
                  @if (t.promocion) {
                    <span class="badge badge-ambar">Promoción</span>
                  } @else {
                    <span class="muted">Precio normal</span>
                  }
                  @if (t.descripcion) {
                    <div class="muted" style="font-size:12px">{{ t.descripcion }}</div>
                  }
                </td>
                <td>{{ t.vigenteDesde }}</td>
                <td>{{ t.vigenteHasta ?? 'Sin fecha final' }}</td>
                <td class="num">{{ t.precioPublicoGeneral | number: '1.2-2' }}</td>
                <td class="num">{{ t.precioMayorista | number: '1.2-2' }}</td>
                <td class="num">{{ t.precioProveedor | number: '1.2-2' }}</td>
                <td>
                  <span class="badge" [class]="claseEstado(estado(t))">{{
                    etiquetaEstado(estado(t))
                  }}</span>
                </td>
              </tr>
            } @empty {
              <tr>
                <td colspan="8">No hay precios configurados.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-paginador
        [page]="pag.pagina(visibles().length)"
        [size]="pag.tamano()"
        [total]="visibles().length"
        (pageChange)="pag.irA($event)"
        (sizeChange)="pag.cambiarTamano($event)"
      />
      @if (auth.puede('parametros.write')) {
        <details style="margin-top:20px">
          <summary style="cursor:pointer">Configurar un período de precios o una promoción</summary>
          <form (ngSubmit)="guardar()" style="margin-top:16px">
            <div class="form-grid">
              <div class="field">
                <label>Producto</label>
                <app-buscador
                  [opciones]="opcProductos()"
                  [(ngModel)]="productoId"
                  name="producto"
                  etiqueta="Producto"
                  placeholder="Buscar producto…"
                />
              </div>
              <label class="field"
                ><span>Vigente desde</span
                ><input type="date" [(ngModel)]="desde" name="desde" required
              /></label>
              <label class="field"
                ><span>Vigente hasta (solo para promociones)</span
                ><input type="date" [(ngModel)]="hasta" name="hasta" [min]="desde"
              /></label>
              <label class="field"
                ><span>Nombre del período (opcional)</span
                ><input
                  [(ngModel)]="descripcion"
                  name="descripcion"
                  maxlength="120"
                  placeholder="Ej. Promoción Fiestas Patrias"
              /></label>
              <label class="field"
                ><span>Público general S/</span
                ><input
                  type="number"
                  min="0.01"
                  step="0.01"
                  [(ngModel)]="publico"
                  name="publico"
                  required
              /></label>
              <label class="field"
                ><span>Mayorista S/</span
                ><input
                  type="number"
                  min="0.01"
                  step="0.01"
                  [(ngModel)]="mayorista"
                  name="mayorista"
                  required
              /></label>
              <label class="field"
                ><span>Proveedor S/</span
                ><input
                  type="number"
                  min="0.01"
                  step="0.01"
                  [(ngModel)]="proveedor"
                  name="proveedor"
                  required
              /></label>
            </div>
            <p class="muted">
              @if (hasta) {
                Promoción: rige del {{ desde || '…' }} al {{ hasta }} y luego vuelve el precio
                normal.
              } @else {
                Precio normal: rige desde {{ desde || '…' }} hasta que se configure otro.
              }
              Confirma el cambio con el código enviado al correo de tu cuenta.
            </p>
            <div class="actions">
              <button
                class="btn btn-soft"
                type="button"
                [disabled]="ocupado()"
                (click)="solicitar()"
              >
                Solicitar código</button
              ><label class="field"
                ><span>Código de verificación</span
                ><input
                  aria-label="Código de verificación"
                  name="codigo"
                  [(ngModel)]="codigo"
                  inputmode="numeric"
                  pattern="[0-9]{6}"
                  maxlength="6"
                  required /></label
              ><button class="btn btn-primary" [disabled]="ocupado()">Guardar precios</button>
            </div>
          </form>
        </details>
      }
      @if (mensaje()) {
        <p class="note" role="status">{{ mensaje() }}</p>
      }
    </section>
  `,
})
export class TarifasQuesoComponent implements OnInit {
  protected readonly notificaciones = inject(NotificacionesService);
  readonly tarifas = input.required<TarifaQueso[]>();
  readonly actualizadas = output<void>();
  readonly ocupado = signal(false);
  readonly mensaje = signal('');
  readonly productos = signal<Producto[]>([]);
  readonly hoy = hoyLocal();
  productoId: number | null = null;
  filtroProductoId: number | null = null;
  readonly pag = new PaginacionLocal();
  desde = '';
  hasta = '';
  descripcion = '';
  codigo = '';
  publico: number | null = null;
  mayorista: number | null = null;
  proveedor: number | null = null;

  readonly opcProductos = computed<OpcionBuscador[]>(() =>
    this.productos().map((p) => ({ valor: p.id, etiqueta: p.nombre, detalle: p.unidadVenta })),
  );
  readonly opcFiltro = computed<OpcionBuscador[]>(() => this.opcProductos());

  constructor(
    protected readonly auth: AuthService,
    private readonly queseria: QueseriaService,
    private readonly produccion: ProduccionService,
    private readonly verificacion: VerificacionService,
  ) {}

  ngOnInit(): void {
    this.produccion.productos().subscribe({
      next: (p) => this.productos.set(p),
      error: (e) => this.notificaciones.error(mensajeDeError(e, 'No se pudo cargar el catálogo de productos.')),
    });
  }

  visibles(): TarifaQueso[] {
    return this.filtroProductoId == null
      ? this.tarifas()
      : this.tarifas().filter((t) => t.productoId === this.filtroProductoId);
  }

  estado(t: TarifaQueso): EstadoPeriodo {
    return estadoPeriodo(t, this.tarifas(), this.hoy);
  }

  etiquetaEstado(e: EstadoPeriodo): string {
    return {
      RIGE: 'Rige hoy',
      PROGRAMADO: 'Programado',
      TERMINADO: 'Terminado',
      REEMPLAZADO: 'En pausa',
    }[e];
  }

  claseEstado(e: EstadoPeriodo): string {
    return {
      RIGE: 'badge badge-verde',
      PROGRAMADO: 'badge badge-ambar',
      TERMINADO: 'badge badge-gris',
      REEMPLAZADO: 'badge badge-gris',
    }[e];
  }

  solicitar() {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.verificacion
      .solicitarCodigo()
      .pipe(finalize(() => this.ocupado.set(false)))
      .subscribe({
        next: (r) =>
          this.notificaciones.info(
            `Código enviado a ${r.destinatarioEnmascarado}. Vence en ${r.minutosVigencia} minutos.`,
          ),
        error: (e) => this.mensaje.set(e.error?.message ?? 'No se pudo enviar el código.'),
      });
  }

  guardar() {
    if (
      this.ocupado() ||
      this.productoId == null ||
      !this.desde ||
      (this.hasta && this.hasta < this.desde) ||
      !this.publico ||
      this.publico <= 0 ||
      !this.mayorista ||
      this.mayorista <= 0 ||
      !this.proveedor ||
      this.proveedor <= 0 ||
      !/^\d{6}$/.test(this.codigo)
    ) {
      this.mensaje.set(
        'Revisa el producto, las fechas (el fin no puede ser antes del inicio), precios positivos y el código de seis dígitos.',
      );
      return;
    }
    this.ocupado.set(true);
    this.queseria
      .guardarTarifa({
        productoId: this.productoId,
        vigenteDesde: this.desde,
        vigenteHasta: this.hasta || undefined,
        descripcion: this.descripcion.trim() || undefined,
        precioPublicoGeneral: this.publico,
        precioMayorista: this.mayorista,
        precioProveedor: this.proveedor,
        codigo: this.codigo,
      })
      .pipe(finalize(() => this.ocupado.set(false)))
      .subscribe({
        next: (t) => {
          this.codigo = '';
          this.mensaje.set('');
          this.notificaciones.exito(
            t.promocion
              ? `Promoción de ${t.productoNombre} guardada: rige del ${t.vigenteDesde} al ${t.vigenteHasta}.`
              : `Precio de ${t.productoNombre} guardado: rige desde ${t.vigenteDesde}.`,
          );
          this.actualizadas.emit();
        },
        error: (e) => this.mensaje.set(e.error?.message ?? 'No se pudieron guardar los precios.'),
      });
  }
}

/** Fecha local (no UTC), igual que el resto de pantallas de venta. */
function hoyLocal(): string {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
