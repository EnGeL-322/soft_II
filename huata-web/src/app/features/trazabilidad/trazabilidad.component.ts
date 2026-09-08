import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TrazabilidadItem, UbicacionGps } from '../../core/models/models';
import { TrazabilidadService } from '../../core/services/trazabilidad.service';
import { UbicacionesService } from '../../core/services/ubicaciones.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { MapaComponent, PuntoMapa } from '../../shared/ui/mapa.component';

/**
 * Seguimiento de producción: buscador libre por código de proveedor, zona, fecha o id de
 * recepción, más un mapa GPS real (Leaflet + OpenStreetMap) con la última posición de cada
 * acopiador que haya compartido su ubicación desde "Recepción / Acopio".
 */
@Component({
  selector: 'app-trazabilidad',
  standalone: true,
  imports: [FormsModule, DatePipe, BadgeEstadoComponent, MapaComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Seguimiento de producción</div>
        <h1>Trazabilidad</h1>
        <p>Busca por código de proveedor, zona, fecha o id de recepción.</p>
      </div>
    </div>

    <div class="table-title" style="margin-top:0">
      <div>
        <h3>Acopiadores en ruta (GPS real)</h3>
        <p>
          Posición reportada por el navegador del acopiador desde "Recepción / Acopio". Sin
          simulación: si nadie compartió su ubicación todavía, el mapa aparece vacío.
        </p>
      </div>
    </div>
    <div class="card" style="height:340px; padding:0; overflow:hidden; margin-bottom:20px">
      <app-mapa [puntos]="puntosMapa()" />
    </div>
    @if (ubicaciones().length === 0) {
      <p class="muted" style="margin:-10px 0 20px">
        Ningún acopiador ha compartido su ubicación todavía.
      </p>
    }

    <div class="toolbar">
      <input
        type="text"
        placeholder="Ej: DEMO-001 · 2026-08-26 · Quillallaka · id de recepción…"
        [(ngModel)]="q"
        (ngModelChange)="buscar()"
      />
    </div>

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
  readonly ubicaciones = signal<UbicacionGps[]>([]);
  readonly puntosMapa = computed<PuntoMapa[]>(() =>
    this.ubicaciones().map((u) => ({
      id: u.acopiadorId,
      lat: u.latitud,
      lng: u.longitud,
      etiqueta: u.acopiadorNombre,
      detalle: 'Actualizado: ' + new Date(u.fecha).toLocaleString(),
    })),
  );

  q = '';

  constructor(
    private readonly trazabilidadService: TrazabilidadService,
    private readonly ubicacionesService: UbicacionesService,
  ) {}

  ngOnInit(): void {
    this.buscar();
    this.ubicacionesService.ultimas().subscribe((u) => this.ubicaciones.set(u));
  }

  buscar(): void {
    this.trazabilidadService.buscar(this.q).subscribe((r) => this.resultados.set(r));
  }
}
