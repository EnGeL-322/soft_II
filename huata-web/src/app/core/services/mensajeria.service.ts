import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

export interface ConversacionProveedor { productorId:number; productorCodigo:string; productorNombre:string; zona:string|null; telefono:string|null; mensajesSinLeer:number; ultimoMensajeAt:string|null; ultimoMensaje:string|null; }
/** Lo que el servidor sabe del mensaje frente a su destinatario. "Pendiente" y "error" viven solo en la cola local. */
export type EstadoMensaje = 'ENVIADO'|'ENTREGADO'|'LEIDO';
export interface MensajeProveedor { id:number; autorId:number; autorNombre:string; mio:boolean; cuerpo:string; creadoEn:string; leidoEn:string|null; productorId:number; clientUuid:string|null; estado:EstadoMensaje; }
export interface ConversacionDetalle { conversacion:ConversacionProveedor; mensajes:MensajeProveedor[]; }
export type DestinoAviso = 'TODOS'|'ZONA'|'SELECCION';
export interface AvisoEnviado { destinatarios:number; }

@Injectable({providedIn:'root'})
export class MensajeriaService {
  constructor(private readonly http:HttpClient) {}
  listar(q='') { return this.http.get<ConversacionProveedor[]>(`${API_BASE_URL}/mensajeria/conversaciones`, {params:q ? {q} : {}}); }
  detalle(productorId:number) { return this.http.get<ConversacionDetalle>(`${API_BASE_URL}/mensajeria/conversaciones/${productorId}`); }
  /** Con clientUuid el envio es idempotente: reintentarlo tras un corte no duplica el mensaje. */
  enviar(productorId:number, cuerpo:string, clientUuid?:string) { return this.http.post<MensajeProveedor>(`${API_BASE_URL}/mensajeria/conversaciones/${productorId}/mensajes`, {cuerpo, clientUuid}); }
  enviarAviso(cuerpo:string, destino:DestinoAviso, zona?:string, productorIds?:number[]) {
    return this.http.post<AvisoEnviado>(`${API_BASE_URL}/mensajeria/avisos`, {cuerpo,destino,zona,productorIds});
  }
}
