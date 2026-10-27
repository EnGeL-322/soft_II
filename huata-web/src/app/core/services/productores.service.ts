import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  EstadoProductor,
  EstadoSolicitudZona,
  PageResponse,
  Productor,
  ProductorUbicacion,
  SolicitudCambioZona,
  TipoUbicacion,
} from '../models/models';

export interface ProductorRequest {
  /**
   * El código del proveedor **es** su DNI y lo deriva el servidor, así que ya no se envía aparte:
   * mandarlo permitía que el código y el documento dijeran cosas distintas.
   */
  nombres: string;
  apellidos: string;
  documentoIdentidad: string;
  telefono?: string;
  rutaId?: number;
  notas?: string;
  acopiadorResponsableId?: number;
  zonaPrincipal?: string;
  ordenEnRuta?: number;
  username?: string;
  password?: string;
}

export interface FiltroProductores {
  q?: string;
  estado?: EstadoProductor;
  rutaId?: number;
  zona?: string;
  acopiadorId?: number;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductoresService {
  constructor(private readonly http: HttpClient) {}

  listar(opts: FiltroProductores) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.q) params['q'] = opts.q;
    if (opts.estado) params['estado'] = opts.estado;
    if (opts.rutaId) params['rutaId'] = String(opts.rutaId);
    if (opts.zona) params['zona'] = opts.zona;
    if (opts.acopiadorId) params['acopiadorId'] = String(opts.acopiadorId);
    return this.http.get<PageResponse<Productor>>(`${API_BASE_URL}/productores`, { params });
  }

  crear(datos: ProductorRequest) {
    return this.http.post<Productor>(`${API_BASE_URL}/productores`, datos);
  }

  actualizar(id: number, datos: ProductorRequest) {
    return this.http.put<Productor>(`${API_BASE_URL}/productores/${id}`, datos);
  }

  /**
   * Solo la ruta (y el orden) del proveedor, desde "Rutas y mapa". No pasa por `actualizar` porque
   * esa exige la ficha completa con DNI, y un proveedor heredado sin DNI no podría cambiar de ruta.
   */
  asignarRuta(id: number, rutaId: number, ordenEnRuta?: number | null) {
    return this.http.patch<Productor>(`${API_BASE_URL}/productores/${id}/ruta`, {
      rutaId,
      ordenEnRuta: ordenEnRuta ?? null,
    });
  }

  /**
   * `motivo` es obligatorio: la regla 7.3 exige que toda transicion quede auditada con el suyo y el
   * backend rechaza la peticion sin el. Declararlo opcional aqui solo servia para que el error
   * apareciera en tiempo de ejecucion en vez de en el compilador.
   */
  cambiarEstado(id: number, estado: EstadoProductor, motivo: string) {
    return this.http.patch<Productor>(`${API_BASE_URL}/productores/${id}/estado`, { estado, motivo });
  }

  /** 7.4: conceder o retirar la marca. El motivo es obligatorio y queda auditado. */
  cambiarExcelencia(id: number, excelencia: boolean, motivo: string) {
    return this.http.patch<Productor>(`${API_BASE_URL}/productores/${id}/excelencia`, { excelencia, motivo });
  }

  inactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/productores/${id}`);
  }

  // ---- Ubicación del proveedor (2.6) ----

  ubicaciones(productorId: number) {
    return this.http.get<ProductorUbicacion[]>(`${API_BASE_URL}/productores/${productorId}/ubicaciones`);
  }

  registrarUbicacion(
    productorId: number,
    datos: { latitud: number; longitud: number; precisionMetros?: number; tipo: TipoUbicacion },
  ) {
    return this.http.post<ProductorUbicacion>(`${API_BASE_URL}/productores/${productorId}/ubicaciones`, datos);
  }

  // ---- Solicitud de cambio de zona (2.5) ----

  solicitudesZona(estado?: EstadoSolicitudZona) {
    const params: Record<string, string> = {};
    if (estado) params['estado'] = estado;
    return this.http.get<SolicitudCambioZona[]>(`${API_BASE_URL}/productores/solicitudes-zona`, { params });
  }

  crearSolicitudZona(productorId: number, zonaSolicitada: string, motivo?: string) {
    return this.http.post<SolicitudCambioZona>(`${API_BASE_URL}/productores/${productorId}/solicitudes-zona`, {
      zonaSolicitada,
      motivo,
    });
  }

  decidirSolicitudZona(id: number, aprobar: boolean, observacion?: string) {
    return this.http.patch<SolicitudCambioZona>(`${API_BASE_URL}/productores/solicitudes-zona/${id}`, {
      aprobar,
      observacion,
    });
  }
}
