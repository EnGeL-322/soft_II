import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Notificacion } from '../models/models';

@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  constructor(private readonly http: HttpClient) {}

  listar() {
    return this.http.get<Notificacion[]>(`${API_BASE_URL}/notificaciones`);
  }

  marcarLeida(id: number) {
    return this.http.patch<Notificacion>(`${API_BASE_URL}/notificaciones/${id}/leer`, {});
  }
}
