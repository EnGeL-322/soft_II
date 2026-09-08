import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Acopiador, PageResponse } from '../../core/models/models';
import { AcopiadoresService, AcopiadorRequest } from '../../core/services/acopiadores.service';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

const VACIO: AcopiadorRequest = { nombres: '', apellidos: '', documentoIdentidad: '', telefono: '' };

@Component({
  selector: 'app-acopiadores',
  standalone: true,
  imports: [FormsModule, PaginadorComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Personal de ruta</div>
        <h1>Acopiadores</h1>
        <p>Responsables del recojo en campo. En Fase 2 (móvil) usan esta misma cuenta para autenticarse.</p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" (click)="nuevo()">+ Nuevo acopiador</button>
      </div>
    </div>

    <div class="toolbar">
      <input type="text" placeholder="Buscar por nombre o documento…" [(ngModel)]="q" (ngModelChange)="buscar()" />
    </div>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Nombre</th>
            <th>Documento</th>
            <th>Teléfono</th>
            <th class="no-print">Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for (a of pagina()?.content ?? []; track a.id) {
            <tr>
              <td>{{ a.nombres }} {{ a.apellidos }}</td>
              <td>{{ a.documentoIdentidad ?? '—' }}</td>
              <td>{{ a.telefono ?? '—' }}</td>
              <td><button type="button" class="btn btn-soft" (click)="editar(a)">Editar</button></td>
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
        <div><h3>{{ editandoId() ? 'Editar acopiador' : 'Nuevo acopiador' }}</h3></div>
      </div>
      <form class="card" (ngSubmit)="guardar()">
        <div class="form-grid">
          <div class="field">
            <label>Nombres</label>
            <input type="text" [(ngModel)]="form.nombres" name="nombres" required />
          </div>
          <div class="field">
            <label>Apellidos</label>
            <input type="text" [(ngModel)]="form.apellidos" name="apellidos" required />
          </div>
          <div class="field">
            <label>Documento de identidad</label>
            <input type="text" [(ngModel)]="form.documentoIdentidad" name="documentoIdentidad" />
          </div>
          <div class="field">
            <label>Teléfono</label>
            <input type="text" [(ngModel)]="form.telefono" name="telefono" />
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
export class AcopiadoresComponent implements OnInit {
  readonly pagina = signal<PageResponse<Acopiador> | null>(null);
  readonly mostrarForm = signal(false);
  readonly editandoId = signal<number | null>(null);

  q = '';
  page = 0;
  form: AcopiadorRequest = { ...VACIO };

  constructor(private readonly acopiadoresService: AcopiadoresService) {}

  ngOnInit(): void {
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
    this.acopiadoresService.listar(this.q, this.page).subscribe((pg) => this.pagina.set(pg));
  }

  nuevo(): void {
    this.editandoId.set(null);
    this.form = { ...VACIO };
    this.mostrarForm.set(true);
  }

  editar(a: Acopiador): void {
    this.editandoId.set(a.id);
    this.form = { nombres: a.nombres, apellidos: a.apellidos, documentoIdentidad: a.documentoIdentidad, telefono: a.telefono };
    this.mostrarForm.set(true);
  }

  guardar(): void {
    const id = this.editandoId();
    const accion = id ? this.acopiadoresService.actualizar(id, this.form) : this.acopiadoresService.crear(this.form);
    accion.subscribe(() => {
      this.mostrarForm.set(false);
      this.cargar();
    });
  }
}
