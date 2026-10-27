import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  Input,
  Output,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';
import { Subject, takeUntil } from 'rxjs';
import { RuteoService } from '../../core/services/ruteo.service';

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

export interface PoligonoMapa {
  id: number | string;
  puntos: Array<{ lat: number; lng: number }>;
  color?: string;
  etiqueta?: string;
}

// Coordenada aproximada de referencia (distrito de Huata, Puno, Perú) usada solo para centrar el
// mapa cuando todavía no hay ningun punto real que mostrar; no representa la ubicación exacta de
// ningún productor/centro (esos datos no se inventan).
const CENTRO_HUATA_PUNO: [number, number] = [-15.65, -69.98];

/**
 * Leaflet inserta como HTML el texto que recibe `bindTooltip`/`bindPopup` si es una cadena. Las
 * etiquetas llevan datos que escribe la gente (nombre de zona, de proveedor, de ruta): una zona
 * llamada `<img src=x onerror=...>` ejecutaría código en el navegador de quien abra el mapa (XSS
 * almacenado). Pasando un nodo con `textContent`, el texto se muestra literal.
 */
export function comoTexto(texto: string): HTMLElement {
  const nodo = document.createElement('span');
  nodo.textContent = texto;
  return nodo;
}

/** Mapa real (Leaflet + OpenStreetMap, sin API key) para GPS real de acopiadores. */
@Component({
  selector: 'app-mapa',
  standalone: true,
  template: `<div style="position:relative; width:100%; height:100%">
    <div
      #contenedor
      style="width:100%; height:100%; min-height:320px; border-radius:10px; overflow:hidden"
    ></div>
    @if (usuarioActual) {
      <div
        style="position:absolute; z-index:500; top:10px; right:10px; background:#fff; padding:7px 10px; border-radius:8px; box-shadow:0 2px 8px #0002; font-size:12px; font-weight:700"
      >
        👤 {{ usuarioActual }}
      </div>
    }
  </div>`,
})
export class MapaComponent implements AfterViewInit, OnChanges, OnDestroy {
  @ViewChild('contenedor', { static: true }) contenedorRef!: ElementRef<HTMLDivElement>;

  @Input() puntos: PuntoMapa[] = [];
  @Input() zoomInicial = 12;
  @Input() unirPuntos = false;
  @Input() trazado: PuntoMapa[] = [];
  @Input() editable = false;
  @Input() usuarioActual = '';
  /** Polígonos de zona (territorio real), dibujados debajo de marcadores y trazado. */
  @Input() poligonos: PoligonoMapa[] = [];
  /**
   * Si es true, el trazado se calcula por calles reales (Valhalla) en vez de unir los puntos con
   * una línea recta. Depende de un servicio público externo sin SLA (ver RuteoService): mientras
   * responde, y si falla, se conserva la línea recta como respaldo.
   */
  @Input() ruteoReal = false;
  /**
   * Identidad de lo que se está mostrando (la ruta, la zona o el filtro de GPS). El mapa se
   * reencuadra SOLO cuando esta clave cambia, es decir cuando el usuario pasa a mirar otra cosa.
   *
   * <p>Antes el encuadre se decidía con `!editable`, y eso rompía el dibujo: al hacer clic se
   * guardaba el punto, guardar ponía `editable` en false por un instante y el mapa se reencuadraba
   * en mitad del trazo. Cada clic devolvía la vista a otro zoom y había que volver a acercarse.
   */
  @Input() encuadrarEn = '';
  /**
   * Puntos que se dibujan como referencia y NO forman parte de la línea: los proveedores de la
   * ruta. Van aparte de `puntos` porque con `unirPuntos` estos entrarían en el trazado y lo
   * deformarían.
   */
  @Input() marcadores: PuntoMapa[] = [];
  /** Opt-in: los demás mapas conservan sus interacciones originales. */
  @Input() idsArrastrables: Array<number | string> = [];
  @Output() marcadorMovido = new EventEmitter<{
    id: number | string;
    latitud: number;
    longitud: number;
  }>();
  @Output() marcadorSeleccionado = new EventEmitter<number | string>();
  /**
   * Segunda línea, dibujada en otro color y punteada: el recorrido que pintó el acopiador frente al
   * trazado de la ruta. Va aparte de `trazado` porque las dos tienen que verse a la vez para poder
   * compararlas — es justamente la decisión que toma quien las mira.
   *
   * No pasa por el ruteo real (`ruteoReal`) aunque esté activo: es un trazo que alguien dibujó a
   * mano, y "corregirlo" por calles mostraría un recorrido que el acopiador no dibujó.
   */
  @Input() trazadoSecundario: PuntoMapa[] = [];
  @Input() colorSecundario = '#a1581f';
  /**
   * Recorrido GPS REAL (lo que registró el teléfono), en orden de captura. Va aparte del trazado
   * planificado y en otro color: uno es lo que se pensaba hacer y el otro lo que se hizo. Nunca pasa
   * por el ruteo por calles: son posiciones medidas, no una ruta a calcular.
   */
  @Input() recorrido: PuntoMapa[] = [];
  /** Cómo se dibuja `recorrido`: línea, puntos o ambos (preferencia de quien mira). */
  @Input() modoRecorrido: 'TRAZADO' | 'PUNTOS' | 'AMBOS' = 'TRAZADO';
  @Input() colorRecorrido = '#2f6fc4';
  @Output() puntoAgregado = new EventEmitter<{ latitud: number; longitud: number }>();

  private mapa?: L.Map;
  private marcadores_: L.Marker[] = [];
  private capasPoligono: L.Polygon[] = [];
  private capasMarcador: L.CircleMarker[] = [];
  private linea?: L.Polyline;
  private lineaSecundaria?: L.Polyline;
  private capaRecorrido?: L.LayerGroup;
  /**
   * Los puntos del recorrido se pintan en canvas y no como elementos del DOM: a un punto cada 10 m,
   * una vuelta de acopio de 20 km son unos 2 000 puntos, y 2 000 nodos SVG vuelven lento el mapa.
   */
  private readonly lienzo = soportaCanvas() ? L.canvas({ padding: 0.5 }) : undefined;
  private claveTrazadoVigente = '';
  /** Clave para la que ya se encuadró. null = todavía no se encuadró nunca. */
  private claveEncuadrada: string | null = null;
  private readonly destruir$ = new Subject<void>();

  constructor(private readonly ruteoService: RuteoService) {}

  ngAfterViewInit(): void {
    this.mapa = L.map(this.contenedorRef.nativeElement).setView(
      CENTRO_HUATA_PUNO,
      this.zoomInicial,
    );
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(this.mapa);
    this.actualizarCapas();
    this.mapa.on('click', (event: L.LeafletMouseEvent) => {
      if (this.editable)
        this.puntoAgregado.emit({ latitud: event.latlng.lat, longitud: event.latlng.lng });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (
      (changes['puntos'] ||
        changes['trazado'] ||
        changes['unirPuntos'] ||
        changes['poligonos'] ||
        changes['ruteoReal'] ||
        changes['marcadores'] ||
        changes['idsArrastrables'] ||
        changes['trazadoSecundario'] ||
        changes['recorrido'] ||
        changes['modoRecorrido'] ||
        changes['encuadrarEn']) &&
      this.mapa
    ) {
      this.actualizarCapas();
    }
  }

  ngOnDestroy(): void {
    this.destruir$.next();
    this.destruir$.complete();
    this.mapa?.remove();
  }

  private actualizarCapas(): void {
    if (!this.mapa) {
      return;
    }
    this.marcadores_.forEach((m) => m.remove());
    this.capasPoligono.forEach((p) => p.remove());
    this.capasMarcador.forEach((m) => m.remove());

    // Círculo y no chincheta: se distinguen de un vistazo de los puntos del trazado.
    this.capasMarcador = this.marcadores.map((p) =>
      L.circleMarker([p.lat, p.lng], {
        radius: 7,
        color: '#8a4b2a',
        fillColor: '#d98b5f',
        fillOpacity: 0.9,
        weight: 2,
      })
        .addTo(this.mapa as L.Map)
        .bindTooltip(comoTexto(p.etiqueta), { direction: 'top' }),
    );
    this.marcadores_ = this.puntos.map((p) => {
      const contenido = document.createElement('div');
      const titulo = document.createElement('strong');
      titulo.textContent = p.etiqueta;
      contenido.append(titulo);
      if (p.detalle) {
        const detalle = document.createElement('p');
        detalle.textContent = p.detalle;
        contenido.append(detalle);
      }
      const marcador = L.marker([p.lat, p.lng], { draggable: this.idsArrastrables.includes(p.id) })
        .addTo(this.mapa as L.Map)
        .bindPopup(contenido);
      marcador.on('click', () => this.marcadorSeleccionado.emit(p.id));
      marcador.on('dragend', () => {
        const posicion = marcador.getLatLng();
        this.marcadorMovido.emit({ id: p.id, latitud: posicion.lat, longitud: posicion.lng });
      });
      return marcador;
    });

    this.capasPoligono = this.poligonos
      .filter((z) => z.puntos.length > 2)
      .map((z) => {
        const capa = L.polygon(
          z.puntos.map((p) => [p.lat, p.lng] as L.LatLngTuple),
          { color: z.color ?? '#437862', weight: 2, fillOpacity: 0.12 },
        ).addTo(this.mapa as L.Map);
        if (z.etiqueta) capa.bindTooltip(comoTexto(z.etiqueta), { sticky: true });
        return capa;
      });

    const lineaPuntos = this.trazado.length ? this.trazado : this.unirPuntos ? this.puntos : [];
    this.dibujarTrazado(lineaPuntos);
    this.dibujarSecundaria();
    this.dibujarRecorrido();

    const coordenadas = [
      ...this.recorrido.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      ...this.puntos.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      ...this.marcadores.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      ...lineaPuntos.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      ...this.trazadoSecundario.map((p) => [p.lat, p.lng] as L.LatLngTuple),
    ];
    this.encuadrarSiCambioElContenido(coordenadas);
  }

  /**
   * Encuadra al entrar a un contenido nuevo y no vuelve a tocar la vista mientras se trabaja sobre
   * él: añadir, mover o quitar un punto conserva el zoom y el centro que eligió el usuario.
   */
  private encuadrarSiCambioElContenido(coordenadas: L.LatLngTuple[]): void {
    if (!this.mapa || coordenadas.length === 0 || this.encuadrarEn === this.claveEncuadrada) {
      return;
    }
    this.claveEncuadrada = this.encuadrarEn;
    this.mapa.fitBounds(L.latLngBounds(coordenadas).pad(0.2), { maxZoom: 15 });
  }

  /**
   * El recorrido real según el modo: la línea, los puntos (con la hora al pasar el mouse) o ambos.
   * El último punto —donde está ahora— siempre se marca más grande.
   */
  private dibujarRecorrido(): void {
    this.capaRecorrido?.remove();
    this.capaRecorrido = undefined;
    if (!this.mapa || this.recorrido.length === 0) return;
    const grupo = L.layerGroup();
    const coords = this.recorrido.map((p) => [p.lat, p.lng] as L.LatLngTuple);
    if (this.modoRecorrido !== 'PUNTOS' && coords.length > 1) {
      L.polyline(coords, { color: this.colorRecorrido, weight: 4, opacity: 0.85 }).addTo(grupo);
    }
    if (this.modoRecorrido !== 'TRAZADO') {
      this.recorrido.slice(0, -1).forEach((p) =>
        L.circleMarker([p.lat, p.lng], {
          renderer: this.lienzo,
          radius: 4,
          color: '#fff',
          weight: 1.5,
          fillColor: this.colorRecorrido,
          fillOpacity: 1,
        })
          .bindTooltip(comoTexto(p.etiqueta), { direction: 'top' })
          .addTo(grupo),
      );
    }
    const ultimo = this.recorrido[this.recorrido.length - 1];
    L.circleMarker([ultimo.lat, ultimo.lng], {
      radius: 8,
      color: '#fff',
      weight: 3,
      fillColor: this.colorRecorrido,
      fillOpacity: 1,
    })
      .bindTooltip(comoTexto(ultimo.etiqueta), { direction: 'top' })
      .addTo(grupo);
    this.capaRecorrido = grupo.addTo(this.mapa);
  }

  /** Punteada para que no se confunda con el trazado de la ruta cuando las dos se solapan. */
  private dibujarSecundaria(): void {
    this.lineaSecundaria?.remove();
    this.lineaSecundaria = undefined;
    if (this.trazadoSecundario.length < 2) {
      return;
    }
    this.lineaSecundaria = L.polyline(
      this.trazadoSecundario.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      { color: this.colorSecundario, weight: 4, dashArray: '8 6' },
    ).addTo(this.mapa as L.Map);
  }

  /**
   * Dibuja la línea recta de inmediato (respaldo visible mientras se calcula o si el ruteo real
   * falla) y, si `ruteoReal` está activo, la reemplaza por el camino real cuando llega.
   */
  private dibujarTrazado(lineaPuntos: PuntoMapa[]): void {
    this.linea?.remove();
    this.linea = undefined;
    if (lineaPuntos.length < 2) {
      this.claveTrazadoVigente = '';
      return;
    }
    this.linea = L.polyline(
      lineaPuntos.map((p) => [p.lat, p.lng] as L.LatLngTuple),
      { color: '#437862', weight: 4 },
    ).addTo(this.mapa as L.Map);

    const clave = JSON.stringify(lineaPuntos.map((p) => [p.lat, p.lng]));
    if (!this.ruteoReal || clave === this.claveTrazadoVigente) {
      return;
    }
    this.claveTrazadoVigente = clave;
    this.ruteoService
      .calcularRuta(lineaPuntos.map((p) => ({ lat: p.lat, lng: p.lng })))
      .pipe(takeUntil(this.destruir$))
      .subscribe((real) => {
        // Si el trazado cambió mientras esperábamos la respuesta, esta ya no aplica.
        if (clave !== this.claveTrazadoVigente || !this.mapa || real.length < 2) return;
        this.linea?.remove();
        this.linea = L.polyline(
          real.map((p) => [p.lat, p.lng] as L.LatLngTuple),
          { color: '#437862', weight: 4 },
        ).addTo(this.mapa);
      });
  }
}

/**
 * Si el navegador puede dibujar en canvas. Si no (navegadores muy viejos, o el entorno de pruebas),
 * los puntos se dibujan con el renderizador normal: más lento con miles de puntos, pero se ven.
 */
function soportaCanvas(): boolean {
  try {
    return !!document.createElement('canvas').getContext('2d');
  } catch {
    return false;
  }
}
