import { Component, OnInit, computed, inject, input, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { PERMISO } from '../../../core/config/navegacion';
import { Acopiador } from '../../../core/models/models';
import { DialogoService } from '../../../core/notificaciones/dialogo.service';
import { NotificacionesService } from '../../../core/notificaciones/notificaciones.service';
import { AcopiadoresService } from '../../../core/services/acopiadores.service';
import { AuthService } from '../../../core/services/auth.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { IconoComponent } from '../../../shared/ui/icono.component';
import { ListaPaginada } from '../../../shared/ui/lista-paginada';
import { PaginadorComponent } from '../../../shared/ui/paginador.component';
import { CatalogoAccesos, CatalogoTerritorio } from '../compartido/catalogos';
import { iniciales } from '../compartido/iniciales';
import { FichaInternoComponent } from '../internos/ficha-interno.component';
import { FichaAcopiadorComponent } from './ficha-acopiador.component';
import { recargarEnVivo } from '../../../core/services/cambios-tiempo-real.service';

/** Acopiadores con filtro por zona y ruta. Sus permisos puntuales se ajustan aquí mismo. */
@Component({
  selector: 'app-lista-acopiadores',
  standalone: true,
  imports: [FormsModule, RouterLink, PaginadorComponent, IconoComponent, FichaAcopiadorComponent, FichaInternoComponent],
  templateUrl: './lista-acopiadores.component.html',
  styleUrl: '../compartido/personas.css',
})
export class ListaAcopiadoresComponent implements OnInit {
  /** Tiempo real: si alguien guarda algo de acopiadores, usuarios, rutas, esta pantalla se actualiza sola. */
  private readonly _enVivo = recargarEnVivo(['acopiadores', 'usuarios', 'rutas'], () => this.lista.buscar());
  private readonly acopiadores = inject(AcopiadoresService);
  private readonly usuarios = inject(UsuariosService);
  private readonly auth = inject(AuthService);
  private readonly notificaciones = inject(NotificacionesService);
  private readonly router = inject(Router);
  private readonly dialogo = inject(DialogoService);
  private readonly accesos = inject(CatalogoAccesos);
  protected readonly territorio = inject(CatalogoTerritorio);
  private readonly ficha = viewChild.required(FichaAcopiadorComponent);
  private readonly fichaPermisos = viewChild.required(FichaInternoComponent);

  readonly abrirNuevo = input(false);
  /** Texto que llega del buscador general de Usuarios (o de ?q= en el enlace). */
  readonly busquedaInicial = input('');

  q = '';
  zona = '';
  rutaId?: number;

  readonly lista = new ListaPaginada<Acopiador>(
    (page, size) => this.acopiadores.buscar({ q: this.q, zona: this.zona, rutaId: this.rutaId, page, size }),
    'No se pudo cargar la lista de acopiadores.',
  );

  readonly iniciales = iniciales;

  readonly puedeEditar = computed(() => this.auth.puede(PERMISO.acopiadoresWrite));
  /** La matriz de permisos es la de Usuarios: necesita leer y escribir cuentas. */
  readonly puedeAjustarPermisos = computed(
    () => this.auth.puede(PERMISO.usuariosRead) && this.auth.puede(PERMISO.usuariosWrite),
  );

  get rutasDeLaZona() {
    return this.territorio.rutasDeZona(this.zona);
  }

  get hayFiltros(): boolean {
    return !!(this.q || this.zona || this.rutaId);
  }

  ngOnInit(): void {
    this.q = this.busquedaInicial();
    this.territorio.asegurar();
    this.lista.buscar();
    if (this.abrirNuevo()) queueMicrotask(() => this.nuevo());
  }

  cambiarZona(): void {
    if (this.rutaId && !this.rutasDeLaZona.some((r) => r.id === this.rutaId)) this.rutaId = undefined;
    this.lista.buscar();
  }

  limpiarFiltros(): void {
    this.q = '';
    this.zona = '';
    this.rutaId = undefined;
    this.lista.buscar();
  }

  nuevo(): void {
    if (!this.puedeEditar()) return;
    this.ficha().nuevo();
  }

  editar(a: Acopiador): void {
    if (this.puedeEditar()) this.ficha().editar(a);
  }

  /**
   * Al crear o editar se va a Zonas, con su zona abierta y su ruta resaltada: ahí se ve con quién
   * comparte la ruta y qué proveedores visita (y se ofrece pintar su recorrido). Sin acceso a Zonas se
   * queda en la lista.
   */
  alGuardar(a: Acopiador): void {
    this.notificaciones.exito(`${a.nombres} ${a.apellidos} quedó asignado a ${a.zona ?? 'su zona'} · ${a.rutaNombre ?? 'su ruta'}.`);
    if (a.rutaId && this.auth.puede(PERMISO.zonasRead)) {
      this.router.navigate(['/zonas'], { queryParams: { ruta: a.rutaId, acopiador: a.id } });
      return;
    }
    this.lista.recargar();
  }

  /**
   * Baja lógica: deja de aparecer y su cuenta ya no entra, pero su historial de recojos se conserva.
   * Sus proveedores con responsable directo pasan a cualquier acopiador de la ruta (lo hace el backend).
   */
  async eliminar(a: Acopiador): Promise<void> {
    if (!this.puedeEditar()) return;
    const ok = await this.dialogo.confirmar({
      titulo: `¿Eliminar a ${a.nombres} ${a.apellidos}?`,
      mensaje:
        'Deja de aparecer en las listas y su cuenta ya no podrá entrar a la web ni a la app. ' +
        'Su historial de recojos se conserva.',
      confirmar: 'Eliminar',
      tipo: 'peligro',
    });
    if (!ok) return;
    this.acopiadores.inactivar(a.id).subscribe({
      next: () => {
        this.notificaciones.exito(`${a.nombres} ${a.apellidos} fue eliminado.`);
        this.lista.recargar();
        this.territorio.recargarAcopiadores();
      },
      error: (e) => this.notificaciones.error(e.error?.message ?? 'No se pudo eliminar al acopiador.'),
    });
  }

  permisos(a: Acopiador): void {
    if (!a.usuarioId || !this.puedeAjustarPermisos()) return;
    this.usuarios.obtener(a.usuarioId).subscribe({
      next: (u) => this.accesos.alEstarListo(() => this.fichaPermisos().abrirPermisos(u)),
      error: () => this.notificaciones.error('No se encontró la cuenta del acopiador.'),
    });
  }

  alGuardarPermisos(mensaje: string): void {
    this.notificaciones.exito(mensaje);
  }
}
