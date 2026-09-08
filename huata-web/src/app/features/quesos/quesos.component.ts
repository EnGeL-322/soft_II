import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Queso } from '../../core/models/models';
import { QuesoRequest, QuesosService } from '../../core/services/quesos.service';

const VACIO: QuesoRequest = { fecha: '', fuente: 'Leche propia', producidosKg: 0, vendidosKg: 0 };

/**
 * Producción · Quesos: modulo historico del prototipo (fuera del alcance oficial del Charter,
 * implementado a pedido explicito). "Total venta" queda vacio cuando no hay evidencia, igual que
 * en el prototipo ("No visible en evidencia"): no se inventa un monto.
 */
@Component({
  selector: 'app-quesos',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Producción</div>
        <h1>Quesos</h1>
        <p>Módulo histórico de Huata, fuera del alcance oficial del Charter, implementado a pedido explícito.</p>
      </div>
    </div>

    <div class="note" style="margin-bottom:16px">
      "Total venta" queda vacío cuando no hay evidencia del monto: no se inventa un valor.
    </div>

    <form class="card" style="margin-bottom:16px" (ngSubmit)="guardar()">
      <div class="form-grid">
        <div class="field">
          <label>Fecha</label>
          <input type="date" [(ngModel)]="form.fecha" name="fecha" required />
        </div>
        <div class="field">
          <label>Fuente</label>
          <input type="text" [(ngModel)]="form.fuente" name="fuente" required />
        </div>
        <div class="field">
          <label>Producidos (kg)</label>
          <input type="number" step="0.1" [(ngModel)]="form.producidosKg" name="producidosKg" required />
        </div>
        <div class="field">
          <label>Vendidos (kg)</label>
          <input type="number" step="0.1" [(ngModel)]="form.vendidosKg" name="vendidosKg" />
        </div>
        <div class="field">
          <label>Total venta (S/, opcional)</label>
          <input type="number" step="0.01" [(ngModel)]="form.totalVenta" name="totalVenta" />
        </div>
      </div>
      <div class="field" style="margin-top:12px">
        <label>Observaciones</label>
        <input type="text" [(ngModel)]="form.observaciones" name="observaciones" />
      </div>
      <div class="actions" style="margin-top:12px">
        <button type="submit" class="btn btn-primary">+ Registrar producción</button>
      </div>
    </form>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Fuente</th>
            <th class="num">Producidos (kg)</th>
            <th class="num">Vendidos (kg)</th>
            <th class="num">Diferencia (kg)</th>
            <th class="num">Total venta</th>
            <th>Observación</th>
          </tr>
        </thead>
        <tbody>
          @for (q of quesos(); track q.id) {
            <tr>
              <td>{{ q.fecha | date: 'mediumDate' }}</td>
              <td>{{ q.fuente }}</td>
              <td class="num">{{ q.producidosKg | number: '1.0-1' }}</td>
              <td class="num">{{ q.vendidosKg | number: '1.0-1' }}</td>
              <td class="num">{{ q.producidosKg - q.vendidosKg | number: '1.0-1' }}</td>
              <td class="num">{{ q.totalVenta != null ? 'S/ ' + (q.totalVenta | number: '1.2-2') : 'No visible en evidencia' }}</td>
              <td class="muted">{{ q.observaciones ?? '—' }}</td>
            </tr>
          }
          @if (quesos().length === 0) {
            <tr>
              <td colspan="7" class="muted" style="text-align:center; padding:24px">Sin registros.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class QuesosComponent implements OnInit {
  readonly quesos = signal<Queso[]>([]);
  form: QuesoRequest = { ...VACIO };

  constructor(private readonly quesosService: QuesosService) {}

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.quesosService.listar().subscribe((lista) => this.quesos.set(lista));
  }

  guardar(): void {
    this.quesosService.crear(this.form).subscribe(() => {
      this.form = { ...VACIO };
      this.cargar();
    });
  }
}
