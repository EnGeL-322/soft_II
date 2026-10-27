import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PERMISO } from '../../core/config/navegacion';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { AuthService } from '../../core/services/auth.service';
import { IconoComponent, NombreIcono } from '../../shared/ui/icono.component';
import { ListaAcopiadoresComponent } from './acopiadores/lista-acopiadores.component';
import { BuscadorGeneralComponent } from './compartido/buscador-general.component';
import { CatalogoAccesos, CatalogoTerritorio } from './compartido/catalogos';
import { ListaInternosComponent } from './internos/lista-internos.component';
import { ListaProveedoresComponent } from './proveedores/lista-proveedores.component';
import { RolesPanelComponent } from './roles-panel.component';

export type Pestana = 'internos' | 'acopiadores' | 'proveedores' | 'roles';

interface DefPestana {
  id: Pestana;
  etiqueta: string;
  icono: NombreIcono;
  /** Ver la pestaña. Es el mismo permiso que exige el listado en el backend. */
  lectura: string;
  /** Mostrar "Nuevo…". Sin él el backend respondería 403. */
  escritura: string;
  nuevo: string;
  descripcion: string;
}

const PESTANAS: readonly DefPestana[] = [
  {
    id: 'internos',
    etiqueta: 'Internos',
    icono: 'usuarios',
    lectura: PERMISO.usuariosRead,
    escritura: PERMISO.usuariosWrite,
    nuevo: 'Nuevo usuario interno',
    descripcion: 'Personal de planta, oficina y gerencia: sus roles y qué puede hacer cada uno.',
  },
  {
    id: 'acopiadores',
    etiqueta: 'Acopiadores',
    icono: 'acopiadores',
    lectura: PERMISO.acopiadoresRead,
    escritura: PERMISO.acopiadoresWrite,
    nuevo: 'Nuevo acopiador',
    descripcion: 'Quienes recogen la leche con la app móvil. Cada uno trabaja en una ruta de su zona.',
  },
  {
    id: 'proveedores',
    etiqueta: 'Proveedores',
    icono: 'proveedores',
    lectura: PERMISO.proveedoresRead,
    escritura: PERMISO.proveedoresWrite,
    nuevo: 'Nuevo proveedor',
    descripcion: 'Quienes entregan leche. Un alta nueva queda activa al guardarla.',
  },
  {
    id: 'roles',
    etiqueta: 'Roles y vistas',
    icono: 'llave',
    lectura: PERMISO.usuariosRead,
    escritura: PERMISO.usuariosWrite,
    nuevo: 'Nuevo rol',
    descripcion: 'Qué vistas trae cada rol. Los cambios se aplican a todas las personas que lo tienen.',
  },
];

/**
 * Usuarios: la única pantalla donde se crean personas. Reúne en pestañas las cuentas internas, los
 * acopiadores y los proveedores (antes eran tres módulos con tres formularios distintos) y los roles.
 * Cada pestaña se ve con su propio permiso, así que quien solo consulta el padrón sigue entrando
 * aquí sin ver las cuentas internas. Todo lo vuelve a validar el backend.
 *
 * Enlaces: `?tipo=proveedores` abre esa pestaña y `&nuevo=1` abre además su ficha vacía.
 */
@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [
    IconoComponent,
    BuscadorGeneralComponent,
    ListaInternosComponent,
    ListaAcopiadoresComponent,
    ListaProveedoresComponent,
    RolesPanelComponent,
  ],
  providers: [CatalogoAccesos, CatalogoTerritorio],
  templateUrl: './usuarios.component.html',
  styleUrl: './usuarios.component.css',
})
export class UsuariosComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notificaciones = inject(NotificacionesService);
  protected readonly accesos = inject(CatalogoAccesos);

  private readonly internos = viewChild(ListaInternosComponent);
  private readonly acopiadores = viewChild(ListaAcopiadoresComponent);
  private readonly proveedores = viewChild(ListaProveedoresComponent);
  private readonly rolesPanel = viewChild(RolesPanelComponent);

  readonly pestanas = computed(() => PESTANAS.filter((p) => this.auth.puede(p.lectura)));
  readonly activa = signal<Pestana>('internos');
  readonly def = computed(() => PESTANAS.find((p) => p.id === this.activa())!);
  readonly puedeCrear = computed(() => this.auth.puede(this.def().escritura));
  readonly puedeEditarRoles = computed(() => this.auth.puede(PERMISO.usuariosWrite));
  /** ?nuevo=1 solo vale para la primera pestaña que se muestra. */
  readonly nuevoAlEntrar = signal(false);
  /** Texto con el que abre la pestaña (buscador general o ?q=). */
  readonly busqueda = signal('');
  /** Cambia para volver a montar la pestaña cuando el buscador general apunta a la que ya está abierta. */
  readonly montaje = signal(0);

  ngOnInit(): void {
    const params = this.ruta.snapshot.queryParamMap;
    const pedida = params.get('tipo') as Pestana | null;
    const visibles = this.pestanas();
    this.activa.set(visibles.find((p) => p.id === pedida)?.id ?? visibles[0]?.id ?? 'internos');
    this.nuevoAlEntrar.set(params.get('nuevo') === '1');
    this.busqueda.set(params.get('q') ?? '');
    if (this.auth.puede(PERMISO.usuariosRead)) this.accesos.cargar();
  }

  elegir(id: Pestana): void {
    this.nuevoAlEntrar.set(false);
    this.busqueda.set('');
    this.activa.set(id);
    // La pestaña queda en la URL: recargar o compartir el enlace vuelve al mismo sitio.
    this.router.navigate([], { relativeTo: this.ruta, queryParams: { tipo: id }, replaceUrl: true });
  }

  /** Desde el buscador general: abre esa pestaña con el texto ya puesto en su buscador. */
  buscarEn({ tipo, q }: { tipo: Pestana; q: string }): void {
    this.nuevoAlEntrar.set(false);
    this.busqueda.set(q);
    this.activa.set(tipo);
    this.montaje.update((n) => n + 1);
    this.router.navigate([], { relativeTo: this.ruta, queryParams: { tipo, q }, replaceUrl: true });
  }

  nuevo(): void {
    switch (this.activa()) {
      case 'internos':
        return this.internos()?.nuevo();
      case 'acopiadores':
        return this.acopiadores()?.nuevo();
      case 'proveedores':
        return this.proveedores()?.nuevo();
      case 'roles':
        void this.rolesPanel()?.nuevo();
        return;
    }
  }

  alCambiarRoles(mensaje: string): void {
    this.notificaciones.exito(mensaje);
    this.accesos.cargar();
    // Si el rol editado es uno de los propios, el menú y los botones deben reflejarlo ya.
    this.auth.refrescarSesion();
  }
}
