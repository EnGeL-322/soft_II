import { Injectable, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { PERMISO } from '../../../core/config/navegacion';
import { Acopiador, ModuloAcceso, RolInfo, Ruta } from '../../../core/models/models';
import { AcopiadoresService } from '../../../core/services/acopiadores.service';
import { AuthService } from '../../../core/services/auth.service';
import { RutasService } from '../../../core/services/rutas.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { OpcionBuscador } from '../../../shared/ui/buscador.component';

/**
 * Roles y catálogo de vistas: los usan la lista de internos, la ficha de permisos y la pestaña Roles.
 * Se cargan una vez por visita a Usuarios (se provee en UsuariosComponent, no en root).
 */
@Injectable()
export class CatalogoAccesos {
  private readonly usuarios = inject(UsuariosService);

  readonly roles = signal<RolInfo[]>([]);
  readonly catalogo = signal<ModuloAcceso[]>([]);
  readonly error = signal('');

  /** Acopiador y Proveedor nacen con su ficha, no se eligen como rol de una cuenta interna. */
  readonly rolesAsignables = computed(() => this.roles().filter((r) => !r.gestionadoEnSuModulo));

  private pendientes: Array<() => void> = [];
  private listo = false;

  cargar(): void {
    forkJoin({ roles: this.usuarios.roles(), catalogo: this.usuarios.catalogoVistas() }).subscribe({
      next: ({ roles, catalogo }) => {
        this.roles.set(roles);
        this.catalogo.set(catalogo);
        this.error.set('');
        this.listo = true;
        this.pendientes.splice(0).forEach((fn) => fn());
      },
      error: () => this.error.set('No se pudo cargar el catálogo de roles y vistas. Recarga la página.'),
    });
  }

  /** Ejecuta `fn` cuando roles y catálogo ya están (la matriz de permisos los necesita). */
  alEstarListo(fn: () => void): void {
    if (this.listo) fn();
    else this.pendientes.push(fn);
  }

  etiquetaRol(codigo: string): string {
    return this.roles().find((r) => r.nombre === codigo)?.etiqueta ?? codigo;
  }

  pantallas(permisos: Iterable<string>): number {
    return contarPantallas(this.catalogo(), permisos);
  }
}

/**
 * Pantallas del menú que abren estos permisos. Desde V55 Proveedores y Acopiadores son pestañas de
 * Usuarios: las vistas que comparten ruta cuentan como una sola pantalla.
 */
export function contarPantallas(catalogo: ModuloAcceso[], permisos: Iterable<string>): number {
  const tiene = new Set(permisos);
  const rutas = catalogo
    .flatMap((m) => m.vistas)
    .filter((v) => v.ruta && tiene.has(v.permisoAcceso))
    .map((v) => v.ruta);
  return new Set(rutas).size;
}

/**
 * Zonas, rutas y acopiadores: filtros y formularios de las pestañas Acopiadores y Proveedores. La zona
 * es texto libre de la ruta (decisión C-05): las zonas que existen son las que tienen las rutas.
 */
@Injectable()
export class CatalogoTerritorio {
  private readonly rutasService = inject(RutasService);
  private readonly acopiadoresService = inject(AcopiadoresService);
  private readonly auth = inject(AuthService);

  readonly rutas = signal<Ruta[]>([]);
  readonly acopiadores = signal<Acopiador[]>([]);
  private cargado = false;

  readonly rutasConZona = computed(() => this.rutas().filter((r) => !!r.zona?.trim()));

  readonly zonas = computed(() =>
    [...new Set(this.rutasConZona().map((r) => r.zona!.trim()))].sort((a, b) => a.localeCompare(b)),
  );

  /** Opciones "Zona · Ruta" del buscador de los formularios. */
  readonly opcionesRuta = computed<OpcionBuscador[]>(() =>
    this.rutasConZona().map((r) => ({
      valor: r.id,
      etiqueta: `${r.zona} · ${r.nombre}`,
      detalle: r.descripcion || undefined,
    })),
  );

  /** Se pide la primera vez que se abre una pestaña que lo usa. */
  asegurar(): void {
    if (this.cargado) return;
    this.cargado = true;
    this.rutasService.listar('', 0, 200).subscribe({
      next: (pg) => this.rutas.set(pg.content),
      error: () => (this.cargado = false),
    });
    this.recargarAcopiadores();
  }

  /** Tras crear o mover un acopiador: el formulario de proveedor ofrece los de su ruta. */
  recargarAcopiadores(): void {
    // Sin acopiadores.read el backend respondería 403; el formulario de proveedor queda sin opciones.
    if (!this.auth.puede(PERMISO.acopiadoresRead)) return;
    this.acopiadoresService.listar('', 0, 200).subscribe((pg) => this.acopiadores.set(pg.content));
  }

  rutasDeZona(zona: string): Ruta[] {
    return zona ? this.rutasConZona().filter((r) => r.zona!.trim() === zona) : this.rutasConZona();
  }

  zonaDeRuta(rutaId?: number): string | undefined {
    return this.rutas().find((r) => r.id === rutaId)?.zona?.trim() || undefined;
  }

  acopiadoresDeRuta(rutaId?: number): Acopiador[] {
    return rutaId ? this.acopiadores().filter((a) => a.rutaId === rutaId) : [];
  }

  /** Quién recoge a un proveedor: su responsable directo si lo tiene, si no el acopiador de su ruta. */
  quienRecoge(responsable: string | undefined, rutaId?: number): string {
    return responsable || this.acopiadoresDeRuta(rutaId).map((a) => `${a.nombres} ${a.apellidos}`).join(', ');
  }
}
