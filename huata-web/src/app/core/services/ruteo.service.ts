import { Injectable } from '@angular/core';
import { Observable, from } from 'rxjs';

export interface PuntoRuteo {
  lat: number;
  lng: number;
}

interface ValhallaLeg {
  shape: string;
}

/**
 * Ruteo real por calles vía Valhalla (servidor público de OpenStreetMap/FOSSGIS, sin API key,
 * sin SLA garantizado — ver integracion-huata/README.md). Usa `fetch` nativo y NO HttpClient a
 * propósito: el interceptor de autenticación agrega el Bearer JWT de la sesión a toda petición de
 * HttpClient (ver core/interceptors/auth.interceptor.ts), y ese token no debe salir hacia un
 * servidor público de terceros.
 *
 * Decodificación de polyline6 y fragmentación por el límite de 10 ubicaciones del servidor
 * portadas tal cual de integracion-huata/assets-static/huata/index.html (demo ya validada).
 */
const VALHALLA_URL = 'https://valhalla1.openstreetmap.de/route';
const MAX_UBICACIONES = 10;
const TIMEOUT_MS = 15_000;

function decodePolyline6(texto: string): PuntoRuteo[] {
  let index = 0;
  let lat = 0;
  let lng = 0;
  const coords: PuntoRuteo[] = [];
  while (index < texto.length) {
    let byte: number;
    let shift = 0;
    let result = 0;
    do {
      byte = texto.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    lat += result & 1 ? ~(result >> 1) : result >> 1;
    shift = 0;
    result = 0;
    do {
      byte = texto.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    lng += result & 1 ? ~(result >> 1) : result >> 1;
    coords.push({ lat: lat / 1e6, lng: lng / 1e6 });
  }
  return coords;
}

async function pedirTramo(ubicaciones: PuntoRuteo[], excluirTrocha: boolean): Promise<PuntoRuteo[]> {
  const cuerpo = {
    locations: ubicaciones.map((p) => ({ lat: p.lat, lon: p.lng })),
    costing: 'auto',
    costing_options: { auto: { exclude_unpaved: excluirTrocha } },
    units: 'kilometers',
  };
  const url = `${VALHALLA_URL}?json=${encodeURIComponent(JSON.stringify(cuerpo))}`;
  const controlador = new AbortController();
  const temporizador = setTimeout(() => controlador.abort(), TIMEOUT_MS);
  try {
    const respuesta = await fetch(url, { signal: controlador.signal });
    const datos = await respuesta.json().catch(() => ({}));
    if (!respuesta.ok || datos.error) throw new Error(datos.error || `HTTP ${respuesta.status}`);
    const legs: ValhallaLeg[] = datos.trip?.legs ?? [];
    return legs.flatMap((leg, i) => {
      const shape = decodePolyline6(leg.shape);
      return i === 0 ? shape : shape.slice(1);
    });
  } finally {
    clearTimeout(temporizador);
  }
}

/** El servidor público limita a 10 ubicaciones por solicitud: se encadenan tramos si hay más paradas. */
async function pedirTramosEncadenados(puntos: PuntoRuteo[], excluirTrocha: boolean): Promise<PuntoRuteo[]> {
  if (puntos.length <= MAX_UBICACIONES) return pedirTramo(puntos, excluirTrocha);
  let geometria: PuntoRuteo[] = [];
  let i = 0;
  while (i < puntos.length - 1) {
    const fin = Math.min(i + MAX_UBICACIONES - 1, puntos.length - 1);
    const shape = await pedirTramo(puntos.slice(i, fin + 1), excluirTrocha);
    geometria = geometria.length ? [...geometria, ...shape.slice(1)] : shape;
    i = fin;
  }
  return geometria;
}

async function pedirRutaCompleta(puntos: PuntoRuteo[]): Promise<PuntoRuteo[]> {
  try {
    return await pedirTramosEncadenados(puntos, true);
  } catch {
    // Reintenta permitiendo trocha: puede no existir conexión 100% asfaltada entre los puntos.
    return pedirTramosEncadenados(puntos, false);
  }
}

@Injectable({ providedIn: 'root' })
export class RuteoService {
  /**
   * Camino real por calles entre los puntos dados, en el orden dado. Nunca emite error: si el
   * servicio público falla o no hay conexión vial encontrada, resuelve con un arreglo vacío para
   * que quien llama conserve la línea recta como respaldo.
   */
  calcularRuta(puntos: PuntoRuteo[]): Observable<PuntoRuteo[]> {
    if (puntos.length < 2) return from(Promise.resolve<PuntoRuteo[]>([]));
    return from(pedirRutaCompleta(puntos).catch(() => [] as PuntoRuteo[]));
  }
}
