import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TrazabilidadItem } from '../../core/models/models';
import { TrazabilidadService } from '../../core/services/trazabilidad.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

/**
 * Seguimiento de producción: buscador libre por código de proveedor, zona, fecha o id de
 * recepción. El panel de "ruta en seguimiento" es un esquema (no un mapa real), con el mismo
 * criterio de honestidad del prototipo: "DEMO GPS · esquema, no a escala".
 */
@Component({
  selector: 'app-trazabilidad',
  standalone: true,
  imports: [FormsModule, DatePipe, BadgeEstadoComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Seguimiento de producción</div>
        <h1>Trazabilidad</h1>
        <p>Busca por código de proveedor, zona, fecha o id de recepción.</p>
      </div>
    </div>

    <div class="toolbar">
      <input
        type="text"
        placeholder="Ej: DEMO-001 · 2026-08-26 · Quillallaka · id de recepción…"
        [(ngModel)]="q"
        (ngModelChange)="buscar()"
      />
      <span class="badge badge-gris">DEMO GPS · esquema, no a escala</span>
    </div>

    @if (resultados().length > 0) {
      <div class="card" style="margin-bottom:16px">
        <strong style="font-size:11.5px">Ruta en seguimiento (esquemático)</strong>
        <div style="display:flex; align-items:center; gap:4px; margin-top:14px; overflow-x:auto; padding-bottom:6px">
          @for (r of resultados(); track r.recojoId; let last = $last) {
            <div style="display:flex; align-items:center; gap:4px; flex:0 0 auto">
              <div
                title="{{ r.productorCodigo }}"
                [style.background]="r.estado === 'CANCELLED' ? 'var(--red)' : 'var(--green2)'"
                style="width:14px; height:14px; border-radius:50%"
              ></div>
              @if (!last) {
                <div style="width:28px; height:2px; background:var(--line)"></div>
              }
            </div>
          }
        </div>
      </div>
    }

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Proveedor</th>
            <th>Zona</th>
            <th>Ruta</th>
            <th class="num">Litros</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          @for (r of resultados(); track r.recojoId) {
            <tr>
              <td>{{ r.fechaHora | date: 'short' }}</td>
              <td>{{ r.productorCodigo }} · {{ r.productorNombre }}</td>
              <td>{{ r.zona ?? '—' }}</td>
              <td>{{ r.rutaNombre ?? '—' }}</td>
              <td class="num">{{ r.litros }}</td>
              <td><app-badge-estado [estado]="r.estado" /></td>
            </tr>
          }
          @if (resultados().length === 0) {
            <tr>
              <td colspan="6" class="muted" style="text-align:center; padding:24px">Sin resultados.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class TrazabilidadComponent implements OnInit {
  readonly resultados = signal<TrazabilidadItem[]>([]);
  q = '';

  constructor(private readonly trazabilidadService: TrazabilidadService) {}

  ngOnInit(): void {
    this.buscar();
  }

  buscar(): void {
    this.trazabilidadService.buscar(this.q).subscribe((r) => this.resultados.set(r));
  }
}
