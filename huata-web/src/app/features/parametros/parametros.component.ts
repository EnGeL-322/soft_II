import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageResponse, ParametroCalidad, ParametroNegocio, RegistroAuditoria, RolInfo } from '../../core/models/models';
import { AuditoriaService } from '../../core/services/auditoria.service';
import { CalidadService } from '../../core/services/calidad.service';
import { ParametrosService } from '../../core/services/parametros.service';

type Tab = 'calidad' | 'general' | 'roles' | 'auditoria';

/**
 * Configuración: todo lo que el proyecto marca como "no hardcodear" vive aquí, editable solo por
 * Administración (rangos de calidad, parámetros generales, roles y bitácora de auditoría — igual
 * que la vista "config" del prototipo).
 */
@Component({
  selector: 'app-parametros',
  standalone: true,
  imports: [FormsModule, DatePipe],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Solo administración</div>
        <h1>Configuración</h1>
        <p>Valores configurables del sistema: nada de esto debe quedar fijo en el código.</p>
      </div>
    </div>

    <div class="tabs">
      <button type="button" class="tab-btn" [class.active]="tab() === 'calidad'" (click)="tab.set('calidad')">
        Rangos de calidad
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'general'" (click)="tab.set('general')">
        Parámetros generales
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'roles'" (click)="tab.set('roles')">Roles</button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'auditoria'" (click)="tab.set('auditoria')">
        Auditoría
      </button>
    </div>

    @if (tab() === 'calidad') {
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Parámetro</th>
              <th>Mínimo</th>
              <th>Máximo</th>
              <th>Unidad</th>
              <th class="no-print">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (p of parametrosCalidad(); track p.id) {
              <tr>
                <td>{{ p.nombre }}</td>
                <td><input type="number" step="0.0001" [(ngModel)]="p.valorMin" [name]="'min' + p.id" /></td>
                <td><input type="number" step="0.0001" [(ngModel)]="p.valorMax" [name]="'max' + p.id" /></td>
                <td>{{ p.unidad ?? '—' }}</td>
                <td><button type="button" class="btn btn-soft" (click)="guardarParametroCalidad(p)">Guardar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (tab() === 'general') {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="guardarParametroNegocio()">
        <div class="form-grid">
          <div class="field">
            <label>Clave</label>
            <input type="text" [(ngModel)]="nuevo.clave" name="clave" required />
          </div>
          <div class="field">
            <label>Valor</label>
            <input type="text" [(ngModel)]="nuevo.valor" name="valor" required />
          </div>
          <div class="field">
            <label>Descripción</label>
            <input type="text" [(ngModel)]="nuevo.descripcion" name="descripcion" />
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-primary">Guardar parámetro</button>
        </div>
      </form>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Clave</th>
              <th>Valor</th>
              <th>Descripción</th>
            </tr>
          </thead>
          <tbody>
            @for (p of parametrosNegocio(); track p.id) {
              <tr>
                <td>{{ p.clave }}</td>
                <td>{{ p.valor }}</td>
                <td class="muted">{{ p.descripcion ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (tab() === 'roles') {
      <div class="table-title">
        <div><h3>Roles</h3><p>Responsabilidades por perfil.</p></div>
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Rol</th>
              <th class="wrap">Responsabilidad</th>
              <th>Acceso</th>
            </tr>
          </thead>
          <tbody>
            @for (r of roles(); track r.id) {
              <tr>
                <td>{{ r.nombre }}</td>
                <td>{{ r.descripcion ?? '—' }}</td>
                <td>{{ r.accesos ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (tab() === 'auditoria') {
      <div class="table-title">
        <div><h3>Auditoría</h3><p>Registro de acciones relevantes del sistema.</p></div>
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Fecha/hora</th>
              <th>Actor</th>
              <th>Acción</th>
              <th>Entidad</th>
              <th class="wrap">Detalle</th>
            </tr>
          </thead>
          <tbody>
            @for (a of auditoria(); track a.id) {
              <tr>
                <td>{{ a.fecha | date: 'short' }}</td>
                <td>{{ a.actor ?? '—' }}</td>
                <td>{{ a.accion }}</td>
                <td>{{ a.entidad ?? '—' }}</td>
                <td class="muted">{{ a.detalle ?? '—' }}</td>
              </tr>
            }
            @if (auditoria().length === 0) {
              <tr>
                <td colspan="5" class="muted" style="text-align:center; padding:24px">Sin registros todavía.</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class ParametrosComponent implements OnInit {
  readonly tab = signal<Tab>('calidad');
  readonly parametrosCalidad = signal<ParametroCalidad[]>([]);
  readonly parametrosNegocio = signal<ParametroNegocio[]>([]);
  readonly roles = signal<RolInfo[]>([]);
  readonly auditoria = signal<RegistroAuditoria[]>([]);

  nuevo: { clave: string; valor: string; descripcion?: string } = { clave: '', valor: '' };

  constructor(
    private readonly calidadService: CalidadService,
    private readonly parametrosService: ParametrosService,
    private readonly auditoriaService: AuditoriaService,
  ) {}

  ngOnInit(): void {
    this.calidadService.parametros().subscribe((p) => this.parametrosCalidad.set(p));
    this.parametrosService.listar().subscribe((p) => this.parametrosNegocio.set(p));
    this.auditoriaService.roles().subscribe((r) => this.roles.set(r));
    this.auditoriaService.listar().subscribe((pg: PageResponse<RegistroAuditoria>) => this.auditoria.set(pg.content));
  }

  guardarParametroCalidad(p: ParametroCalidad): void {
    this.calidadService.guardarParametro(p.id, p).subscribe();
  }

  guardarParametroNegocio(): void {
    this.parametrosService.guardar(this.nuevo).subscribe(() => {
      this.nuevo = { clave: '', valor: '' };
      this.parametrosService.listar().subscribe((p) => this.parametrosNegocio.set(p));
    });
  }
}
