import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { MensajeProveedor, MensajeriaService } from './mensajeria.service';
import { MensajeriaSalidaService, esErrorDefinitivo, nuevoUuid } from './mensajeria-salida.service';

interface Llamada { productorId: number; cuerpo: string; clientUuid: string; respuesta: Subject<MensajeProveedor>; }

function crear() {
  const llamadas: Llamada[] = [];
  const api = {
    enviar: (productorId: number, cuerpo: string, clientUuid: string) => {
      const respuesta = new Subject<MensajeProveedor>();
      llamadas.push({ productorId, cuerpo, clientUuid, respuesta });
      return respuesta;
    },
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({ providers: [{ provide: MensajeriaService, useValue: api }] });
  return { salida: TestBed.inject(MensajeriaSalidaService), llamadas };
}

function confirmado(l: Llamada, id: number): MensajeProveedor {
  return { id, autorId: 1, autorNombre: 'Secretaría', mio: true, cuerpo: l.cuerpo, creadoEn: '', leidoEn: null, productorId: l.productorId, clientUuid: l.clientUuid, estado: 'ENVIADO' };
}

function responder(l: Llamada, id: number) {
  l.respuesta.next(confirmado(l, id));
  l.respuesta.complete();
}

function fallar(l: Llamada, status: number) {
  l.respuesta.error(new HttpErrorResponse({ status, error: { message: 'Rechazado' } }));
}

describe('MensajeriaSalidaService', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('muestra el mensaje al instante y lo quita de la cola cuando el servidor lo confirma', () => {
    const { salida, llamadas } = crear();
    const confirmados: MensajeProveedor[] = [];
    salida.confirmados$.subscribe((m) => confirmados.push(m));

    const m = salida.encolar(5, 'Hola');

    expect(salida.cola()).toEqual([expect.objectContaining({ cuerpo: 'Hola', estado: 'PENDIENTE' })]);
    expect(llamadas[0].clientUuid).toBe(m.clientUuid);
    responder(llamadas[0], 100);
    expect(salida.cola()).toEqual([]);
    expect(confirmados.map((c) => c.id)).toEqual([100]);
  });

  it('manda de a uno y en el orden en que se escribio', () => {
    const { salida, llamadas } = crear();
    salida.encolar(5, 'primero');
    salida.encolar(5, 'segundo');

    expect(llamadas.map((l) => l.cuerpo)).toEqual(['primero']);
    responder(llamadas[0], 1);
    expect(llamadas.map((l) => l.cuerpo)).toEqual(['primero', 'segundo']);
  });

  it('sin red el mensaje sigue pendiente y se reintenta con el mismo clientUuid', () => {
    const { salida, llamadas } = crear();
    salida.encolar(5, 'sin señal');

    fallar(llamadas[0], 0);
    expect(salida.cola()[0].estado).toBe('PENDIENTE');
    vi.advanceTimersByTime(1000);

    expect(llamadas.length).toBe(2);
    expect(llamadas[1].clientUuid).toBe(llamadas[0].clientUuid);
    responder(llamadas[1], 7);
    expect(salida.cola()).toEqual([]);
  });

  it('un rechazo definitivo queda como error y no frena a los siguientes', () => {
    const { salida, llamadas } = crear();
    salida.encolar(5, 'malo');
    salida.encolar(5, 'bueno');

    fallar(llamadas[0], 422);

    expect(salida.cola()[0]).toEqual(expect.objectContaining({ cuerpo: 'malo', estado: 'ERROR', error: 'Rechazado' }));
    expect(llamadas[1].cuerpo).toBe('bueno');
  });

  it('un error se puede reintentar a mano o eliminar', () => {
    const { salida, llamadas } = crear();
    const m = salida.encolar(5, 'malo');
    fallar(llamadas[0], 400);

    salida.reintentar(m.clientUuid);
    expect(llamadas.length).toBe(2);
    fallar(llamadas[1], 403);

    salida.eliminar(m.clientUuid);
    expect(salida.cola()).toEqual([]);
  });

  it('si el mensaje llega primero por el canal, la respuesta del POST no lo duplica en la cola', () => {
    const { salida, llamadas } = crear();
    const m = salida.encolar(5, 'eco');

    salida.confirmar(m.clientUuid);
    responder(llamadas[0], 9);

    expect(salida.cola()).toEqual([]);
  });

  it('solo los rechazos de validacion o permiso son definitivos', () => {
    expect([400, 403, 404, 422].every(esErrorDefinitivo)).toBe(true);
    expect([0, 401, 409, 429, 500, 503].some(esErrorDefinitivo)).toBe(false);
  });

  it('genera uuid v4 aunque crypto.randomUUID no exista (http sin TLS)', () => {
    const original = crypto.randomUUID;
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true });
    try {
      expect(nuevoUuid()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    } finally {
      Object.defineProperty(crypto, 'randomUUID', { value: original, configurable: true });
    }
  });
});
