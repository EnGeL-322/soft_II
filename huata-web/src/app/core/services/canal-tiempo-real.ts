import { Observable, Subject, timer } from 'rxjs';
import { retry, share, takeUntil } from 'rxjs/operators';

export type EstadoSocket = 'conectando' | 'conectado' | 'desconectado';

/**
 * Abre un canal WebSocket de solo lectura contra el backend y emite cada mensaje ya parseado.
 *
 * El backend declara `/api/ws/**` sin filtro de seguridad HTTP (el handshake no lleva cabecera
 * `Authorization`), así que el primer mensaje que se envía es el token: es la única puerta del canal,
 * y el servidor cierra la sesión si no autoriza. Ver `CanalTiempoReal` en el backend.
 *
 * Se reconecta sola con espera fija: un corte de red no debe dejar la pantalla muda hasta que alguien
 * la recargue. `hasta$` la cierra cuando el componente se destruye.
 */
export function abrirCanal<T>(ruta: string, token: string | null, hasta$: Subject<void>, estado: (e: EstadoSocket) => void): Observable<T> {
  return new Observable<T>((observer) => {
    if (!token) {
      observer.complete();
      return;
    }
    estado('conectando');
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocolo}//${location.host}${ruta}`);
    socket.onopen = () => {
      socket.send(JSON.stringify({ type: 'AUTH', token }));
      estado('conectado');
    };
    socket.onmessage = (evento) => {
      try {
        observer.next(JSON.parse(evento.data) as T);
      } catch {
        // Se ignoran mensajes que no cumplan el contrato del canal.
      }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      estado('desconectado');
      // error() y no complete(): es lo que activa el retry de abajo. Un complete() dejaría el canal
      // cerrado para siempre tras el primer corte.
      observer.error(new Error('WebSocket desconectado'));
    };
    return () => socket.close();
  }).pipe(
    retry({ delay: () => timer(3_000) }),
    takeUntil(hasta$),
    share(),
  );
}
