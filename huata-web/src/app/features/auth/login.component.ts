import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PortalComponent } from '../portal/portal.component';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, RouterLink, PortalComponent],
  template: `
    <div inert aria-hidden="true"><app-portal/></div>
    <div class="modal show" id="loginModal" role="dialog" aria-modal="true" aria-labelledby="login-title">
      <div class="modalbox login-box-v2">
        <div class="modalhead" style="margin-bottom:2px"><div class="login-mark" aria-hidden="true"><svg width="22" height="22" viewBox="0 0 24 24" fill="none"><path d="M12 3c2.5 3.2 5 6 5 9a5 5 0 0 1-10 0c0-3 2.5-5.8 5-9Z" fill="#fff"/></svg></div><a class="xbtn" routerLink="/portal" aria-label="Cerrar inicio de sesión" style="margin-left:auto">×</a></div>
        <h2 id="login-title">Iniciar sesión</h2><p class="login-sub">Accede al panel de operación de Huata Lácteos según tu rol.</p>
        <form (ngSubmit)="ingresar()">
          <div class="field"><label for="loginUser">Usuario</label><input id="loginUser" name="username" [(ngModel)]="username" autocomplete="username" required></div>
          <div class="field"><label for="loginPassword">Contraseña</label><input id="loginPassword" name="password" [(ngModel)]="password" type="password" autocomplete="current-password" required></div>
          @if(error()){<p class="error-text" role="alert">{{error()}}</p>}
          <div class="modal-actions"><a class="btn btn-soft" routerLink="/portal">Cancelar</a><button type="submit" class="btn btn-primary" [disabled]="cargando()">{{cargando()?'Ingresando…':'Entrar al panel'}}</button></div>
        </form>
      </div>
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
    if (this.cargando()) return;
    this.error.set('');
    this.cargando.set(true);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.cargando.set(false);
        this.router.navigateByUrl('/dashboard');
      },
      error: (e) => {
        this.cargando.set(false);
        this.error.set(e.status === 401 ? 'Usuario o contraseña incorrectos.' : (e.error?.message ?? 'No se pudo conectar con el servidor. Intenta nuevamente.'));
      },
    });
  }
}

