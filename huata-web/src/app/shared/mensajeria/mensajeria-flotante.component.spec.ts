import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, of } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { ConversacionDetalle, ConversacionProveedor, MensajeProveedor, MensajeriaService } from '../../core/services/mensajeria.service';
import { EventoMensajeria, MensajeriaTiempoRealService } from '../../core/services/mensajeria-tiempo-real.service';
import { MensajeriaFlotanteComponent } from './mensajeria-flotante.component';

const conversacion = (productorId: number, mensajesSinLeer = 0): ConversacionProveedor => ({
  productorId, productorCodigo: `P${productorId}`, productorNombre: `Proveedor ${productorId}`, zona: 'FAON', telefono: null,
  mensajesSinLeer, ultimoMensajeAt: null, ultimoMensaje: null,
});

const mensaje = (id: number, extra: Partial<MensajeProveedor> = {}): MensajeProveedor => ({
  id, autorId: 9, autorNombre: 'Proveedor 5', mio: false, cuerpo: `m${id}`, creadoEn: '2026-10-01T10:00:00Z',
  leidoEn: null, productorId: 5, clientUuid: null, estado: 'ENVIADO', ...extra,
});

function crear(permisos: string[] = ['mensajeria.read', 'mensajeria.write'], roles: string[] = ['ADMINISTRADOR']) {
  const eventos$ = new Subject<EventoMensajeria>();
  const respuestas: Subject<MensajeProveedor>[] = [];
  const vivo = { estado: signal<'conectado' | 'desconectado' | 'conectando'>('conectado'), eventos$, iniciar: vi.fn(), detener: vi.fn(), registrarVistos: vi.fn() };
  const api = {
    listar: vi.fn(() => of([conversacion(5), conversacion(6)])),
    detalle: vi.fn((id: number): ReturnType<MensajeriaService['detalle']> => of<ConversacionDetalle>({ conversacion: conversacion(id), mensajes: [mensaje(1)] })),
    enviar: vi.fn(() => { const r = new Subject<MensajeProveedor>(); respuestas.push(r); return r; }),
  };
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    imports: [MensajeriaFlotanteComponent],
    providers: [
      { provide: AuthService, useValue: { puede: (p: string) => permisos.includes(p), tieneRol: (r: string) => roles.includes(r) } },
      { provide: MensajeriaService, useValue: api },
      { provide: MensajeriaTiempoRealService, useValue: vivo },
    ],
  });
  const fixture = TestBed.createComponent(MensajeriaFlotanteComponent);
  fixture.detectChanges();
  const c = fixture.componentInstance;
  return { fixture, c, eventos$, respuestas, vivo, api };
}

describe('MensajeriaFlotanteComponent en vivo', () => {
  it('abre el canal al poder usar la mensajeria', () => {
    const { vivo } = crear();
    expect(vivo.iniciar).toHaveBeenCalledTimes(1);
  });

  it('lo escrito se ve al instante con reloj y no se duplica aunque el eco del canal llegue antes que la respuesta', () => {
    const { c, fixture, eventos$, respuestas } = crear();
    c.abrir();
    c.texto = 'Mañana paso a las 6';
    c.enviar();
    fixture.detectChanges();

    expect(c.enCola().map((m) => m.estado)).toEqual(['PENDIENTE']);
    expect(fixture.nativeElement.textContent).toContain('🕓');
    const uuid = c.enCola()[0].clientUuid;

    const confirmado = mensaje(50, { mio: true, cuerpo: 'Mañana paso a las 6', clientUuid: uuid, autorNombre: 'Secretaría' });
    eventos$.next({ type: 'MENSAJE', mensaje: confirmado });
    respuestas[0].next(confirmado);
    respuestas[0].complete();

    expect(c.enCola()).toEqual([]);
    expect(c.seleccionada()!.mensajes.filter((m) => m.id === 50)).toHaveLength(1);
  });

  it('un mensaje del proveedor llega en vivo a la conversacion abierta', () => {
    const { c, eventos$ } = crear();
    c.abrir();

    eventos$.next({ type: 'MENSAJE', mensaje: mensaje(7, { cuerpo: '¿Cuándo pagan?' }) });

    expect(c.seleccionada()!.mensajes.map((m) => m.cuerpo)).toContain('¿Cuándo pagan?');
  });

  it('con el panel cerrado, lo del proveedor sube "sin leer" y un aviso de otra persona de la planta no', () => {
    const { c, eventos$ } = crear();

    eventos$.next({ type: 'MENSAJE', mensaje: mensaje(8, { productorId: 6 }) });
    eventos$.next({ type: 'MENSAJE', mensaje: mensaje(9, { productorId: 6, autorNombre: 'Administrador', leidoEn: '2026-10-01T10:00:00Z' }) });

    expect(c.conversaciones().find((x) => x.productorId === 6)!.mensajesSinLeer).toBe(1);
  });

  it('los checks cambian cuando llega el estado', () => {
    const { c, eventos$ } = crear();
    c.abrir();

    eventos$.next({ type: 'ESTADO', productorId: 5, ids: [1], estado: 'LEIDO', en: '2026-10-01T10:00:00Z' });

    expect(c.seleccionada()!.mensajes.find((m) => m.id === 1)!.estado).toBe('LEIDO');
  });

  /** Sin layout real (jsdom), se simula una lista alta y se observa a dónde se mueve el scroll. */
  function listaAlta(fixture: ReturnType<typeof crear>['fixture']) {
    const el: HTMLElement = fixture.nativeElement.querySelector('.messages');
    const estado = { posicion: 0 };
    Object.defineProperty(el, 'scrollHeight', { configurable: true, get: () => 900 });
    Object.defineProperty(el, 'clientHeight', { configurable: true, get: () => 300 });
    Object.defineProperty(el, 'scrollTop', { configurable: true, get: () => estado.posicion, set: (v: number) => { estado.posicion = v; } });
    return { el, estado };
  }

  it('al enviar baja al ultimo mensaje aunque la persona estuviera leyendo arriba', () => {
    const { c, fixture } = crear();
    c.abrir();
    fixture.detectChanges();
    TestBed.tick();
    const { estado } = listaAlta(fixture);
    estado.posicion = 0;
    c.alDesplazar();

    c.texto = 'Hola';
    c.enviar();
    fixture.detectChanges();
    TestBed.tick();

    expect(estado.posicion).toBe(900);
  });

  it('un mensaje que llega no mueve a quien esta leyendo mensajes viejos', () => {
    const { c, fixture, eventos$ } = crear();
    c.abrir();
    fixture.detectChanges();
    TestBed.tick();
    const { estado } = listaAlta(fixture);
    estado.posicion = 0;
    c.alDesplazar();

    eventos$.next({ type: 'MENSAJE', mensaje: mensaje(70) });
    fixture.detectChanges();
    TestBed.tick();

    expect(estado.posicion).toBe(0);
  });

  it('sin conexion avisa que los mensajes se enviaran cuando haya señal', () => {
    const { c, fixture, vivo } = crear();
    c.abrir();
    vivo.estado.set('desconectado');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin conexión: los mensajes se enviarán cuando haya señal');
  });
});

describe('MensajeriaFlotanteComponent por permiso', () => {
  it('se habilita para cualquier rol al que se le conceda mensajeria.read', () => {
    const { c } = crear(['mensajeria.read', 'mensajeria.write'], ['CALIDAD']);
    expect(c.puedeUsar()).toBe(true);
    expect(c.puedeEnviar()).toBe(true);
  });

  it('con solo lectura no ofrece enviar', () => {
    const { c } = crear(['mensajeria.read'], ['GERENCIA']);
    expect(c.puedeUsar()).toBe(true);
    expect(c.puedeEnviar()).toBe(false);
  });

  it('el proveedor no ve la bandeja de planta aunque tenga el permiso', () => {
    const { c, vivo } = crear(['mensajeria.read', 'mensajeria.write'], ['PROVEEDOR']);
    expect(c.puedeUsar()).toBe(false);
    expect(vivo.iniciar).not.toHaveBeenCalled();
  });
});
