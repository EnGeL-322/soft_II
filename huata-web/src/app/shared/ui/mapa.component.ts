import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';

// Los iconos por defecto de Leaflet intentan resolver su ruta desde el propio <script>, lo que no
// funciona empaquetado con Angular. Se apuntan explícitamente a los assets copiados por
// angular.json (node_modules/leaflet/dist/images -> "leaflet/").
delete (L.Icon.Default.prototype as unknown as { _getIconUrl?: unknown })._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'leaflet/marker-icon-2x.png',
  iconUrl: 'leaflet/marker-icon.png',
  shadowUrl: 'leaflet/marker-shadow.png',
});

export interface PuntoMapa {
  id: number | string;
  lat: number;
  lng: number;
  etiqueta: string;
  detalle?: string;
}

// Coordenada aproximada de referencia (distrito de Huata, Puno, Perú) usada solo para centrar el
// mapa cuando todavía no hay ningun punto real que mostrar; no representa la ubicación exacta de
// ningún productor/centro (esos datos no se inventan).
const CENTRO_HUATA_PUNO: [number, number] = [-15.65, -69.98];

/** Mapa real (Leaflet + OpenStreetMap, sin API key) para GPS real de acopiadores. */
@Component({
  selector: 'app-mapa',
  standalone: true,
  template: `<div #contenedor style="width:100%; height:100%; min-height:320px; border-radius:10px; overflow:hidden"></div>`,
})
export class MapaComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('contenedor', { static: true }) contenedorRef!: ElementRef<HTMLDivElement>;

  @Input() puntos: PuntoMapa[] = [];
  @Input() zoomInicial = 12;

  private mapa?: L.Map;
  private marcadores: L.Marker[] = [];

  ngAfterViewInit(): void {
    this.mapa = L.map(this.contenedorRef.nativeElement).setView(CENTRO_HUATA_PUNO, this.zoomInicial);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(this.mapa);
    this.actualizarMarcadores();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['puntos'] && this.mapa) {
      this.actualizarMarcadores();
    }
  }

  ngOnDestroy(): void {
    this.mapa?.remove();
  }

  private actualizarMarcadores(): void {
    if (!this.mapa) {
      return;
    }
    this.marcadores.forEach((m) => m.remove());
    this.marcadores = this.puntos.map((p) =>
      L.marker([p.lat, p.lng])
        .addTo(this.mapa as L.Map)
        .bindPopup(`<b>${p.etiqueta}</b>${p.detalle ? '<br>' + p.detalle : ''}`),
    );

    if (this.marcadores.length > 0) {
      const grupo = L.featureGroup(this.marcadores);
      this.mapa.fitBounds(grupo.getBounds().pad(0.3), { maxZoom: 15 });
    }
  }
}
