import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TrazabilidadService } from '../../core/services/trazabilidad.service';
import { TrazabilidadItem } from '../../core/models/models';
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
  { ruta: 'dashboard', icono: '◆', etiqueta: 'Centro de operación' },
  { ruta: 'recojo', icono: '◫', etiqueta: 'Recepción / Acopio' },
  { ruta: 'calidad', icono: '✓', etiqueta: 'Calidad y evidencia' },
  { ruta: 'rutas', icono: '↝', etiqueta: 'Rutas y movilidad' },
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './shell.component.html',
  styleUrls: ['./shell.component.css', './shell-responsive.css'],
})
export class ShellComponent implements OnInit {
  consulta = '';
  buscando = false;
  errorBusqueda = '';
  resultadosBusqueda: TrazabilidadItem[] | null = null;
  protected readonly grupos = [
    { nombre: 'Operación', rutas: ['dashboard', 'recojo', 'calidad', 'rutas'] },
    { nombre: 'Gestión', rutas: ['productores', 'acopiadores', 'liquidaciones', 'quesos'] },
    { nombre: 'Análisis', rutas: ['zonas', 'trazabilidad', 'reportes'] },
    { nombre: 'Administración', rutas: ['usuarios', 'parametros', 'datos-respaldo'] },
  ];
  items(rutas: string[]) {
    return this.navVisible().filter((n) => rutas.includes(n.ruta));
  }
  get rol() {
    return this.auth
      .roles()
      .map((r) =>
        r === 'ADMINISTRADOR'
          ? 'Administrador'
          : r === 'CALIDAD'
            ? 'Calidad'
            : r === 'ACOPIADOR'
              ? 'Acopiador'
              : r,
      )
      .join(' · ');
  }
  buscar() {
    if (!this.consulta.trim() || this.buscando) return;
    this.buscando = true;
    this.errorBusqueda = '';
    this.resultadosBusqueda = null;
    this.trazabilidad.buscar(this.consulta.trim()).subscribe({
      next: (rows) => {
        this.resultadosBusqueda = rows;
        this.buscando = false;
      },
      error: () => {
        this.errorBusqueda = 'No se pudo realizar la búsqueda.';
        this.buscando = false;
      },
    });
  }
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
    private readonly trazabilidad: TrazabilidadService,
  ) {}

  ngOnInit(): void {
    this.notificacionesService
      .listar()
      .subscribe({ next: (lista) => this.notificaciones.set(lista), error: () => {} });
  }

  noLeidas(): number {
    return this.notificaciones().filter((n) => !n.leida).length;
  }

  alternarNotificaciones(): void {
    this.mostrarNotificaciones.update((v) => !v);
  }

  abrirNotificacion(n: Notificacion): void {
    this.notificacionesService.marcarLeida(n.id).subscribe(() => {
      this.notificaciones.update((lista) =>
        lista.map((x) => (x.id === n.id ? { ...x, leida: true } : x)),
      );
    });
    this.mostrarNotificaciones.set(false);
    if (n.entidadTipo === 'INCIDENCIA_CALIDAD') {
      this.router.navigate(['/calidad'], { queryParams: { tab: 'seguimiento' } });
    }
  }
}
