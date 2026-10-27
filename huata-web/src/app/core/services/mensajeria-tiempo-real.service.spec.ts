import { TestBed } from '@angular/core/testing';
import { AuthService } from './auth.service';
import { MensajeProveedor } from './mensajeria.service';
import {
  EventoMensajeria,
  FABRICA_SOCKET_MENSAJERIA,
  LATIDO_MS,
  MensajeriaTiempoRealService,
  SIN_RESPUESTA_MS,
  SocketMensajeria,
  esperaReconexion,
} from './mensajeria-tiempo-real.service';

class SocketFalso implements SocketMensajeria {
  enviados: any[] = [];
  cerrado = false;
  onopen: ((e: unknown) => void) | null = null;
  onmessage: ((e: { data: string }) => void) | null = null;
  onclose: ((e: unknown) => void) | null = null;
  onerror: ((e: unknown) => void) | null = null;
  constructor(readonly url: string) {}
  send(datos: string) { this.enviados.push(JSON.parse(datos)); }
  close() { this.cerrado = true; }
  abrir() { this.onopen?.({}); }
  recibir(evento: object) { this.onmessage?.({ data: JSON.stringify(evento) }); }
  caer() { this.onclose?.({}); }
}

function mensaje(id: number, extra: Partial<MensajeProveedor> = {}): MensajeProveedor {
  return { id, autorId: 9, autorNombre: 'Proveedor', mio: false, cuerpo: `m${id}`, creadoEn: '2026-10-01T10:00:00Z', leidoEn: null, productorId: 5, clientUuid: null, estado: 'ENVIADO', ...extra };
}

function crear() {
  const sockets: SocketFalso[] = [];
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      { provide: AuthService, useValue: { token: 'tok' } },
      { provide: FABRICA_SOCKET_MENSAJERIA, useValue: (url: string) => { const s = new SocketFalso(url); sockets.push(s); return s; } },
    ],
  });
  const servicio = TestBed.inject(MensajeriaTiempoRealService);
  const eventos: EventoMensajeria[] = [];
  servicio.eventos$.subscribe((e) => eventos.push(e));
  return { servicio, sockets, eventos, ultimo: () => sockets[sockets.length - 1] };
}

function conectar(s: SocketFalso) {
  s.abrir();
  s.recibir({ type: 'LISTO', usuarioId: 1, planta: true, productorId: null });
}

describe('MensajeriaTiempoRealService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('se presenta con el token y queda en vivo al recibir LISTO', () => {
    const { servicio, ultimo } = crear();
    servicio.iniciar();

    expect(ultimo().url).toContain('/api/ws/mensajeria');
    conectar(ultimo());

    expect(ultimo().enviados[0]).toEqual({ type: 'AUTH', token: 'tok' });
    expect(servicio.estado()).toBe('conectado');
    servicio.detener();
  });

  it('confirma como entregado lo que le llega del otro lado, no lo propio', () => {
    const { servicio, ultimo, eventos } = crear();
    servicio.iniciar();
    conectar(ultimo());

    ultimo().recibir({ type: 'MENSAJE', mensaje: mensaje(10) });
    ultimo().recibir({ type: 'MENSAJE', mensaje: mensaje(11, { mio: true }) });

    expect(ultimo().enviados.filter((m) => m.type === 'ENTREGADO')).toEqual([{ type: 'ENTREGADO', ids: [10] }]);
    expect(eventos.filter((e) => e.type === 'MENSAJE').length).toBe(2);
    servicio.detener();
  });

  it('se reconecta con espera creciente y pide lo perdido desde el ultimo id visto', () => {
    const { servicio, sockets, ultimo } = crear();
    servicio.iniciar();
    conectar(ultimo());
    ultimo().recibir({ type: 'MENSAJE', mensaje: mensaje(42) });

    ultimo().caer();
    expect(servicio.estado()).toBe('desconectado');
    expect(sockets.length).toBe(1);

    vi.advanceTimersByTime(esperaReconexion(0));
    expect(sockets.length).toBe(2);
    ultimo().caer();
    vi.advanceTimersByTime(esperaReconexion(1) - 1);
    expect(sockets.length).toBe(2); // 2 s la segunda vez, no 1 s
    vi.advanceTimersByTime(1);
    expect(sockets.length).toBe(3);

    conectar(ultimo());
    expect(ultimo().enviados).toContainEqual({ type: 'SYNC', desdeId: 42 });
    servicio.detener();
  });

  it('la espera de reconexion no pasa de 30 segundos', () => {
    expect([0, 1, 2, 3].map(esperaReconexion)).toEqual([1000, 2000, 4000, 8000]);
    expect(esperaReconexion(10)).toBe(30_000);
  });

  it('si el latido no tiene respuesta da la conexion por muerta y reconecta', () => {
    const { servicio, sockets, ultimo } = crear();
    servicio.iniciar();
    conectar(ultimo());

    vi.advanceTimersByTime(LATIDO_MS);
    expect(ultimo().enviados).toContainEqual({ type: 'PING' });
    vi.advanceTimersByTime(SIN_RESPUESTA_MS);

    expect(servicio.estado()).toBe('desconectado');
    expect(sockets[0].cerrado).toBe(true);
    vi.advanceTimersByTime(esperaReconexion(0));
    expect(sockets.length).toBe(2);
    servicio.detener();
  });

  it('con PONG a tiempo la conexion sigue viva', () => {
    const { servicio, sockets, ultimo } = crear();
    servicio.iniciar();
    conectar(ultimo());

    vi.advanceTimersByTime(LATIDO_MS);
    ultimo().recibir({ type: 'PONG' });
    vi.advanceTimersByTime(SIN_RESPUESTA_MS);

    expect(servicio.estado()).toBe('conectado');
    expect(sockets.length).toBe(1);
    servicio.detener();
  });

  it('pide la siguiente tanda si el SYNC no vino completo', () => {
    const { servicio, ultimo } = crear();
    servicio.iniciar();
    servicio.registrarVistos([1]);
    conectar(ultimo());

    ultimo().recibir({ type: 'SYNC', mensajes: [mensaje(2), mensaje(3)], completo: false });

    expect(ultimo().enviados).toContainEqual({ type: 'SYNC', desdeId: 3 });
    expect(ultimo().enviados).toContainEqual({ type: 'ENTREGADO', ids: [2, 3] });
    servicio.detener();
  });

  it('al detenerse no reconecta', () => {
    const { servicio, sockets, ultimo } = crear();
    servicio.iniciar();
    conectar(ultimo());

    servicio.detener();
    vi.advanceTimersByTime(60_000);

    expect(sockets.length).toBe(1);
    expect(servicio.estado()).toBe('desconectado');
  });
});
