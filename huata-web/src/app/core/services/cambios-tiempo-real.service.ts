import { DestroyRef, Injectable, InjectionToken, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, fromEvent, timer } from 'rxjs';
import { buffer, debounceTime, filter, map, retry, share } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { EstadoSocket } from './canal-tiempo-real';

/** Módulos que difunde el backend (`ModulosDeCambio`). */
export type ModuloCambio =
  | 'acopio'
  | 'acopiadores'
  | 'calidad'
  | 'compras'
  | 'contabilidad'
  | 'inventario'
  | 'pagos'
  | 'parametros'
  | 'produccion'
  | 'proveedores'
  | 'rutas'
  | 'usuarios'
  | 'ventas';

/** Lo único que viaja: qué cambió. Los datos se vuelven a pedir por la API, con sus permisos. */
export interface CambioPublico {
  modulo: ModuloCambio;
  entidad: string;
  id: number;
  accion: 'CREADO' | 'ACTUALIZADO' | 'ELIMINADO';
}

type Mensaje = { type: 'LISTO' } | { type: 'PONG' } | { type: 'CAMBIOS'; cambios: CambioPublico[] };

/** Lo mínimo de un WebSocket: existe para que las pruebas usen uno falso. */
export interface SocketCambios {
  onopen: ((e: unknown) => void) | null;
  onmessage: ((e: { data: string }) => void) | null;
  onclose: ((e: unknown) => void) | null;
  onerror: ((e: unknown) => void) | null;
  send(datos: string): void;
  close(): void;
}

export const FABRICA_SOCKET_CAMBIOS = new InjectionToken<(url: string) => SocketCambios>('FABRICA_SOCKET_CAMBIOS', {
  providedIn: 'root',
  factory: () => (url: string) => new WebSocket(url) as unknown as SocketCambios,
});

export const LATIDO_CAMBIOS_MS = 25_000;
export const SIN_RESPUESTA_CAMBIOS_MS = 10_000;

/** Entidades cuyo cambio puede tocar los permisos de esta misma sesión. */
const ENTIDADES_DE_ACCESO = new Set(['Usuario', 'UsuarioPermiso', 'Rol', 'Vista', 'Modulo']);

/**
 * Canal de cambios (`/api/ws/cambios`): cuando alguien guarda algo en cualquier módulo, las pantallas
 * abiertas que lo muestran se recargan solas, en todos los navegadores a la vez.
 *
 * Una sola conexión para toda la sesión: la abre el shell y la comparten las pantallas. Se reconecta
 * sola; al volver, cada pantalla recarga una vez por lo que pudo perderse durante el corte.
 */
@Injectable({ providedIn: 'root' })
export class CambiosTiempoRealService {
  private readonly auth = inject(AuthService);
  private readonly abrirSocket = inject(FABRICA_SOCKET_CAMBIOS);
  readonly estado = signal<EstadoSocket>('desconectado');
  /** Emite al reconectar tras un corte: lo ocurrido mientras tanto no llegó por el canal. */
  private readonly reconectado$ = new Subject<void>();
  private conectadoAntes = false;

  readonly cambios$: Observable<CambioPublico[]> = new Observable<CambioPublico[]>((observer) => {
    const token = this.auth.token;
    if (!token) {
      observer.complete();
      return;
    }
    this.estado.set('conectando');
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = this.abrirSocket(`${protocolo}//${location.host}/api/ws/cambios`);
    let latido: ReturnType<typeof setInterval> | undefined;
    let vigilancia: ReturnType<typeof setTimeout> | undefined;
    const parar = () => {
      clearInterval(latido);
      clearTimeout(vigilancia);
    };
    socket.onopen = () => socket.send(JSON.stringify({ type: 'AUTH', token }));
    socket.onmessage = (evento) => {
      clearTimeout(vigilancia);
      let mensaje: Mensaje;
      try {
        mensaje = JSON.parse(evento.data) as Mensaje;
      } catch {
        return;
      }
      if (mensaje.type === 'LISTO') {
        this.estado.set('conectado');
        if (this.conectadoAntes) this.reconectado$.next();
        this.conectadoAntes = true;
        // Latido: un proxy o una red móvil pueden cortar en silencio una conexión que solo escucha.
        // Sin PONG a tiempo, la conexión se da por muerta y se reconecta.
        clearInterval(latido);
        latido = setInterval(() => {
          try {
            socket.send('{"type":"PING"}');
          } catch {
            // el cierre se encarga de reconectar
          }
          clearTimeout(vigilancia);
          vigilancia = setTimeout(() => {
            socket.close();
            socket.onclose?.({});
          }, SIN_RESPUESTA_CAMBIOS_MS);
        }, LATIDO_CAMBIOS_MS);
      } else if (mensaje.type === 'CAMBIOS') {
        if (mensaje.cambios.some((c) => ENTIDADES_DE_ACCESO.has(c.entidad))) this.auth.refrescarSesion();
        observer.next(mensaje.cambios);
      }
    };
    socket.onerror = () => socket.close();
    socket.onclose = () => {
      parar();
      socket.onclose = null;
      this.estado.set('desconectado');
      observer.error(new Error('Canal de cambios desconectado'));
    };
    return () => {
      parar();
      socket.onclose = null;
      socket.close();
      this.estado.set('desconectado');
    };
  }).pipe(
    // 2 s, 4 s, 8 s… hasta 30 s; vuelve a empezar tras recibir algo (resetOnSuccess).
    retry({ delay: (_, intento) => timer(Math.min(30_000, 1_000 * 2 ** Math.min(intento, 5))), resetOnSuccess: true }),
    // Cambiar de pantalla no debe cortar y reabrir el canal: espera unos segundos sin oyentes.
    share({ resetOnRefCountZero: () => timer(5_000) }),
  );

  /**
   * Los cambios de esos módulos, agrupados: varios guardados seguidos (un pago masivo) producen una
   * sola recarga. Con la pestaña oculta se esperan hasta que vuelva a verse.
   */
  en(modulos: readonly ModuloCambio[], espera = 600): Observable<CambioPublico[]> {
    const interesa = new Set<string>(modulos);
    const visible$ = fromEvent(document, 'visibilitychange').pipe(filter(() => !document.hidden));
    const reconexion$ = this.reconectado$.pipe(map(() => [] as CambioPublico[]));
    return new Observable<CambioPublico[]>((observer) => {
      const propios = this.cambios$.pipe(
        map((cambios) => cambios.filter((c) => interesa.has(c.modulo))),
        filter((cambios) => cambios.length > 0),
      );
      const pendientes: CambioPublico[] = [];
      const subs = [
        propios.pipe(buffer(propios.pipe(debounceTime(espera)))).subscribe((lotes) => {
          // buffer() también emite al terminar el canal (sin sesión): un lote vacío no es un cambio.
          pendientes.push(...lotes.flat());
          if (pendientes.length && !document.hidden) observer.next(pendientes.splice(0));
        }),
        visible$.subscribe(() => pendientes.length && observer.next(pendientes.splice(0))),
        reconexion$.subscribe((v) => observer.next(v)),
      ];
      return () => subs.forEach((s) => s.unsubscribe());
    });
  }
}

/**
 * Recarga la pantalla cuando cambia algo de esos módulos. Se llama en un inicializador de campo o en el
 * constructor (contexto de inyección) y se cancela sola al destruirse el componente:
 *
 * ```ts
 * private readonly _enVivo = recargarEnVivo(['pagos'], () => this.cargarPlanilla());
 * ```
 */
export function recargarEnVivo(modulos: readonly ModuloCambio[], recargar: (cambios: CambioPublico[]) => void): void {
  inject(CambiosTiempoRealService)
    .en(modulos)
    .pipe(takeUntilDestroyed(inject(DestroyRef)))
    .subscribe((cambios) => recargar(cambios));
}
