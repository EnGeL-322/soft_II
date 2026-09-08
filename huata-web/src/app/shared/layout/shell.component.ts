import { Component, OnInit, computed, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { NotificacionesService } from '../../core/services/notificaciones.service';
import { Notificacion } from '../../core/models/models';

interface ItemNav {
  ruta: string;
  icono: string;
  etiqueta: string;
  soloAdmin?: boolean;
}

const NAV: ItemNav[] = [
  { ruta: 'dashboard', icono: '◆', etiqueta: 'Dashboard' },
  { ruta: 'recojo', icono: '◫', etiqueta: 'Recepción / Acopio' },
  { ruta: 'calidad', icono: '✓', etiqueta: 'Calidad y evidencia' },
  { ruta: 'rutas', icono: '↝', etiqueta: 'Rutas y centros' },
  { ruta: 'productores', icono: '♙', etiqueta: 'Proveedores' },
  { ruta: 'acopiadores', icono: '☺', etiqueta: 'Acopiadores' },
  { ruta: 'liquidaciones', icono: 'S/', etiqueta: 'Pagos y liquidación' },
  { ruta: 'quesos', icono: '▣', etiqueta: 'Producción · Quesos' },
  { ruta: 'zonas', icono: '◎', etiqueta: 'Zonas y rendimiento' },
  { ruta: 'trazabilidad', icono: '⤢', etiqueta: 'Seguimiento de producción' },
  { ruta: 'reportes', icono: '▤', etiqueta: 'Reportes' },
  { ruta: 'usuarios', icono: '⚇', etiqueta: 'Usuarios y roles', soloAdmin: true },
  { ruta: 'parametros', icono: '⚙', etiqueta: 'Configuración', soloAdmin: true },
  { ruta: 'datos-respaldo', icono: '⇅', etiqueta: 'Datos y respaldo', soloAdmin: true },
];

/** Layout comun a toda la web autenticada: sidebar (mismo orden que el prototipo HTML) + topbar. */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="sidebar">
        <div class="brand">
          <div class="brandmark">HL</div>
          <div>
            <strong>Huata Lácteos</strong>
            <small>Control de acopio</small>
          </div>
        </div>

        @for (item of navVisible(); track item.ruta) {
          <a class="navbtn" [routerLink]="item.ruta" routerLinkActive="active">
            <span class="navico">{{ item.icono }}</span>{{ item.etiqueta }}
          </a>
        }
      </aside>

      <div class="main">
        <header class="topbar">
          <div></div>
          <div class="topbar-user">
            <div style="position: relative">
              <button type="button" class="btn btn-soft" (click)="alternarNotificaciones()">
                🔔 @if (noLeidas() > 0) {
                  <span class="badge badge-rojo">{{ noLeidas() }}</span>
                }
              </button>
              @if (mostrarNotificaciones()) {
                <div
                  class="card"
                  style="position:absolute; right:0; top:38px; width:320px; max-height:360px; overflow:auto; z-index:20"
                >
                  <strong style="font-size:12px">Notificaciones</strong>
                  @if (notificaciones().length === 0) {
                    <p class="muted" style="margin-top:8px">Sin notificaciones.</p>
                  }
                  @for (n of notificaciones(); track n.id) {
                    <div
                      style="border-top:1px solid var(--line); padding:8px 0; cursor:pointer"
                      (click)="abrirNotificacion(n)"
                    >
                      <div style="font-weight:700; font-size:11.5px">
                        {{ n.titulo }} @if (!n.leida) {
                          <span class="badge badge-verde">nueva</span>
                        }
                      </div>
                      <div class="muted" style="font-size:11px">{{ n.mensaje }}</div>
                    </div>
                  }
                </div>
              }
            </div>
            <span>{{ auth.nombreCompleto() }} · {{ auth.roles().join(', ') }}</span>
            <button type="button" class="btn btn-soft" (click)="auth.logout()">Salir</button>
          </div>
        </header>
        <main class="content">
          <router-outlet />
        </main>
      </div>
    </div>
  `,
})
export class ShellComponent implements OnInit {
  protected readonly notificaciones = signal<Notificacion[]>([]);
  protected readonly mostrarNotificaciones = signal(false);

  // computed (no signal con valor fijo): se evalua recien cuando el template lo lee, por lo que
  // "auth" ya esta asignado por el constructor (un inicializador de campo corre antes que el
  // cuerpo del constructor y "this.auth" aun seria undefined en ese momento).
  protected readonly navVisible = computed(() =>
    NAV.filter((item) => !item.soloAdmin || this.auth.tieneRol('ADMINISTRADOR')),
  );

  constructor(
    protected readonly auth: AuthService,
    private readonly notificacionesService: NotificacionesService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.notificacionesService.listar().subscribe((lista) => this.notificaciones.set(lista));
  }

  noLeidas(): number {
    return this.notificaciones().filter((n) => !n.leida).length;
  }

  alternarNotificaciones(): void {
    this.mostrarNotificaciones.update((v) => !v);
  }

  abrirNotificacion(n: Notificacion): void {
    this.notificacionesService.marcarLeida(n.id).subscribe(() => {
      this.notificaciones.update((lista) => lista.map((x) => (x.id === n.id ? { ...x, leida: true } : x)));
    });
    this.mostrarNotificaciones.set(false);
    if (n.entidadTipo === 'INCIDENCIA_CALIDAD') {
      this.router.navigate(['/calidad'], { queryParams: { tab: 'seguimiento' } });
    }
  }
}
