import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import {
  CambioPublico,
  CambiosTiempoRealService,
  FABRICA_SOCKET_CAMBIOS,
  LATIDO_CAMBIOS_MS,
  SIN_RESPUESTA_CAMBIOS_MS,
  SocketCambios,
} from './cambios-tiempo-real.service';

class SocketFalso implements SocketCambios {
  enviados: any[] = [];
  cerrado = false;
  onopen: ((e: unknown) => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: ((e: unknown) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  constructor(readonly url: string) {}
  send(datos: string) {
    this.enviados.push(JSON.parse(datos));
  }
  close() {
    this.cerrado = true;
  }
  abrir() {
    this.onopen?.({});
    this.recibir({ type: 'LISTO' });
  }
  recibir(evento: object) {
    this.onmessage?.({ data: JSON.stringify(evento) });
  }
  caer() {
    this.onclose?.({});
  }
}

const cambio = (modulo: string, entidad: string, id: number) => ({ modulo, entidad, id, accion: 'ACTUALIZADO' });

function crear() {
  const sockets: SocketFalso[] = [];
  const refrescarSesion = vi.fn();
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: AuthService, useValue: { token: 'tok', refrescarSesion } },
      {
        provide: FABRICA_SOCKET_CAMBIOS,
        useValue: (url: string) => {
          const s = new SocketFalso(url);
          sockets.push(s);
          return s;
        },
      },
    ],
  });
  const servicio = TestBed.inject(CambiosTiempoRealService);
  return { servicio, sockets, refrescarSesion, ultimo: () => sockets[sockets.length - 1] };
}

describe('CambiosTiempoRealService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('se presenta con el token en /api/ws/cambios y queda en vivo al recibir LISTO', () => {
    const { servicio, ultimo } = crear();
    const sub = servicio.cambios$.subscribe();
    ultimo().abrir();

    expect(ultimo().url).toContain('/api/ws/cambios');
    expect(ultimo().enviados[0]).toEqual({ type: 'AUTH', token: 'tok' });
    expect(servicio.estado()).toBe('conectado');
    sub.unsubscribe();
  });

  it('una pantalla recibe solo sus módulos, y varios guardados seguidos son una sola recarga', () => {
    const { servicio, ultimo } = crear();
    const recibidos: CambioPublico[][] = [];
    const sub = servicio.en(['pagos']).subscribe((c) => recibidos.push(c));
    ultimo().abrir();

    ultimo().recibir({ type: 'CAMBIOS', cambios: [cambio('pagos', 'Liquidacion', 1), cambio('rutas', 'Ruta', 7)] });
    vi.advanceTimersByTime(200);
    ultimo().recibir({ type: 'CAMBIOS', cambios: [cambio('pagos', 'Liquidacion', 2)] });
    vi.advanceTimersByTime(600);

    expect(recibidos.length).toBe(1);
    expect(recibidos[0].map((c) => c.id)).toEqual([1, 2]);
    sub.unsubscribe();
  });

  it('sin cambios de su módulo, la pantalla no recarga', () => {
    const { servicio, ultimo } = crear();
    const recarga = vi.fn();
    const sub = servicio.en(['ventas']).subscribe(recarga);
    ultimo().abrir();
    ultimo().recibir({ type: 'CAMBIOS', cambios: [cambio('acopio', 'Recojo', 3)] });
    vi.advanceTimersByTime(1_000);

    expect(recarga).not.toHaveBeenCalled();
    sub.unsubscribe();
  });

  it('si cambian roles o permisos, relee la sesión (aparecen o se van secciones)', () => {
    const { servicio, ultimo, refrescarSesion } = crear();
    const sub = servicio.cambios$.subscribe();
    ultimo().abrir();
    ultimo().recibir({ type: 'CAMBIOS', cambios: [cambio('usuarios', 'UsuarioPermiso', 4)] });

    expect(refrescarSesion).toHaveBeenCalledTimes(1);
    sub.unsubscribe();
  });

  it('se reconecta tras un corte y entonces pide una recarga por lo que se perdió', () => {
    const { servicio, sockets, ultimo } = crear();
    const recibidos: CambioPublico[][] = [];
    const sub = servicio.en(['acopio']).subscribe((c) => recibidos.push(c));
    ultimo().abrir();
    expect(recibidos.length).toBe(0);

    ultimo().caer();
    expect(servicio.estado()).toBe('desconectado');
    vi.advanceTimersByTime(2_000);
    expect(sockets.length).toBe(2);
    ultimo().abrir();

    expect(recibidos).toEqual([[]]);
    sub.unsubscribe();
  });

  it('si el latido no tiene respuesta da la conexión por muerta y reconecta', () => {
    const { servicio, sockets, ultimo } = crear();
    const sub = servicio.cambios$.subscribe();
    ultimo().abrir();

    vi.advanceTimersByTime(LATIDO_CAMBIOS_MS);
    expect(ultimo().enviados).toContainEqual({ type: 'PING' });
    vi.advanceTimersByTime(SIN_RESPUESTA_CAMBIOS_MS);

    expect(sockets[0].cerrado).toBe(true);
    vi.advanceTimersByTime(2_000);
    expect(sockets.length).toBe(2);
    sub.unsubscribe();
  });

  it('cambiar de pantalla no corta el canal; sin nadie escuchando, se cierra a los segundos', () => {
    const { servicio, sockets, ultimo } = crear();
    const a = servicio.cambios$.subscribe();
    ultimo().abrir();
    a.unsubscribe();
    const b = servicio.cambios$.subscribe();
    vi.advanceTimersByTime(1_000);
    expect(sockets.length).toBe(1);
    expect(sockets[0].cerrado).toBe(false);

    b.unsubscribe();
    vi.advanceTimersByTime(5_000);
    expect(sockets[0].cerrado).toBe(true);
  });
});
