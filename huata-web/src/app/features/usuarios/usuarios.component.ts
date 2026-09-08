import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageResponse, Usuario } from '../../core/models/models';
import { UsuariosService, UsuarioRequest } from '../../core/services/usuarios.service';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

const VACIO: UsuarioRequest = { nombreCompleto: '', username: '', email: '', telefono: '', password: '', roles: [] };

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [FormsModule, PaginadorComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Solo administración</div>
        <h1>Usuarios y roles</h1>
        <p>Cuentas de acceso al sistema. Los roles definen qué puede ver y hacer cada persona.</p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" (click)="nuevo()">+ Nuevo usuario</button>
      </div>
    </div>

    <div class="toolbar">
      <input type="text" placeholder="Buscar…" [(ngModel)]="q" (ngModelChange)="buscar()" />
    </div>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Usuario</th>
            <th>Roles</th>
            <th class="no-print">Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for (u of pagina()?.content ?? []; track u.id) {
            <tr>
              <td>{{ u.nombreCompleto }}</td>
              <td>{{ u.username }}</td>
              <td>{{ u.roles.join(', ') }}</td>
              <td>
                <button type="button" class="btn btn-soft" (click)="editar(u)">Editar</button>
                <button type="button" class="btn btn-danger" (click)="inactivar(u)">Inactivar</button>
              </td>
            </tr>
          }
          @if ((pagina()?.content?.length ?? 0) === 0) {
            <tr>
              <td colspan="4" class="muted" style="text-align:center; padding:24px">Sin resultados.</td>
            </tr>
          }
        </tbody>
      </table>
      @if (pagina(); as pg) {
        <app-paginador
          [page]="pg.page"
          [totalElements]="pg.totalElements"
          [totalPages]="pg.totalPages"
          (anterior)="cambiarPagina(pg.page - 1)"
          (siguiente)="cambiarPagina(pg.page + 1)"
        />
      }
    </div>

    @if (mostrarForm()) {
      <div class="table-title">
        <div><h3>{{ editandoId() ? 'Editar usuario' : 'Nuevo usuario' }}</h3></div>
      </div>
      <form class="card" (ngSubmit)="guardar()">
        <div class="form-grid">
          <div class="field">
            <label>Nombre completo</label>
            <input type="text" [(ngModel)]="form.nombreCompleto" name="nombreCompleto" required />
          </div>
          <div class="field">
            <label>Usuario (email)</label>
            <input type="text" [(ngModel)]="form.username" name="username" required [disabled]="!!editandoId()" />
          </div>
          <div class="field">
            <label>Teléfono</label>
            <input type="text" [(ngModel)]="form.telefono" name="telefono" />
          </div>
          <div class="field">
            <label>Contraseña {{ editandoId() ? '(dejar vacío para no cambiar)' : '' }}</label>
            <input type="password" [(ngModel)]="form.password" name="password" />
          </div>
        </div>
        <div class="field" style="margin-top:12px">
          <label>Roles</label>
          <div style="display:flex; gap:14px; flex-wrap:wrap">
            @for (r of rolesDisponibles(); track r) {
              <label style="display:flex; align-items:center; gap:6px; font-weight:400">
                <input type="checkbox" [checked]="form.roles.includes(r)" (change)="alternarRol(r)" />
                {{ r }}
              </label>
            }
          </div>
        </div>
        <div class="actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary">Guardar</button>
          <button type="button" class="btn btn-soft" (click)="mostrarForm.set(false)">Cancelar</button>
        </div>
      </form>
    }
  `,
})
export class UsuariosComponent implements OnInit {
  readonly pagina = signal<PageResponse<Usuario> | null>(null);
  readonly rolesDisponibles = signal<string[]>([]);
  readonly mostrarForm = signal(false);
  readonly editandoId = signal<number | null>(null);

  q = '';
  page = 0;
  form: UsuarioRequest = { ...VACIO, roles: [] };

  constructor(private readonly usuariosService: UsuariosService) {}

  ngOnInit(): void {
    this.usuariosService.roles().subscribe((r) => this.rolesDisponibles.set(r));
    this.buscar();
  }

  buscar(): void {
    this.page = 0;
    this.cargar();
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargar();
  }

  private cargar(): void {
    this.usuariosService.listar(this.q, this.page).subscribe((pg) => this.pagina.set(pg));
  }

  nuevo(): void {
    this.editandoId.set(null);
    this.form = { ...VACIO, roles: [] };
    this.mostrarForm.set(true);
  }

  editar(u: Usuario): void {
    this.editandoId.set(u.id);
    this.form = { nombreCompleto: u.nombreCompleto, username: u.username, email: u.email, telefono: u.telefono, password: '', roles: [...u.roles] };
    this.mostrarForm.set(true);
  }

  alternarRol(rol: string): void {
    this.form.roles = this.form.roles.includes(rol) ? this.form.roles.filter((r) => r !== rol) : [...this.form.roles, rol];
  }

  guardar(): void {
    const id = this.editandoId();
    const accion = id ? this.usuariosService.actualizar(id, this.form) : this.usuariosService.crear(this.form);
    accion.subscribe(() => {
      this.mostrarForm.set(false);
      this.cargar();
    });
  }

  inactivar(u: Usuario): void {
    if (!window.confirm(`¿Inactivar a ${u.nombreCompleto}?`)) {
      return;
    }
    this.usuariosService.inactivar(u.id).subscribe(() => this.cargar());
  }
}
