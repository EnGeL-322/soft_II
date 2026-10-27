import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { CambiosTiempoRealService } from '../../core/services/cambios-tiempo-real.service';
import {
  GRUPOS_NAV,
  ItemNav,
  NAVEGACION,
  SubitemNav,
  coincideBusquedaNav,
  permisosDeItem,
} from '../../core/config/navegacion';
import { nombreRol } from '../../core/config/roles';
import { MensajeriaFlotanteComponent } from '../mensajeria/mensajeria-flotante.component';
import { IconoComponent } from '../ui/icono.component';

/** Preferencia del menú contraído, por navegador. Solo comodidad: si falla, el menú se ve expandido. */
const CLAVE_MENU_CONTRAIDO = 'huata.menuContraido';

function leerContraido(): boolean {
  try {
    return localStorage.getItem(CLAVE_MENU_CONTRAIDO) === '1';
  } catch {
    return false;
  }
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    FormsModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MensajeriaFlotanteComponent,
    IconoComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrls: [
    './shell.component.css',
    './shell-subnav.css',
    './shell-contraido.css',
    './shell-responsive.css',
  ],
})
export class ShellComponent {
  readonly menuAbierto = signal(false);
  /** Menú lateral reducido a iconos (solo escritorio) para dejar más espacio de trabajo. */
  readonly contraido = signal(leerContraido());
  readonly busquedaMenu = signal('');
  protected readonly urlActual = signal('');
  private readonly destroyRef = inject(DestroyRef);
  /** El shell mantiene abierto el canal de cambios toda la sesión; las pantallas lo comparten. */
  private readonly cambiosEnVivo = inject(CambiosTiempoRealService);
  protected readonly estadoEnVivo = this.cambiosEnVivo.estado;
  protected readonly grupos = GRUPOS_NAV;
  protected readonly navVisible = computed(() =>
    NAVEGACION.filter((item) => permisosDeItem(item).some((p) => this.auth.puede(p))),
  );
  readonly seccionActual = computed(() =>
    this.navVisible().find((item) => this.urlActual().split(/[?#]/)[0] === '/' + item.ruta),
  );
  /** `?vista=` de la URL: decide qué subitem del menú queda marcado. */
  private readonly vistaUrl = computed(
    () => new URLSearchParams(this.urlActual().split('?')[1]?.split('#')[0] ?? '').get('vista') ?? '',
  );
  /** Subitem abierto de la sección actual: el de la URL o, sin `vista`, el primero permitido. */
  readonly subitemActual = computed(() => {
    const item = this.seccionActual();
    if (!item) return undefined;
    const subs = this.subitemsDe(item);
    return subs.find((s) => s.vista === this.vistaUrl()) ?? subs[0];
  });
  readonly inicio = computed(() => this.navVisible().find((item) => item.ruta === 'dashboard'));
  readonly resultados = computed(() =>
    this.navVisible().filter(
      (item) => item.ruta !== 'dashboard' && coincideBusquedaNav(item, this.busquedaMenu()),
    ),
  );

  get iniciales() {
    return this.auth
      .nombreCompleto()
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((n) => n[0])
      .join('');
  }
  get rol() {
    return this.auth
      .roles()
      .map((r) => nombreRol(r))
      .join(' · ');
  }
  items(grupo: string) {
    return this.resultados().filter((item) => item.grupo === grupo);
  }
  subitemsDe(item: ItemNav): SubitemNav[] {
    return (item.subitems ?? []).filter((s) => !s.permisos || s.permisos.some((p) => this.auth.puede(p)));
  }
  /** Los subitems se despliegan en la sección abierta o cuando la búsqueda los nombra. */
  muestraSubitems(item: ItemNav): boolean {
    return this.seccionActual()?.ruta === item.ruta || !!this.busquedaMenu().trim();
  }
  alternarContraido(): void {
    const valor = !this.contraido();
    this.contraido.set(valor);
    try {
      localStorage.setItem(CLAVE_MENU_CONTRAIDO, valor ? '1' : '0');
    } catch {
      // Sin almacenamiento (modo privado): la preferencia dura solo esta sesión.
    }
  }
  navegar(): void {
    this.menuAbierto.set(false);
    this.busquedaMenu.set('');
  }
  etiquetaDe(item: ItemNav): string {
    return item.etiquetaEscritura && item.permisoEscritura && this.auth.puede(item.permisoEscritura)
      ? item.etiquetaEscritura
      : item.etiqueta;
  }

  constructor(
    protected readonly auth: AuthService,
    private readonly router: Router,
  ) {
    const actualizar = (url: string) => {
      this.urlActual.set(url);
      this.menuAbierto.set(false);
    };
    actualizar(router.url);
    router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((evento) => actualizar(evento.urlAfterRedirects));
    this.cambiosEnVivo.cambios$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }
}
