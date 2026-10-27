import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

export type PropositoVerificacion = 'PRECIOS';

/**
 * Lo que devuelve el backend al pedir un código. **No trae el código**: solo a dónde se envió —el
 * buzón enmascarado— y cuándo vence. Si viniera aquí, toda la verificación sería un adorno.
 */
export interface CodigoEmitido {
  destinatarioEnmascarado: string;
  expiraEn: string;
  minutosVigencia: number;
}

/** Código de 6 dígitos que Configuración exige para confirmar un cambio de precios. */
@Injectable({ providedIn: 'root' })
export class VerificacionService {
  constructor(private readonly http: HttpClient) {}

  solicitarCodigo(proposito: PropositoVerificacion = 'PRECIOS') {
    return this.http.post<CodigoEmitido>(`${API_BASE_URL}/verificacion/codigo`, { proposito });
  }
}
