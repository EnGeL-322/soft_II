import { Component, computed, inject, output, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { RolInfo, Usuario } from '../../../core/models/models';
import { AuthService } from '../../../core/services/auth.service';
import { UsuarioRequest, UsuariosService } from '../../../core/services/usuarios.service';
import { CatalogoAccesos } from '../compartido/catalogos';
import { generarClave } from '../compartido/clave';
import { VentanaFichaComponent } from '../compartido/ventana-ficha.component';
import { CambioPermiso, SelectorVistasComponent } from '../compartido/selector-vistas.component';

/**
 * Tres pasos, cada uno con una sola decisión: los datos de la cuenta, los roles de esta persona y
 * las vistas que verá (las de sus roles, ajustables solo para ella).
 */
export type Paso = 'datos' | 'roles' | 'vistas';

const ORDEN: readonly Paso[] = ['datos', 'roles', 'vistas'];

interface FormInterno {
  nombreCompleto: string;
  username: string;
  telefono: string;
  password: string;
}

/**
 * Ficha de una cuenta interna en tres pasos: datos, roles y vistas. Al pasar a vistas aparecen las
 * que dan los roles elegidos, ya encendidas; se apagan las que la persona no deba ver y se le pueden dar otras
 * (roles + extras − quitados). Con `abrirPermisos` sirve también para dar o quitar acciones puntuales a la cuenta de
 * un acopiador, cuyos datos y rol se editan en su propia ficha. Todo lo vuelve a validar el backend.
 */
@Component({
  selector: 'app-ficha-interno',
  standalone: true,
  imports: [FormsModule, VentanaFichaComponent, SelectorVistasComponent],
  templateUrl: './ficha-interno.component.html',
  styleUrls: ['../accesos-dialog.css', '../compartido/personas.css'],
})
export class FichaInternoComponent {
  private readonly usuarios = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  protected readonly accesos = inject(CatalogoAccesos);
  private readonly ventana = viewChild.required(VentanaFichaComponent);

  /** Mensaje para el aviso de éxito; la lista se recarga al recibirlo. */
  readonly guardado = output<string>();

  readonly editando = signal<Usuario | null>(null);
  readonly soloPermisos = signal(false);
  readonly paso = signal<Paso>('datos');
  readonly rolesSel = signal<string[]>([]);
  readonly concedidos = signal<ReadonlySet<string>>(new Set());
  readonly revocados = signal<ReadonlySet<string>>(new Set());
  readonly verClave = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');
  form: FormInterno = vacio();

  /** Lo que dan los roles elegidos. */
  readonly base = computed<ReadonlySet<string>>(() => {
    const elegidos = new Set(this.rolesSel());
    return new Set(this.accesos.roles().filter((r) => elegidos.has(r.nombre)).flatMap((r) => r.permisos));
  });

  /** Lo que tendrá de verdad: roles − quitados + extras. Misma regla que Usuarios.permisosDe. */
  readonly efectivos = computed<ReadonlySet<string>>(() => {
    const total = new Set(this.base());
    this.concedidos().forEach((p) => total.add(p));
    this.revocados().forEach((p) => total.delete(p));
    return total;
  });

  readonly personalizado = computed(() => this.concedidos().size + this.revocados().size > 0);

  readonly titulo = computed(() => this.editando()?.nombreCompleto ?? 'Nuevo usuario interno');

  /** Pasos visibles: a una cuenta de acopiador solo se le ajustan las vistas. */
  readonly pasos = computed<readonly Paso[]>(() => (this.soloPermisos() ? ['vistas'] : ORDEN));
  readonly anterior = computed<Paso | null>(() => this.vecino(-1));
  readonly siguiente = computed<Paso | null>(() => this.vecino(1));

  nuevo(): void {
    this.soloPermisos.set(false);
    this.cargar(null, vacio(), []);
  }

  /** Abre la ficha de la cuenta; `paso` permite ir directo a sus roles o a sus vistas. */
  editar(u: Usuario, paso: Paso = 'datos'): void {
    if (u.gestionadoEnSuModulo) return;
    this.soloPermisos.set(false);
    const asignables = new Set(this.accesos.rolesAsignables().map((r) => r.nombre));
    this.cargar(u, datosDe(u), u.roles.filter((r) => asignables.has(r)));
    this.irA(paso);
  }

  /** Cuenta de acopiador: aquí solo se dan o quitan acciones; el rol y los datos son de su ficha. */
  abrirPermisos(u: Usuario): void {
    this.soloPermisos.set(true);
    this.cargar(u, datosDe(u), [...u.roles]);
    this.paso.set('vistas');
  }

  private cargar(u: Usuario | null, form: FormInterno, roles: string[]): void {
    this.editando.set(u);
    this.form = form;
    this.rolesSel.set(roles);
    this.concedidos.set(new Set(u?.permisosConcedidos ?? []));
    this.revocados.set(new Set(u?.permisosRevocados ?? []));
    this.error.set('');
    this.paso.set('datos');
    this.verClave.set(false);
    this.ventana().abrir();
  }

  cerrar(): void {
    this.ventana().cerrar();
  }

  alCerrar(): void {
    this.form.password = '';
    this.error.set('');
  }

  /** Avanza solo si lo anterior está completo; retroceder siempre se puede. */
  irA(paso: Paso): void {
    if (paso !== 'datos' && !this.datosCompletos()) {
      this.paso.set('datos');
      this.error.set('Completa el nombre, el usuario y la contraseña inicial.');
      return;
    }
    if (paso === 'vistas' && !this.soloPermisos() && !this.rolesSel().length) {
      this.paso.set('roles');
      this.error.set('Elige al menos un rol: es el punto de partida de sus vistas.');
      return;
    }
    this.error.set('');
    this.paso.set(paso);
  }

  /** Para la barra de pasos: los anteriores al actual ya se completaron. */
  pasoHecho(paso: Paso): boolean {
    return ORDEN.indexOf(paso) < ORDEN.indexOf(this.paso());
  }

  private vecino(delta: number): Paso | null {
    const pasos = this.pasos();
    return pasos[pasos.indexOf(this.paso()) + delta] ?? null;
  }

  private datosCompletos(): boolean {
    return (
      !!this.form.nombreCompleto.trim() &&
      !!this.form.username.trim() &&
      (!!this.editando() || !!this.form.password.trim())
    );
  }

  alternarRol(codigo: string): void {
    if (this.guardando()) return;
    const actual = this.rolesSel();
    this.rolesSel.set(actual.includes(codigo) ? actual.filter((r) => r !== codigo) : [...actual, codigo]);
    // Una excepción que ya coincide con lo que dan los roles deja de ser excepción.
    const base = this.base();
    this.concedidos.set(new Set([...this.concedidos()].filter((p) => !base.has(p))));
    this.revocados.set(new Set([...this.revocados()].filter((p) => base.has(p))));
  }

  aplicarCambios(cambios: CambioPermiso[]): void {
    const base = this.base();
    const concedidos = new Set(this.concedidos());
    const revocados = new Set(this.revocados());
    for (const { permiso, activo } of cambios) {
      if (base.has(permiso)) {
        if (activo) revocados.delete(permiso);
        else revocados.add(permiso);
      } else if (activo) {
        concedidos.add(permiso);
      } else {
        concedidos.delete(permiso);
      }
    }
    this.concedidos.set(concedidos);
    this.revocados.set(revocados);
  }

  restablecerARoles(): void {
    this.concedidos.set(new Set());
    this.revocados.set(new Set());
  }

  rolesDescritos(): string {
    return this.rolesSel().map((c) => this.accesos.etiquetaRol(c)).join(' + ');
  }

  /** Para la tarjeta del rol: cuántas pantallas trae. */
  pantallasDeRol(rol: RolInfo): number {
    return this.accesos.pantallas(rol.permisos);
  }

  generarClave(): void {
    this.form.password = generarClave();
    this.verClave.set(true);
  }

  guardar(): void {
    if (this.guardando()) return;
    const cuenta = this.editando();
    if (this.soloPermisos() && cuenta) {
      this.enviar(
        this.usuarios.actualizarPermisos(cuenta.id, [...this.concedidos()], [...this.revocados()]),
        'Permisos actualizados.',
        'No se pudieron guardar los permisos.',
      );
      return;
    }
    if (!this.datosCompletos()) {
      this.paso.set('datos');
      this.error.set('Completa el nombre, el usuario y la contraseña inicial.');
      return;
    }
    if (!this.rolesSel().length) {
      this.paso.set('roles');
      this.error.set('Elige al menos un rol: es el punto de partida de sus vistas.');
      return;
    }
    const username = this.form.username.trim();
    const datos: UsuarioRequest = {
      nombreCompleto: this.form.nombreCompleto.trim(),
      username,
      email: cuenta?.email || username,
      telefono: this.form.telefono.trim() || undefined,
      password: this.form.password || undefined,
      roles: [...this.rolesSel()],
      permisosConcedidos: [...this.concedidos()],
      permisosRevocados: [...this.revocados()],
    };
    this.enviar(
      cuenta ? this.usuarios.actualizar(cuenta.id, datos) : this.usuarios.crear(datos),
      cuenta ? 'Usuario actualizado.' : 'Usuario creado.',
      'No se pudo guardar el usuario. Revisa los datos.',
    );
  }

  private enviar(peticion: ReturnType<UsuariosService['crear']>, exito: string, fallo: string): void {
    this.guardando.set(true);
    this.error.set('');
    peticion.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: () => {
        this.guardando.set(false);
        this.ventana().cerrar();
        this.guardado.emit(exito);
        // Si la cuenta editada es la propia, el menú y los botones deben reflejarlo ya.
        this.auth.refrescarSesion();
      },
      error: (e) => this.error.set(e.error?.message ?? fallo),
    });
  }
}

function vacio(): FormInterno {
  return { nombreCompleto: '', username: '', telefono: '', password: '' };
}

function datosDe(u: Usuario): FormInterno {
  return { nombreCompleto: u.nombreCompleto, username: u.username, telefono: u.telefono ?? '', password: '' };
}
