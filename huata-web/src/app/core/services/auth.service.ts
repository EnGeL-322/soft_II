import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API_BASE_URL } from '../config/api-config';

export interface LoginResponse {
  token: string;
  nombreCompleto: string;
  username: string;
  roles: string[];
  /** Codigos "<recurso>.<accion>" que concede el backend. Ver Permiso.java. */
  permisos: string[];
}

type SesionUsuario = LoginResponse;

/**
 * La version va en la clave a proposito.
 *
 * Al anadir "permisos" a la sesion, las sesiones ya guardadas en el navegador siguieron ahi sin ese
 * campo: `autenticado()` daba true, `permisos()` daba [] y el menu aparecia vacio, sin forma de
 * recuperarse salvo borrando localStorage a mano. Cambiar la clave hace que una sesion con forma
 * antigua sencillamente no exista, y el usuario acabe en el login, que es lo correcto. Si vuelve a
 * cambiar la forma de SesionUsuario, sube la version.
 */
const STORAGE_KEY = 'huata.sesion.v2';
const CLAVES_ANTIGUAS = ['huata.sesion'];

/** Sesion en memoria (signals) respaldada por localStorage para sobrevivir un refresh de pagina. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly sesionSignal = signal<SesionUsuario | null>(leerSesionGuardada());

  readonly sesion = this.sesionSignal.asReadonly();
  readonly autenticado = computed(() => this.sesionSignal() !== null);
  readonly nombreCompleto = computed(() => this.sesionSignal()?.nombreCompleto ?? '');
  readonly roles = computed(() => this.sesionSignal()?.roles ?? []);
  readonly permisos = computed(() => this.sesionSignal()?.permisos ?? []);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {
    this.refrescarSesion();
    this.refrescarAlVolver();
  }

  /**
   * Ademas de al arrancar, se re-leen los permisos cada vez que la persona vuelve a esta pestana (y
   * como mucho una vez cada 30 s). Sin esto, un permiso concedido o quitado desde "Usuarios y roles"
   * no aparecia hasta pulsar F5, aunque el backend ya lo aplicaba: parecia que asignarlo no servia.
   */
  private refrescarAlVolver(): void {
    if (typeof document === 'undefined') {
      return;
    }
    let ultimo = Date.now();
    const alVolver = () => {
      if (document.visibilityState === 'visible' && Date.now() - ultimo > 30_000) {
        ultimo = Date.now();
        this.refrescarSesion();
      }
    };
    document.addEventListener('visibilitychange', alVolver);
    window.addEventListener('focus', alVolver);
  }

  login(username: string, password: string) {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { username, password }).pipe(
      tap((resp) => this.guardar(resp)),
    );
  }

  /**
   * Re-lee roles y permisos del backend al arrancar la aplicacion.
   *
   * Los permisos se guardan en el navegador al iniciar sesion, asi que sin esto un cambio en
   * rol_permisos —que es dato editable, justamente para ajustar responsabilidades sin desplegar— no
   * se notaria hasta que la persona volviera a entrar. El token NO viaja en la respuesta de /me
   * (llega null), por eso se conserva el que ya se tenia en vez de sustituir la sesion entera.
   *
   * Un 401 aqui lo resuelve errorInterceptor cerrando la sesion; cualquier otro fallo (el backend
   * aun no esta arriba) se ignora para no dejar la aplicacion inutilizable.
   */
  refrescarSesion(): void {
    const actual = this.sesionSignal();
    if (!actual) {
      return;
    }
    this.http.get<LoginResponse>(`${API_BASE_URL}/auth/me`).subscribe({
      next: (resp) => this.guardar({ ...resp, token: actual.token }),
      error: () => {},
    });
  }

  /**
   * Sesión nueva entregada por "Mi cuenta" al cambiar el usuario: el token anterior ya no vale (su
   * usuario no existe), así que se reemplaza sin pasar por el login.
   */
  aplicarSesion(sesion: LoginResponse): void {
    this.guardar(sesion);
  }

  private guardar(sesion: SesionUsuario): void {
    this.sesionSignal.set(sesion);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sesion));
    } catch {
      // Modo privado o almacenamiento lleno: la sesion sigue viva en memoria hasta el refresh.
    }
  }

  logout(): void {
    this.sesionSignal.set(null);
    localStorage.removeItem(STORAGE_KEY);
    this.router.navigateByUrl('/login');
  }

  get token(): string | null {
    return this.sesionSignal()?.token ?? null;
  }

  tieneRol(rol: string): boolean {
    return this.roles().includes(rol);
  }

  /**
   * Unica pregunta que debe hacerse la interfaz para mostrar u ocultar algo. Es solo UX: el backend
   * exige la misma autoridad en cada endpoint (@PreAuthorize), asi que alterar esto en el navegador
   * no da acceso a nada, solo rompe el propio menu.
   */
  puede(permiso: string): boolean {
    return this.permisos().includes(permiso);
  }

  /** Cierto si tiene al menos uno de los permisos (para rutas que agrupan varios recursos). */
  puedeAlguno(...permisos: string[]): boolean {
    return permisos.some((p) => this.permisos().includes(p));
  }
}

/**
 * Lee la sesion guardada y comprueba su forma antes de darla por buena. El `as SesionUsuario` de la
 * version anterior era una mentira al compilador: en localStorage cabe cualquier cosa, incluida una
 * sesion escrita por una version anterior de la aplicacion.
 */
function leerSesionGuardada(): SesionUsuario | null {
  try {
    CLAVES_ANTIGUAS.forEach((clave) => localStorage.removeItem(clave));
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const candidato = JSON.parse(raw) as Partial<SesionUsuario>;
    const valida =
      typeof candidato?.token === 'string' &&
      typeof candidato?.username === 'string' &&
      Array.isArray(candidato?.roles) &&
      Array.isArray(candidato?.permisos);
    if (!valida) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return candidato as SesionUsuario;
  } catch {
    return null;
  }
}
