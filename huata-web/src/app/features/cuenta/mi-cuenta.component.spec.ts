import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { API_BASE_URL } from '../../core/config/api-config';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { MiCuentaComponent, fuerzaClave } from './mi-cuenta.component';

describe('Mi cuenta', () => {
  let http: HttpTestingController;

  function crear() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    // Confirma sin mostrar la ventana.
    TestBed.inject(DialogoService).confirmar = () => Promise.resolve(true);
    const fixture = TestBed.createComponent(MiCuentaComponent);
    fixture.detectChanges();
    http.expectOne(`${API_BASE_URL}/cuenta`).flush({
      nombreCompleto: 'Juan Pérez',
      username: 'juan',
      email: 'juan',
      telefono: null,
      roles: ['Proveedor'],
      puedeEditarNombre: false,
    });
    fixture.detectChanges();
    return { fixture, c: fixture.componentInstance };
  }

  afterEach(() => http?.verify());

  it('mide la fuerza de la contraseña', () => {
    expect(fuerzaClave('')).toBe(0);
    expect(fuerzaClave('Ab1!')).toBe(1);
    expect(fuerzaClave('abcdefg1')).toBe(2);
    expect(fuerzaClave('Abcdef1!')).toBe(3);
  });

  it('el proveedor ve su nombre bloqueado', async () => {
    const { fixture } = crear();
    // ngModel aplica "disabled" en la siguiente vuelta.
    await fixture.whenStable();
    fixture.detectChanges();
    const nombre: HTMLInputElement = fixture.nativeElement.querySelector('#mc-nombre');
    expect(nombre.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('lo corrige la planta');
  });

  it('no envía una contraseña que no coincide con su confirmación', async () => {
    const { c } = crear();
    c.claveActual = 'Actual123';
    c.claveNueva = 'Nueva12345';
    c.claveRepetida = 'Otra12345';
    await c.cambiarClave();
    expect(c.errorClave()).toBe('La confirmación no coincide.');
    http.expectNone(`${API_BASE_URL}/cuenta/clave`);
  });

  it('al cambiar el usuario aplica la sesión nueva', async () => {
    const { c } = crear();
    c.usuarioNuevo = 'juan.perez';
    c.claveUsuario = 'Actual123';
    await c.cambiarUsuario();
    const req = http.expectOne(`${API_BASE_URL}/cuenta/usuario`);
    expect(req.request.body).toEqual({ clave: 'Actual123', nuevoUsuario: 'juan.perez' });
    req.flush({ token: 't2', nombreCompleto: 'Juan Pérez', username: 'juan.perez', roles: ['PROVEEDOR'], permisos: [] });
    http.expectOne(`${API_BASE_URL}/cuenta`).flush({
      nombreCompleto: 'Juan Pérez', username: 'juan.perez', roles: ['Proveedor'], puedeEditarNombre: false,
    });
    expect(JSON.parse(localStorage.getItem('huata.sesion.v2') ?? '{}').token).toBe('t2');
  });
});
