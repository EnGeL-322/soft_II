import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { PageResponse, RegistroAuditoria, RolInfo } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  constructor(private readonly http: HttpClient) {}

  listar(page = 0, size = 30) {
    return this.http.get<PageResponse<RegistroAuditoria>>(`${API_BASE_URL}/auditoria`, { params: { page, size } });
  }

  roles() {
    return this.http.get<RolInfo[]>(`${API_BASE_URL}/roles`);
  }
}
