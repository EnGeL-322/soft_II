import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as L from 'leaflet';
import { MapaComponent, PuntoMapa, comoTexto } from './mapa.component';
import { RuteoService } from '../../core/services/ruteo.service';

/**
 * El encuadre automático del mapa.
 *
 * <p>El bug que se fija aquí: al pintar una ruta, cada clic guardaba el punto, guardar ponía
 * `editable` en false por un instante y el mapa —que decidía el encuadre con `!editable`— se
 * reencuadraba en mitad del trazo. El usuario perdía el zoom en cada punto que marcaba.
 */
describe('MapaComponent · encuadre', () => {
  let fixture: ComponentFixture<MapaComponent>;
  let componente: MapaComponent;
  let encuadres: L.LatLngBounds[];

  const punto = (id: number, lat: number, lng: number): PuntoMapa => ({
    id,
    lat,
    lng,
    etiqueta: `Punto ${id}`,
  });

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [MapaComponent],
      providers: [
        { provide: RuteoService, useValue: { calcularRuta: () => ({ subscribe: () => ({}) }) } },
      ],
    });
    fixture = TestBed.createComponent(MapaComponent);
    componente = fixture.componentInstance;

    encuadres = [];
    // Leaflet necesita un contenedor con tamaño real; en JSDOM se espía el encuadre, que es lo que
    // se quiere verificar, sin depender de que el mapa se pinte.
    const original = L.Map.prototype.fitBounds;
    vi.spyOn(L.Map.prototype, 'fitBounds').mockImplementation(function (
      this: L.Map,
      bounds: L.LatLngBoundsExpression,
    ) {
      encuadres.push(L.latLngBounds(bounds as L.LatLngBoundsLiteral));
      return this;
    });
    expect(original).toBeDefined();
  });

  function render(puntos: PuntoMapa[], encuadrarEn: string, editable = false) {
    componente.puntos = puntos;
    componente.encuadrarEn = encuadrarEn;
    componente.editable = editable;
    componente.ngOnChanges({
      puntos: {
        currentValue: puntos,
        previousValue: [],
        firstChange: false,
        isFirstChange: () => false,
      },
      encuadrarEn: {
        currentValue: encuadrarEn,
        previousValue: '',
        firstChange: false,
        isFirstChange: () => false,
      },
    });
  }

  it('el marcador arrastrable emite su ID y posición sin cambiar el encuadre', () => {
    fixture.detectChanges();
    componente.idsArrastrables = [1];
    render([punto(1, -15.6, -69.9)], 'gestion:1');
    const mover = vi.fn();
    componente.marcadorMovido.subscribe(mover);
    const marcador = (componente as unknown as { marcadores_: L.Marker[] }).marcadores_[0];
    expect(marcador.options.draggable).toBe(true);
    const antes = encuadres.length;
    marcador.setLatLng([-15.7, -70]);
    marcador.fire('dragend');
    expect(mover).toHaveBeenCalledWith({ id: 1, latitud: -15.7, longitud: -70 });
    expect(encuadres).toHaveLength(antes);
  });

  it('encuadra la primera vez que llega contenido', () => {
    fixture.detectChanges(); // ngAfterViewInit crea el mapa

    render([punto(1, -15.6, -69.9), punto(2, -15.7, -69.8)], 'ruta:3');

    expect(encuadres.length).toBe(1);
  });

  it('NO reencuadra al añadir un punto a la misma ruta, aunque editable cambie al guardar', () => {
    fixture.detectChanges();
    render([punto(1, -15.6, -69.9), punto(2, -15.7, -69.8)], 'ruta:3', true);
    const trasElPrimerEncuadre = encuadres.length;

    // Un clic en el mapa: se añade el punto y, mientras se guarda, editable pasa a false.
    render(
      [punto(1, -15.6, -69.9), punto(2, -15.7, -69.8), punto(3, -15.8, -69.7)],
      'ruta:3',
      false,
    );
    // Terminó de guardar y vuelve a modo dibujo.
    render(
      [punto(1, -15.6, -69.9), punto(2, -15.7, -69.8), punto(3, -15.8, -69.7)],
      'ruta:3',
      true,
    );

    expect(encuadres.length).toBe(trasElPrimerEncuadre);
  });

  it('reencuadra cuando el usuario pasa a mirar otra ruta', () => {
    fixture.detectChanges();
    render([punto(1, -15.6, -69.9), punto(2, -15.7, -69.8)], 'ruta:3');

    render([punto(9, -15.1, -70.4), punto(10, -15.2, -70.3)], 'ruta:4');

    expect(encuadres.length).toBe(2);
  });

  it('no encuadra sobre un contenido vacío: espera a que lleguen los puntos', () => {
    fixture.detectChanges();

    render([], 'ruta:3');
    expect(encuadres.length).toBe(0);

    render([punto(1, -15.6, -69.9)], 'ruta:3');
    expect(encuadres.length).toBe(1);
  });

  // ---- Recorrido GPS real: trazado, puntos o ambos ----

  function dibujarRecorrido(modo: 'TRAZADO' | 'PUNTOS' | 'AMBOS') {
    componente.recorrido = [
      punto(1, -15.6, -69.9),
      punto(2, -15.61, -69.91),
      punto(3, -15.62, -69.92),
    ];
    componente.modoRecorrido = modo;
    componente.ngOnChanges({
      recorrido: {
        currentValue: componente.recorrido,
        previousValue: [],
        firstChange: false,
        isFirstChange: () => false,
      },
    });
    const capa = (componente as unknown as { capaRecorrido?: L.LayerGroup }).capaRecorrido;
    const capas = capa?.getLayers() ?? [];
    return {
      lineas: capas.filter((c) => c instanceof L.Polyline && !(c instanceof L.Polygon)).length,
      puntos: capas.filter((c) => c instanceof L.CircleMarker).length,
    };
  }

  it('en modo trazado dibuja la línea y marca solo la posición actual', () => {
    fixture.detectChanges();
    expect(dibujarRecorrido('TRAZADO')).toEqual({ lineas: 1, puntos: 1 });
  });

  it('en modo puntos no dibuja línea y marca cada punto', () => {
    fixture.detectChanges();
    expect(dibujarRecorrido('PUNTOS')).toEqual({ lineas: 0, puntos: 3 });
  });

  it('en modo ambos dibuja la línea y todos los puntos', () => {
    fixture.detectChanges();
    expect(dibujarRecorrido('AMBOS')).toEqual({ lineas: 1, puntos: 3 });
  });
});

/** Las etiquetas del mapa son datos que escribe la gente: nunca deben interpretarse como HTML. */
describe('MapaComponent · etiquetas seguras', () => {
  it('una etiqueta con HTML se muestra literal y no crea elementos', () => {
    const malicioso = '<img src=x onerror="window.__xss = true">Zona';
    const nodo = comoTexto(malicioso);

    expect(nodo.textContent).toBe(malicioso);
    expect(nodo.querySelector('img')).toBeNull();
    expect(nodo.children.length).toBe(0);
  });
});
