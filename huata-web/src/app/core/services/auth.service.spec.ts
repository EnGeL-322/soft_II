import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { API_BASE_URL } from '../config/api-config';

const CLAVE = 'huata.sesion.v2';
const CLAVE_ANTIGUA = 'huata.sesion';

function crear() {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  return {
    auth: TestBed.inject(AuthService),
    http: TestBed.inject(HttpTestingController),
  };
}

describe('AuthService', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('descarta una sesion guardada sin permisos en vez de darla por buena', () => {
    // Es la forma que tenia la sesion antes de existir el modelo de permisos. Si se aceptara,
    // permisos() daria [] y el menu apareceria vacio sin que nadie pudiera recuperarse.
    localStorage.setItem(
      CLAVE_ANTIGUA,
      JSON.stringify({ token: 't', nombreCompleto: 'Ana', username: 'ana', roles: ['ADMINISTRADOR'] }),
    );

    const { auth, http } = crear();

    expect(auth.autenticado()).toBe(false);
    expect(localStorage.getItem(CLAVE_ANTIGUA)).toBeNull();
    http.expectNone(`${API_BASE_URL}/auth/me`);
    http.verify();
  });

  it('descarta una sesion con la clave nueva pero la forma incompleta', () => {
    localStorage.setItem(CLAVE, JSON.stringify({ token: 't', username: 'ana', roles: ['CALIDAD'] }));

    const { auth, http } = crear();

    expect(auth.autenticado()).toBe(false);
    expect(localStorage.getItem(CLAVE)).toBeNull();
    http.verify();
  });

  it('no revienta si localStorage tiene basura', () => {
    localStorage.setItem(CLAVE, 'esto no es json');

    const { auth } = crear();

    expect(auth.autenticado()).toBe(false);
  });

  it('guarda permisos al iniciar sesion y los expone en puede()', () => {
    const { auth, http } = crear();

    auth.login('operador.demo@huata.pe', 'x').subscribe();
    http.expectOne(`${API_BASE_URL}/auth/login`).flush({
      token: 'tok',
      nombreCompleto: 'Operador',
      username: 'operador.demo@huata.pe',
      roles: ['SECRETARIA'],
      permisos: ['panel.read', 'acopio.read', 'acopio.write'],
    });

    expect(auth.puede('acopio.write')).toBe(true);
    expect(auth.puede('pagos.write')).toBe(false);
    expect(auth.puedeAlguno('pagos.write', 'acopio.read')).toBe(true);
    http.verify();
  });

  it('al arrancar refresca los permisos desde /auth/me conservando el token', () => {
    // El backend no devuelve token en /me. Si se sustituyera la sesion entera, la siguiente
    // peticion saldria sin Authorization y el usuario acabaria deslogueado sin motivo.
    localStorage.setItem(
      CLAVE,
      JSON.stringify({
        token: 'tok-original',
        nombreCompleto: 'Ana',
        username: 'ana',
        roles: ['ADMINISTRADOR'],
        permisos: ['panel.read'],
      }),
    );

    const { auth, http } = crear();

    http.expectOne(`${API_BASE_URL}/auth/me`).flush({
      token: null,
      nombreCompleto: 'Ana',
      username: 'ana',
      roles: ['ADMINISTRADOR'],
      permisos: ['panel.read', 'pagos.write'],
    });

    expect(auth.token).toBe('tok-original');
    expect(auth.puede('pagos.write')).toBe(true);
    http.verify();
  });

  it('si /auth/me falla mantiene la sesion en vez de dejar la aplicacion inservible', () => {
    localStorage.setItem(
      CLAVE,
      JSON.stringify({
        token: 'tok',
        nombreCompleto: 'Ana',
        username: 'ana',
        roles: ['CALIDAD'],
        permisos: ['panel.read'],
      }),
    );

    const { auth, http } = crear();

    http.expectOne(`${API_BASE_URL}/auth/me`).error(new ProgressEvent('error'));

    expect(auth.autenticado()).toBe(true);
    expect(auth.puede('panel.read')).toBe(true);
    http.verify();
  });
  it('al volver a la pestana re-lee los permisos: lo concedido aparece sin pulsar F5', () => {
    localStorage.setItem(
      CLAVE,
      JSON.stringify({ token: 'tok', nombreCompleto: 'Ana', username: 'ana', roles: ['CALIDAD'], permisos: ['panel.read'] }),
    );
    const { auth, http } = crear();
    http.expectOne(`${API_BASE_URL}/auth/me`).flush({
      token: null, nombreCompleto: 'Ana', username: 'ana', roles: ['CALIDAD'], permisos: ['panel.read'],
    });
    expect(auth.puede('calidad.aprobar')).toBe(false);

    // El administrador le concede calidad.aprobar mientras ella trabaja en otra pestana.
    vi.setSystemTime(Date.now() + 31_000);
    window.dispatchEvent(new Event('focus'));
    http.expectOne(`${API_BASE_URL}/auth/me`).flush({
      token: null, nombreCompleto: 'Ana', username: 'ana', roles: ['CALIDAD'], permisos: ['panel.read', 'calidad.aprobar'],
    });

    expect(auth.puede('calidad.aprobar')).toBe(true);
    expect(auth.token).toBe('tok');
    vi.useRealTimers();
    http.verify();
  });
});
