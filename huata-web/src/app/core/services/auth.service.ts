import { HttpClient } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { API_BASE_URL } from '../config/api-config';

interface LoginResponse {
  token: string;
  nombreCompleto: string;
  username: string;
  roles: string[];
}

type SesionUsuario = LoginResponse;

const STORAGE_KEY = 'huata.sesion';

/** Sesion en memoria (signals) respaldada por localStorage para sobrevivir un refresh de pagina. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly sesionSignal = signal<SesionUsuario | null>(leerSesionGuardada());

  readonly sesion = this.sesionSignal.asReadonly();
  readonly autenticado = computed(() => this.sesionSignal() !== null);
  readonly nombreCompleto = computed(() => this.sesionSignal()?.nombreCompleto ?? '');
  readonly roles = computed(() => this.sesionSignal()?.roles ?? []);

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  login(username: string, password: string) {
    return this.http.post<LoginResponse>(`${API_BASE_URL}/auth/login`, { username, password }).pipe(
      tap((resp) => {
        this.sesionSignal.set(resp);
        localStorage.setItem(STORAGE_KEY, JSON.stringify(resp));
      }),
    );
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
}

function leerSesionGuardada(): SesionUsuario | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as SesionUsuario) : null;
  } catch {
    return null;
  }
}
