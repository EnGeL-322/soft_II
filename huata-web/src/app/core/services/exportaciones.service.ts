import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

@Injectable({ providedIn: 'root' })
export class ExportacionesService {
  constructor(private readonly http: HttpClient) {}

  hojas() {
    return this.http.get<string[]>(`${API_BASE_URL}/exportaciones`);
  }

  descargar(hoja: string) {
    return this.http.get(`${API_BASE_URL}/exportaciones/${hoja}`, { responseType: 'blob' });
  }
}
