import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { SIN_CONEXION, erroresDeCampo, mensajeDeError } from '../../core/notificaciones/mensaje-error';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { DialogoHostComponent } from './dialogo-host.component';
import { NotificacionesHostComponent } from './notificaciones-host.component';

describe('NotificacionesService', () => {
  let s: NotificacionesService;
  beforeEach(() => {
    vi.useFakeTimers();
    s = new NotificacionesService();
  });
  afterEach(() => vi.useRealTimers());

  it('muestra la notificación y la cierra sola tras unos segundos', () => {
    s.exito('Registro guardado correctamente.');
    expect(s.notificaciones()).toHaveLength(1);
    vi.advanceTimersByTime(4000);
    expect(s.notificaciones()).toHaveLength(0);
  });

  it('los errores quedan visibles más tiempo que los éxitos', () => {
    s.exito('Listo');
    s.error('No se pudo guardar.');
    vi.advanceTimersByTime(4000);
    expect(s.notificaciones().map((n) => n.tipo)).toEqual(['error']);
    vi.advanceTimersByTime(5000);
    expect(s.notificaciones()).toHaveLength(0);
  });

  it('con duración 0 queda hasta que se cierre a mano', () => {
    s.error('Sin conexión', { duracion: 0 });
    vi.advanceTimersByTime(60000);
    const [n] = s.notificaciones();
    expect(n).toBeDefined();
    s.cerrar(n.id);
    expect(s.notificaciones()).toHaveLength(0);
  });

  it('no apila mensajes idénticos: cuenta las repeticiones', () => {
    s.error(SIN_CONEXION);
    s.error(SIN_CONEXION);
    s.error(SIN_CONEXION);
    expect(s.notificaciones()).toHaveLength(1);
    expect(s.notificaciones()[0].repeticiones).toBe(3);
  });

  it('nunca muestra más de 4 a la vez', () => {
    for (let i = 1; i <= 6; i++) s.info('Mensaje ' + i);
    expect(s.notificaciones().map((n) => n.mensaje)).toEqual(['Mensaje 3', 'Mensaje 4', 'Mensaje 5', 'Mensaje 6']);
  });

  it('ignora mensajes vacíos', () => {
    s.exito('  ');
    expect(s.notificaciones()).toHaveLength(0);
  });
});

describe('DialogoService', () => {
  it('resuelve true al aceptar y false al cancelar', async () => {
    const d = new DialogoService();
    const si = d.confirmar({ titulo: '¿Eliminar?' });
    d.responder(true);
    expect(await si).toBe(true);
    const no = d.confirmar({ titulo: '¿Eliminar?' });
    d.responder(false);
    expect(await no).toBe(false);
  });

  it('pedirTexto devuelve el texto o null si se cancela', async () => {
    const d = new DialogoService();
    const motivo = d.pedirTexto({ titulo: 'Anular', etiqueta: 'Motivo' });
    d.responder('Error de digitación');
    expect(await motivo).toBe('Error de digitación');
    const cancelado = d.pedirTexto({ titulo: 'Anular', etiqueta: 'Motivo' });
    d.responder(null);
    expect(await cancelado).toBeNull();
  });

  it('muestra una ventana a la vez y encola las siguientes', async () => {
    const d = new DialogoService();
    const a = d.confirmar({ titulo: 'Primera' });
    const b = d.confirmar({ titulo: 'Segunda' });
    expect(d.actual()?.opciones.titulo).toBe('Primera');
    d.responder(true);
    expect(d.actual()?.opciones.titulo).toBe('Segunda');
    d.responder(false);
    expect([await a, await b]).toEqual([true, false]);
    expect(d.actual()).toBeNull();
  });
});

describe('DialogoHostComponent', () => {
  function crear() {
    const fixture = TestBed.createComponent(DialogoHostComponent);
    const dialogo = TestBed.inject(DialogoService);
    return { fixture, dialogo, el: fixture.nativeElement as HTMLElement };
  }
  const tecla = (key: string) => document.dispatchEvent(new KeyboardEvent('keydown', { key }));

  it('muestra título, mensaje y botones; el destructivo se distingue', async () => {
    const { fixture, dialogo, el } = crear();
    const resultado = dialogo.confirmar({
      titulo: '¿Eliminar este registro?',
      mensaje: 'Esta acción no se puede deshacer.',
      confirmar: 'Eliminar',
      tipo: 'peligro',
    });
    fixture.detectChanges();
    expect(el.querySelector('[role="alertdialog"]')?.getAttribute('aria-modal')).toBe('true');
    expect(el.querySelector('h2')?.textContent).toContain('¿Eliminar este registro?');
    expect(el.querySelector('.caja')?.classList).toContain('caja-peligro');
    (el.querySelector('.btn-confirmar') as HTMLButtonElement).click();
    expect(await resultado).toBe(true);
  });

  it('Escape cancela sin ejecutar la acción', async () => {
    const { fixture, dialogo } = crear();
    const resultado = dialogo.confirmar({ titulo: '¿Pagar?' });
    fixture.detectChanges();
    tecla('Escape');
    expect(await resultado).toBe(false);
  });

  it('en una acción peligrosa el clic fuera no cierra la ventana', () => {
    const { fixture, dialogo, el } = crear();
    dialogo.confirmar({ titulo: '¿Eliminar?', tipo: 'peligro' });
    fixture.detectChanges();
    const fondo = el.querySelector('.fondo') as HTMLElement;
    fondo.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    expect(dialogo.actual()).not.toBeNull();
  });

  it('en una confirmación normal el clic fuera cancela', async () => {
    const { fixture, dialogo, el } = crear();
    const resultado = dialogo.confirmar({ titulo: '¿Continuar?' });
    fixture.detectChanges();
    (el.querySelector('.fondo') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    expect(await resultado).toBe(false);
  });

  it('si el texto es obligatorio no deja aceptar vacío', async () => {
    const { fixture, dialogo, el } = crear();
    const resultado = dialogo.pedirTexto({ titulo: 'Rechazar', etiqueta: 'Motivo', obligatorio: true });
    fixture.detectChanges();
    await fixture.whenStable();
    const aceptar = el.querySelector('.btn-confirmar') as HTMLButtonElement;
    expect(aceptar.disabled).toBe(true);
    const area = el.querySelector('textarea') as HTMLTextAreaElement;
    area.value = 'Zona equivocada';
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(aceptar.disabled).toBe(false);
    aceptar.click();
    expect(await resultado).toBe('Zona equivocada');
  });
});

describe('NotificacionesHostComponent', () => {
  it('pinta cada notificación con su tipo y permite cerrarla', () => {
    const fixture = TestBed.createComponent(NotificacionesHostComponent);
    const s = TestBed.inject(NotificacionesService);
    s.error('No se pudo completar la operación.');
    fixture.detectChanges();
    const toast = fixture.nativeElement.querySelector('.toast') as HTMLElement;
    expect(toast.classList).toContain('toast-error');
    expect(toast.getAttribute('role')).toBe('alert');
    (toast.querySelector('.cerrar') as HTMLButtonElement).click();
    expect(s.notificaciones()).toHaveLength(0);
  });
});

describe('mensajeDeError', () => {
  const http = (status: number, body: unknown = null) => new HttpErrorResponse({ status, error: body });

  it('traduce la falta de conexión y el tiempo agotado', () => {
    expect(mensajeDeError(http(0))).toBe(SIN_CONEXION);
    expect(mensajeDeError(http(504))).toBe(SIN_CONEXION);
  });

  it('usa el mensaje del backend en errores de validación o de negocio', () => {
    expect(mensajeDeError(http(422, { message: 'La zona ya existe.' }))).toBe('La zona ya existe.');
  });

  it('nunca muestra textos técnicos de un error 500', () => {
    expect(mensajeDeError(http(500, { message: 'NullPointerException at ...' }), 'No se pudo guardar.')).toBe(
      'No se pudo guardar.',
    );
  });

  it('explica la falta de permiso', () => {
    expect(mensajeDeError(http(403))).toContain('permiso');
  });

  it('lee los errores por campo del backend', () => {
    expect(erroresDeCampo(http(400, { fields: { dni: 'Debe tener 8 dígitos' } }))).toEqual({
      dni: 'Debe tener 8 dígitos',
    });
    expect(erroresDeCampo(http(400, { message: 'x' }))).toEqual({});
  });
});
