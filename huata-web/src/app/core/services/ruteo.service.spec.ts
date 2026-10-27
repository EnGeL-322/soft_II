import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { vi } from 'vitest';
import { PuntoRuteo, RuteoService } from './ruteo.service';

/** Codifica polyline6 (inverso de decodePolyline6) para poder construir respuestas de prueba realistas. */
function encodePolyline6(puntos: PuntoRuteo[]): string {
  let prevLat = 0;
  let prevLng = 0;
  let resultado = '';
  const codificarValor = (valor: number) => {
    let v = valor < 0 ? ~(valor << 1) : valor << 1;
    let salida = '';
    while (v >= 0x20) {
      salida += String.fromCharCode((0x20 | (v & 0x1f)) + 63);
      v >>= 5;
    }
    salida += String.fromCharCode(v + 63);
    return salida;
  };
  for (const p of puntos) {
    const lat = Math.round(p.lat * 1e6);
    const lng = Math.round(p.lng * 1e6);
    resultado += codificarValor(lat - prevLat) + codificarValor(lng - prevLng);
    prevLat = lat;
    prevLng = lng;
  }
  return resultado;
}

function respuestaValhalla(tramos: PuntoRuteo[][]): Response {
  return {
    ok: true,
    json: () =>
      Promise.resolve({
        trip: { legs: tramos.map((puntos) => ({ shape: encodePolyline6(puntos) })) },
      }),
  } as unknown as Response;
}

describe('RuteoService', () => {
  let fetchOriginal: typeof fetch;

  beforeEach(() => {
    fetchOriginal = globalThis.fetch;
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    globalThis.fetch = fetchOriginal;
  });

  function crear() {
    return TestBed.inject(RuteoService);
  }

  it('no llama a la red si hay menos de dos puntos', async () => {
    const espia = vi.fn();
    globalThis.fetch = espia as unknown as typeof fetch;

    const resultado = await firstValueFrom(crear().calcularRuta([{ lat: -15.6, lng: -69.9 }]));

    expect(resultado).toEqual([]);
    expect(espia).not.toHaveBeenCalled();
  });

  it('decodifica el shape devuelto por Valhalla en las coordenadas originales', async () => {
    const esperado: PuntoRuteo[] = [
      { lat: -15.613667, lng: -69.976278 },
      { lat: -15.6, lng: -69.97 },
      { lat: -15.59, lng: -69.96 },
    ];
    globalThis.fetch = vi.fn().mockResolvedValue(respuestaValhalla([esperado])) as unknown as typeof fetch;

    const resultado = await firstValueFrom(crear().calcularRuta([esperado[0], esperado[esperado.length - 1]]));

    expect(resultado.length).toBe(esperado.length);
    resultado.forEach((p, i) => {
      expect(p.lat).toBeCloseTo(esperado[i].lat, 5);
      expect(p.lng).toBeCloseTo(esperado[i].lng, 5);
    });
  });

  it('nunca emite error: si el servicio falla, resuelve con arreglo vacío para conservar la línea recta', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new Error('sin red')) as unknown as typeof fetch;

    const resultado = await firstValueFrom(
      crear().calcularRuta([
        { lat: -15.6, lng: -69.9 },
        { lat: -15.61, lng: -69.91 },
      ]),
    );

    expect(resultado).toEqual([]);
  });

  it('fragmenta en tramos encadenados cuando hay más de 10 paradas, sin duplicar el punto de unión', async () => {
    const puntos: PuntoRuteo[] = Array.from({ length: 12 }, (_, i) => ({ lat: -15 - i * 0.01, lng: -69 - i * 0.01 }));
    const espia = vi.fn().mockImplementation((url: string) => {
      const json = JSON.parse(decodeURIComponent(url.split('json=')[1]));
      const ubicaciones: PuntoRuteo[] = json.locations.map((l: { lat: number; lon: number }) => ({
        lat: l.lat,
        lng: l.lon,
      }));
      return Promise.resolve(respuestaValhalla([ubicaciones]));
    });
    globalThis.fetch = espia as unknown as typeof fetch;

    const resultado = await firstValueFrom(crear().calcularRuta(puntos));

    // 12 puntos con maximo 10 ubicaciones por solicitud -> al menos 2 solicitudes encadenadas.
    expect(espia.mock.calls.length).toBeGreaterThanOrEqual(2);
    expect(resultado.length).toBe(puntos.length);
    resultado.forEach((p, i) => {
      expect(p.lat).toBeCloseTo(puntos[i].lat, 5);
      expect(p.lng).toBeCloseTo(puntos[i].lng, 5);
    });
  });
});
