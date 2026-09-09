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
  templateUrl: './trazabilidad.component.html',
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
