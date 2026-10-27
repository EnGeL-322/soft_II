import { Component, OnInit, inject, input, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PERMISO } from '../../../core/config/navegacion';
import { Usuario } from '../../../core/models/models';
import { DialogoService } from '../../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../../core/notificaciones/notificaciones.service';
import { AuthService } from '../../../core/services/auth.service';
import { EstadoUsuarios, UsuariosService } from '../../../core/services/usuarios.service';
import { IconoComponent } from '../../../shared/ui/icono.component';
import { ListaPaginada } from '../../../shared/ui/lista-paginada';
import { PaginadorComponent } from '../../../shared/ui/paginador.component';
import { CatalogoAccesos } from '../compartido/catalogos';
import { iniciales } from '../compartido/iniciales';
import { FichaInternoComponent } from './ficha-interno.component';
import { recargarEnVivo } from '../../../core/services/cambios-tiempo-real.service';

/**
 * Cuentas internas (todo rol salvo Acopiador y Proveedor, que tienen su pestaña). Se filtra en el
 * servidor (`internos=true`) para que la paginación cuadre.
 */
@Component({
  selector: 'app-lista-internos',
  standalone: true,
  imports: [FormsModule, PaginadorComponent, IconoComponent, FichaInternoComponent],
  templateUrl: './lista-internos.component.html',
  styleUrl: '../compartido/personas.css',
})
export class ListaInternosComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de usuarios, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['usuarios'], () => this.lista.buscar());
  private readonly usuarios = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly dialogo = inject(DialogoService);
  private readonly notificaciones = inject(NotificacionesService);
  protected readonly accesos = inject(CatalogoAccesos);
  private readonly ficha = viewChild.required(FichaInternoComponent);

  /** ?nuevo=1: abrir la ficha vacía en cuanto se muestra la pestaña. */
  readonly abrirNuevo = input(false);
  /** Texto que llega del buscador general de Usuarios (o de ?q= en el enlace). */
  readonly busquedaInicial = input('');

  q = '';
  estado: EstadoUsuarios = 'ACTIVOS';

  readonly lista = new ListaPaginada<Usuario>(
    (page, size) => this.usuarios.listar(this.q, page, size, this.estado, true),
    'No se pudo cargar la lista de usuarios.',
  );

  readonly iniciales = iniciales;

  get puedeEditar(): boolean {
    return this.auth.puede(PERMISO.usuariosWrite);
  }

  ngOnInit(): void {
    this.q = this.busquedaInicial();
    this.lista.buscar();
    if (this.abrirNuevo()) this.accesos.alEstarListo(() => this.nuevo());
  }

  nuevo(): void {
    if (this.puedeEditar) this.ficha().nuevo();
  }

  editar(u: Usuario): void {
    if (this.puedeEditar) this.ficha().editar(u);
  }

  esPersonalizado(u: Usuario): boolean {
    return (u.permisosConcedidos?.length ?? 0) + (u.permisosRevocados?.length ?? 0) > 0;
  }

  alGuardar(mensaje: string): void {
    this.notificaciones.exito(mensaje);
    this.lista.recargar();
    // Los conteos de usuarios por rol cambian.
    this.accesos.cargar();
  }

  async eliminar(u: Usuario): Promise<void> {
    if (!this.puedeEditar) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Eliminar a ${u.nombreCompleto}?`,
      mensaje: 'Esta acción no se puede deshacer.',
      confirmar: 'Eliminar',
      tipo: 'peligro',
    });
    if (!ok) return;
    this.usuarios.eliminar(u.id).subscribe({
      next: (r) => {
        this.notificaciones.exito(
          r.conservaHistorial
            ? `${r.nombre} fue eliminado. Su nombre se conserva solo como autor de ${r.mensajes} mensaje(s) a proveedores.`
            : `${r.nombre} fue eliminado.`,
        );
        this.lista.recargar();
        this.accesos.cargar();
      },
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo eliminar al usuario.'),
    });
  }

  reactivar(u: Usuario): void {
    if (!this.puedeEditar) return;
    this.usuarios.reactivar(u.id).subscribe({
      next: () => {
        this.notificaciones.exito(`${u.nombreCompleto} vuelve a estar activo.`);
        this.lista.recargar();
      },
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo reactivar al usuario.'),
    });
  }
}
