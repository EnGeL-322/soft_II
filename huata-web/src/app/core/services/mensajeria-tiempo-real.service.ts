import { Injectable, InjectionToken, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { AuthService } from './auth.service';
import { EstadoSocket } from './canal-tiempo-real';
import { EstadoMensaje, MensajeProveedor } from './mensajeria.service';

/** Lo que manda el servidor por /api/ws/mensajeria (ver MensajeriaWebSocketHandler en el backend). */
export type EventoMensajeria =
  | { type: 'LISTO'; usuarioId: number; planta: boolean; productorId: number | null }
  | { type: 'MENSAJE'; mensaje: MensajeProveedor }
  | { type: 'ESTADO'; productorId: number; ids: number[]; estado: EstadoMensaje; en: string }
  | { type: 'SYNC'; mensajes: MensajeProveedor[]; completo: boolean };

/** Lo minimo de un WebSocket que usa este servicio. Existe para que las pruebas pongan uno falso. */
export interface SocketMensajeria {
  send(datos: string): void;
  close(): void;
  onopen: ((e: unknown) => void) | null;
  onmessage: ((e: { data: string }) => void) | null;
  onclose: ((e: unknown) => void) | null;
  onerror: ((e: unknown) => void) | null;
}

export const FABRICA_SOCKET_MENSAJERIA = new InjectionToken<(url: string) => SocketMensajeria>('FABRICA_SOCKET_MENSAJERIA', {
  providedIn: 'root',
  factory: () => (url: string) => new WebSocket(url) as unknown as SocketMensajeria,
});

export const ESPERA_MAXIMA_MS = 30_000;
export const LATIDO_MS = 25_000;
export const SIN_RESPUESTA_MS = 10_000;

/** 1 s, 2 s, 4 s, 8 s… hasta 30 s: reintentar rapido un corte breve sin martillar un servidor caido. */
export function esperaReconexion(intento: number): number {
  return Math.min(1000 * 2 ** intento, ESPERA_MAXIMA_MS);
}

/**
 * Canal en vivo de la mensajeria. A diferencia de `abrirCanal` (solo lectura, espera fija) este habla
 * con el servidor: pide lo que se perdio al reconectar (SYNC desde el ultimo id visto), confirma lo que
 * llego (ENTREGADO) y manda un latido (PING) para detectar una conexion muerta que el navegador todavia
 * cree abierta — pasa al cambiar de red, y sin latido la pantalla se quedaria muda sin saberlo.
 *
 * Enviar mensajes NO va por aqui: va por el POST REST de siempre, que es idempotente por clientUuid.
 */
@Injectable({ providedIn: 'root' })
export class MensajeriaTiempoRealService {
  private readonly auth = inject(AuthService);
  private readonly fabrica = inject(FABRICA_SOCKET_MENSAJERIA);

  readonly estado = signal<EstadoSocket>('desconectado');
  readonly eventos$ = new Subject<EventoMensajeria>();

  private socket: SocketMensajeria | null = null;
  private usuarios = 0;
  private intento = 0;
  private ultimoId: number | null = null;
  private reconexion?: ReturnType<typeof setTimeout>;
  private latido?: ReturnType<typeof setInterval>;
  private vigilancia?: ReturnType<typeof setTimeout>;
  private readonly alVolverLaRed = () => this.reconectarYa();

  /** Varias pantallas pueden usar el canal: se abre con la primera y se cierra con la ultima. */
  iniciar(): void {
    this.usuarios++;
    if (this.usuarios > 1) return;
    this.intento = 0;
    window.addEventListener('online', this.alVolverLaRed);
    this.abrir();
  }

  detener(): void {
    if (this.usuarios === 0) return;
    this.usuarios--;
    if (this.usuarios > 0) return;
    window.removeEventListener('online', this.alVolverLaRed);
    clearTimeout(this.reconexion);
    this.descartarSocket();
    this.estado.set('desconectado');
  }

  /** El ultimo id que la pantalla ya tiene: desde ahi se pide el SYNC al reconectar. */
  registrarVistos(ids: number[]): void {
    for (const id of ids) if (this.ultimoId === null || id > this.ultimoId) this.ultimoId = id;
  }

  private abrir(): void {
    const token = this.auth.token;
    if (!token || this.usuarios === 0) {
      this.estado.set('desconectado');
      return;
    }
    this.estado.set('conectando');
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    let socket: SocketMensajeria;
    try {
      socket = this.fabrica(`${protocolo}//${location.host}/api/ws/mensajeria`);
    } catch {
      this.programarReconexion();
      return;
    }
    this.socket = socket;
    socket.onopen = () => socket.send(JSON.stringify({ type: 'AUTH', token }));
    socket.onmessage = (e) => this.recibir(e.data);
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      // Un cierre de un socket que ya se descarto (latido sin respuesta) no debe reprogramar nada.
      if (this.socket !== socket) return;
      this.descartarSocket();
      this.estado.set('desconectado');
      this.programarReconexion();
    };
  }

  private recibir(datos: string): void {
    let evento: EventoMensajeria | { type: 'PONG' };
    try {
      evento = JSON.parse(datos);
    } catch {
      return;
    }
    clearTimeout(this.vigilancia);
    switch (evento.type) {
      case 'PONG':
        return;
      case 'LISTO':
        this.intento = 0;
        this.estado.set('conectado');
        this.iniciarLatido();
        if (this.ultimoId !== null) this.mandar({ type: 'SYNC', desdeId: this.ultimoId });
        break;
      case 'MENSAJE':
        this.registrarVistos([evento.mensaje.id]);
        this.acusar([evento.mensaje]);
        break;
      case 'SYNC':
        this.registrarVistos(evento.mensajes.map((m) => m.id));
        this.acusar(evento.mensajes);
        // Mas de una tanda pendiente (estuvo mucho tiempo fuera): se pide la siguiente.
        if (!evento.completo && this.ultimoId !== null) this.mandar({ type: 'SYNC', desdeId: this.ultimoId });
        break;
    }
    this.eventos$.next(evento);
  }

  /** Doble check gris para quien los escribio: llegaron a esta pantalla. */
  private acusar(mensajes: MensajeProveedor[]): void {
    const ids = mensajes.filter((m) => !m.mio && m.estado === 'ENVIADO').map((m) => m.id);
    if (ids.length) this.mandar({ type: 'ENTREGADO', ids });
  }

  private mandar(mensaje: object): void {
    try {
      this.socket?.send(JSON.stringify(mensaje));
    } catch {
      // Si el envio falla, el onclose se encarga de reconectar.
    }
  }

  private iniciarLatido(): void {
    clearInterval(this.latido);
    this.latido = setInterval(() => {
      this.mandar({ type: 'PING' });
      clearTimeout(this.vigilancia);
      this.vigilancia = setTimeout(() => this.conexionMuerta(), SIN_RESPUESTA_MS);
    }, LATIDO_MS);
  }

  /** El latido no tuvo respuesta: la conexion esta muerta aunque el navegador no lo sepa todavia. */
  private conexionMuerta(): void {
    if (!this.socket) return;
    this.descartarSocket();
    this.estado.set('desconectado');
    this.programarReconexion();
  }

  private reconectarYa(): void {
    if (this.usuarios === 0 || this.estado() === 'conectado') return;
    clearTimeout(this.reconexion);
    this.intento = 0;
    this.descartarSocket();
    this.abrir();
  }

  private programarReconexion(): void {
    if (this.usuarios === 0) return;
    clearTimeout(this.reconexion);
    this.reconexion = setTimeout(() => this.abrir(), esperaReconexion(this.intento++));
  }

  private descartarSocket(): void {
    clearInterval(this.latido);
    clearTimeout(this.vigilancia);
    const socket = this.socket;
    this.socket = null;
    if (socket) {
      socket.onclose = null;
      socket.onmessage = null;
      try {
        socket.close();
      } catch {
        // ya estaba cerrado
      }
    }
  }
}
