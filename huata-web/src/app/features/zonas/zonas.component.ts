import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ZonaResumen } from '../../core/models/models';
import { TrazabilidadService } from '../../core/services/trazabilidad.service';

/** Zonas y rendimiento: semana actual vs semana anterior por zona (la zona sigue siendo un campo
 * libre de Ruta, no una lista fija — ver decisión C-05). */
@Component({
  selector: 'app-zonas',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Zonas y rendimiento</div>
        <h1>Comparativo por zona</h1>
        <p>Últimos 7 días vs los 7 anteriores. La zona es un dato configurable dentro de cada ruta, no una lista fija.</p>
      </div>
    </div>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Zona</th>
            <th class="num">Semana anterior (L)</th>
            <th class="num">Semana actual (L)</th>
            <th class="num">Variación</th>
            <th>Participación</th>
          </tr>
        </thead>
        <tbody>
          @for (z of zonas(); track z.zona) {
            <tr>
              <td>{{ z.zona }}</td>
              <td class="num">{{ z.litrosPeriodoAnterior | number: '1.0-1' }}</td>
              <td class="num">{{ z.litrosPeriodoActual | number: '1.0-1' }}</td>
              <td class="num" [style.color]="z.variacionPorciento >= 0 ? 'var(--green)' : 'var(--red)'">
                {{ z.variacionPorciento >= 0 ? '+' : '' }}{{ z.variacionPorciento | number: '1.0-1' }}%
              </td>
              <td style="min-width:160px">
                <div class="bartrack"><div class="barfill" [style.width.%]="z.participacionPorciento"></div></div>
                <span class="muted" style="font-size:10.5px">{{ z.participacionPorciento | number: '1.0-1' }}%</span>
              </td>
            </tr>
          }
          @if (zonas().length === 0) {
            <tr>
              <td colspan="5" class="muted" style="text-align:center; padding:24px">Sin recojos en el periodo.</td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class ZonasComponent implements OnInit {
  readonly zonas = signal<ZonaResumen[]>([]);

  constructor(private readonly trazabilidadService: TrazabilidadService) {}

  ngOnInit(): void {
    this.trazabilidadService.zonas().subscribe((z) => this.zonas.set(z));
  }
}
