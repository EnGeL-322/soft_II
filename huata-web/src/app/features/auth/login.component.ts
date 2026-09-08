import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="login-shell">
      <form class="login-card" (ngSubmit)="ingresar()">
        <div class="kicker">Huata Lácteos</div>
        <h1>Control de acopio de leche</h1>
        <p>Ingresa con tu cuenta para continuar.</p>

        <div class="form-grid" style="grid-template-columns: 1fr">
          <div class="field">
            <label>Usuario</label>
            <input type="text" name="username" [(ngModel)]="username" placeholder="admin@huata.pe" required />
          </div>
          <div class="field">
            <label>Contraseña</label>
            <input type="password" name="password" [(ngModel)]="password" required />
          </div>
        </div>

        @if (error()) {
          <p class="error-text" style="margin-top:10px">{{ error() }}</p>
        }

        <button type="submit" class="btn btn-primary" style="width:100%; margin-top:16px" [disabled]="cargando()">
          {{ cargando() ? 'Ingresando…' : 'Ingresar' }}
        </button>

        <p class="muted" style="margin-top:16px; font-size:11px">
          Demo: admin&#64;huata.pe / Huata2026*
        </p>
        <p class="muted" style="margin-top:8px; font-size:11px">
          <a routerLink="/portal">¿Eres proveedor? Consulta tu calidad sin iniciar sesión →</a>
        </p>
      </form>
    </div>
  `,
})
export class LoginComponent {
  username = '';
  password = '';
  readonly cargando = signal(false);
  readonly error = signal('');

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  ingresar(): void {
    this.error.set('');
    this.cargando.set(true);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.cargando.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.cargando.set(false);
        this.error.set('Usuario o contraseña incorrectos.');
      },
    });
  }
}
