import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { TrazabilidadItem, ZonaResumen } from '../models/models';

@Injectable({ providedIn: 'root' })
export class TrazabilidadService {
  constructor(private readonly http: HttpClient) {}

  buscar(q: string) {
    return this.http.get<TrazabilidadItem[]>(`${API_BASE_URL}/reportes/trazabilidad`, { params: { q: q ?? '' } });
  }

  zonas() {
    return this.http.get<ZonaResumen[]>(`${API_BASE_URL}/reportes/zonas`);
  }
}
