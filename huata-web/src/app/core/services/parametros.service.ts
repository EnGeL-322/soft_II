import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ParametroNegocio } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ParametrosService {
  constructor(private readonly http: HttpClient) {}

  listar() {
    return this.http.get<ParametroNegocio[]>(`${API_BASE_URL}/parametros-negocio`);
  }

  guardar(datos: { clave: string; valor: string; descripcion?: string }) {
    return this.http.post<ParametroNegocio>(`${API_BASE_URL}/parametros-negocio`, datos);
  }
}
