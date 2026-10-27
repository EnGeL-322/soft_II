import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { ModuloAcceso, RolInfo } from '../../core/models/models';
import { DialogoService } from '../../core/notificaciones/dialogo.service';
import { UsuariosService } from '../../core/services/usuarios.service';
import { contarPantallas } from './compartido/catalogos';
import { CambioPermiso, SelectorVistasComponent } from './compartido/selector-vistas.component';

interface Borrador {
  etiqueta: string;
  descripcion: string;
  permisos: ReadonlySet<string>;
}

/**
 * Roles como plantillas de vistas: lista a la izquierda, el rol elegido a la derecha y se edita ahí
 * mismo. Los predefinidos se pueden ajustar y restaurar; los propios se crean (partiendo de otro si se
 * quiere) y se eliminan si nadie los usa. Cambiar un rol cambia a todos los que lo tienen. El backend
 * impide además dejar el sistema sin nadie que administre usuarios.
 */
@Component({
  selector: 'app-roles-panel',
  standalone: true,
  imports: [FormsModule, SelectorVistasComponent],
  templateUrl: './roles-panel.component.html',
  styleUrl: './roles-panel.component.css',
})
export class RolesPanelComponent {
  private readonly usuarios = inject(UsuariosService);
  private readonly dialogo = inject(DialogoService);

  readonly roles = input.required<RolInfo[]>();
  readonly catalogo = input.required<ModuloAcceso[]>();
  readonly puedeEditar = input(false);
  /** Mensaje para el aviso de la página; el padre recarga roles y usuarios. */
  readonly cambiado = output<string>();

  /** null con `creando` = rol nuevo. */
  readonly elegido = signal<RolInfo | null>(null);
  readonly creando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');
  readonly etiqueta = signal('');
  readonly descripcion = signal('');
  readonly permisos = signal<ReadonlySet<string>>(new Set());
  copiarDe = '';
  private original: Borrador = { etiqueta: '', descripcion: '', permisos: new Set() };

  readonly internos = computed(() => this.roles().filter((r) => !r.gestionadoEnSuModulo));
  readonly deModulo = computed(() => this.roles().filter((r) => r.gestionadoEnSuModulo));

  readonly soloLectura = computed(
    () => !this.puedeEditar() || !!this.elegido()?.permisosBloqueados || this.guardando(),
  );

  readonly hayCambios = computed(() => {
    const o = this.original;
    const p = this.permisos();
    return (
      this.creando() ||
      this.etiqueta().trim() !== o.etiqueta ||
      this.descripcion().trim() !== o.descripcion ||
      p.size !== o.permisos.size ||
      [...p].some((x) => !o.permisos.has(x))
    );
  });

  constructor() {
    // Al llegar (o recargarse) la lista, se muestra el primer rol, o el mismo que estaba abierto.
    effect(() => {
      const roles = this.roles();
      untracked(() => {
        if (this.creando()) return;
        const actual = this.elegido();
        const mismo = actual && roles.find((r) => r.id === actual.id);
        const destino = mismo ?? roles[0];
        if (destino) this.cargar(destino);
      });
    });
  }

  pantallas(rol: RolInfo): number {
    return contarPantallas(this.catalogo(), rol.permisos);
  }

  async elegir(rol: RolInfo): Promise<void> {
    if (rol.id === this.elegido()?.id && !this.creando()) return;
    if (await this.puedeDescartar()) this.cargar(rol);
  }

  async nuevo(): Promise<void> {
    if (!this.puedeEditar() || !(await this.puedeDescartar())) return;
    this.elegido.set(null);
    this.creando.set(true);
    this.copiarDe = '';
    this.fijar({ etiqueta: '', descripcion: '', permisos: new Set() });
  }

  private cargar(rol: RolInfo): void {
    this.creando.set(false);
    this.elegido.set(rol);
    this.fijar({ etiqueta: rol.etiqueta, descripcion: rol.descripcion ?? '', permisos: new Set(rol.permisos) });
  }

  private fijar(b: Borrador): void {
    this.original = b;
    this.etiqueta.set(b.etiqueta);
    this.descripcion.set(b.descripcion);
    this.permisos.set(b.permisos);
    this.error.set('');
  }

  private async puedeDescartar(): Promise<boolean> {
    if (!this.hayCambios() || !this.puedeEditar()) return true;
    return this.dialogo.confirmar({
      titulo: '¿Descartar los cambios?',
      mensaje: 'Hay cambios en este rol que no se guardaron.',
      confirmar: 'Descartar',
      tipo: 'aviso',
    });
  }

  descartar(): void {
    const rol = this.elegido();
    if (rol) this.cargar(rol);
    else if (this.roles()[0]) this.cargar(this.roles()[0]);
  }

  copiar(): void {
    const origen = this.roles().find((r) => r.nombre === this.copiarDe);
    if (origen) this.permisos.set(new Set(origen.permisos));
  }

  aplicarCambios(cambios: CambioPermiso[]): void {
    const nueva = new Set(this.permisos());
    cambios.forEach(({ permiso, activo }) => (activo ? nueva.add(permiso) : nueva.delete(permiso)));
    this.permisos.set(nueva);
  }

  guardar(): void {
    if (this.guardando() || !this.puedeEditar()) return;
    const etiqueta = this.etiqueta().trim();
    if (!etiqueta) {
      this.error.set('Ponle un nombre al rol.');
      return;
    }
    const rol = this.elegido();
    const datos = { etiqueta, descripcion: this.descripcion().trim() || undefined, permisos: [...this.permisos()] };
    this.ejecutar(
      rol ? this.usuarios.actualizarRol(rol.id, datos) : this.usuarios.crearRol(datos),
      rol ? `Rol «${etiqueta}» actualizado.` : `Rol «${etiqueta}» creado.`,
    );
  }

  async restaurar(): Promise<void> {
    const rol = this.elegido();
    if (!rol) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Restaurar «${rol.etiqueta}»?`,
      mensaje: 'Vuelve a su configuración estándar y se pierden los ajustes hechos al rol.',
      confirmar: 'Restaurar',
      tipo: 'aviso',
    });
    if (ok) this.ejecutar(this.usuarios.restaurarRol(rol.id), `Rol «${rol.etiqueta}» restaurado a su estándar.`);
  }

  async eliminar(): Promise<void> {
    const rol = this.elegido();
    if (!rol) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Eliminar el rol «${rol.etiqueta}»?`,
      mensaje: 'Esta acción no se puede deshacer.',
      confirmar: 'Eliminar',
      tipo: 'peligro',
    });
    if (ok) {
      this.elegido.set(null);
      this.ejecutar(this.usuarios.eliminarRol(rol.id), `Rol «${rol.etiqueta}» eliminado.`);
    }
  }

  private ejecutar(peticion: Observable<unknown>, mensaje: string): void {
    this.guardando.set(true);
    this.error.set('');
    peticion.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: () => {
        // La lista se recarga desde el padre; al llegar se vuelve a mostrar este rol con lo guardado.
        this.creando.set(false);
        this.original = { etiqueta: this.etiqueta().trim(), descripcion: this.descripcion().trim(), permisos: this.permisos() };
        this.cambiado.emit(mensaje);
      },
      error: (e) => this.error.set(e.error?.message ?? 'No se pudo guardar el rol.'),
    });
  }
}
