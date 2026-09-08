import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ConsultaPublica } from '../models/models';

/** Sin autenticación: el mismo backend expone /api/v1/public/** con permitAll (ver SecurityConfig). */
@Injectable({ providedIn: 'root' })
export class PortalService {
  constructor(private readonly http: HttpClient) {}

  consultar(codigo: string) {
    return this.http.get<ConsultaPublica>(`${API_BASE_URL}/public/productores/${codigo}/calidad`);
  }
}
