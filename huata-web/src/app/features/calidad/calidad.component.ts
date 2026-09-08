import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import {
  ControlCalidad,
  EstadoIncidencia,
  IncidenciaCalidad,
  ParametroCalidad,
  Productor,
  Recojo,
  ResultadoCalidad,
} from '../../core/models/models';
import { CalidadService } from '../../core/services/calidad.service';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService } from '../../core/services/recojo.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

type Tab = 'registrar' | 'historial' | 'seguimiento' | 'parametros';

const CONTROL_VACIO = {
  recojoId: undefined as number | undefined,
  densidad: undefined as number | undefined,
  puntoCongelacion: undefined as number | undefined,
  acidez: undefined as number | undefined,
  grasa: undefined as number | undefined,
  solidosNoGrasos: undefined as number | undefined,
  solidosTotales: undefined as number | undefined,
  revisionVisual: '',
  resultado: 'ACEPTADA' as ResultadoCalidad,
  equipoUtilizado: '',
  responsable: '',
};

/**
 * Calidad y evidencia: registrar control, historial por proveedor y seguimiento de incidencias
 * (botones "Historial de calidad" / "Seguimiento" del UI/UX, con deep-link desde notificaciones).
 */
@Component({
  selector: 'app-calidad',
  standalone: true,
  imports: [FormsModule, DatePipe, BadgeEstadoComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Calidad y evidencia</div>
        <h1>Control de calidad</h1>
        <p>Lenguaje simple para el usuario operativo: "olor agrio", "grumos", "aspecto normal", "agua añadida".</p>
      </div>
    </div>

    <div class="tabs">
      <button type="button" class="tab-btn" [class.active]="tab() === 'registrar'" (click)="tab.set('registrar')">
        Registrar control
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'historial'" (click)="tab.set('historial')">
        Historial de calidad
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'seguimiento'" (click)="tab.set('seguimiento')">
        Seguimiento
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'parametros'" (click)="tab.set('parametros')">
        Parámetros de referencia
      </button>
    </div>

    @if (tab() === 'registrar') {
      <form class="card" (ngSubmit)="registrarControl()">
        <div class="form-grid">
          <div class="field">
            <label>Productor</label>
            <select [(ngModel)]="productorId" name="productorId" (ngModelChange)="cargarRecojosDeProductor()">
              <option [ngValue]="undefined" disabled>Seleccionar…</option>
              @for (p of productores(); track p.id) {
                <option [ngValue]="p.id">{{ p.codigo }} · {{ p.nombres }} {{ p.apellidos }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label>Recojo</label>
            <select [(ngModel)]="control.recojoId" name="recojoId" required>
              <option [ngValue]="undefined" disabled>Seleccionar…</option>
              @for (r of recojosProductor(); track r.id) {
                <option [ngValue]="r.id">{{ r.fechaHora | date: 'short' }} · {{ r.litros }} L</option>
              }
            </select>
          </div>
          <div class="field">
            <label>Resultado</label>
            <select [(ngModel)]="control.resultado" name="resultado" required>
              <option value="ACEPTADA">Aceptada</option>
              <option value="OBSERVADA">Observada</option>
              <option value="RECHAZADA">Rechazada</option>
            </select>
          </div>
          <div class="field">
            <label>Densidad (g/mL a 15°C)</label>
            <input type="number" step="0.0001" [(ngModel)]="control.densidad" name="densidad" />
          </div>
          <div class="field">
            <label>Punto de congelación (°C)</label>
            <input type="number" step="0.001" [(ngModel)]="control.puntoCongelacion" name="puntoCongelacion" />
          </div>
          <div class="field">
            <label>Acidez (%)</label>
            <input type="number" step="0.01" [(ngModel)]="control.acidez" name="acidez" />
          </div>
          <div class="field">
            <label>Grasa (%)</label>
            <input type="number" step="0.01" [(ngModel)]="control.grasa" name="grasa" />
          </div>
          <div class="field">
            <label>Equipo utilizado</label>
            <input type="text" [(ngModel)]="control.equipoUtilizado" name="equipoUtilizado" />
          </div>
          <div class="field">
            <label>Responsable</label>
            <input type="text" [(ngModel)]="control.responsable" name="responsable" />
          </div>
        </div>
        <div class="field" style="margin-top:12px">
          <label>Revisión visual / olor</label>
          <textarea
            [(ngModel)]="control.revisionVisual"
            name="revisionVisual"
            placeholder="Ej: aspecto normal, sin observaciones / olor agrio y presencia de grumos"
          ></textarea>
        </div>
        <div class="actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary" [disabled]="!control.recojoId">Guardar control</button>
        </div>
      </form>
    }

    @if (tab() === 'historial' || tab() === 'seguimiento') {
      <div class="toolbar">
        <select [(ngModel)]="productorId" (ngModelChange)="cargarProductorSeleccionado()">
          <option [ngValue]="undefined" disabled>Seleccionar productor…</option>
          @for (p of productores(); track p.id) {
            <option [ngValue]="p.id">{{ p.codigo }} · {{ p.nombres }} {{ p.apellidos }}</option>
          }
        </select>
      </div>
    }

    @if (tab() === 'historial') {
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Resultado</th>
              <th>Densidad</th>
              <th>Punto de congelación</th>
              <th>Revisión visual</th>
              <th>Responsable</th>
            </tr>
          </thead>
          <tbody>
            @for (c of historial(); track c.id) {
              <tr>
                <td>{{ c.fecha | date: 'short' }}</td>
                <td><app-badge-estado [estado]="c.resultado" /></td>
                <td>{{ c.densidad ?? '—' }}</td>
                <td>{{ c.puntoCongelacion ?? '—' }}</td>
                <td>{{ c.revisionVisual ?? '—' }}</td>
                <td>{{ c.responsable ?? '—' }}</td>
              </tr>
            }
            @if (historial().length === 0) {
              <tr>
                <td colspan="6" class="muted" style="text-align:center; padding:24px">
                  Selecciona un productor para ver su historial.
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (tab() === 'seguimiento') {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="registrarIncidencia()">
        <div class="form-grid">
          <div class="field">
            <label>Tipo</label>
            <input type="text" [(ngModel)]="nuevaIncidencia.tipo" name="tipo" placeholder="Adulteración, agua añadida…" required />
          </div>
          <div class="field">
            <label>Descripción</label>
            <input type="text" [(ngModel)]="nuevaIncidencia.descripcion" name="descripcion" />
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-amber" [disabled]="!productorId">+ Registrar incidencia</button>
        </div>
      </form>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Fecha</th>
              <th>Tipo</th>
              <th>Descripción</th>
              <th>Estado</th>
              <th class="no-print">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (i of seguimiento(); track i.id) {
              <tr>
                <td>{{ i.fecha | date: 'short' }}</td>
                <td>{{ i.tipo }}</td>
                <td>{{ i.descripcion ?? '—' }}</td>
                <td><app-badge-estado [estado]="i.estado" /></td>
                <td>
                  @if (i.estado !== 'CERRADA') {
                    <button type="button" class="btn btn-soft" (click)="avanzarIncidencia(i)">
                      {{ i.estado === 'ABIERTA' ? 'Poner en seguimiento' : 'Cerrar' }}
                    </button>
                  }
                </td>
              </tr>
            }
            @if (seguimiento().length === 0) {
              <tr>
                <td colspan="5" class="muted" style="text-align:center; padding:24px">
                  Selecciona un productor para ver sus incidencias.
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }

    @if (tab() === 'parametros') {
      <div class="note" style="margin-bottom:14px">
        Rangos de referencia configurables (no son constantes de código): confirmar valores definitivos con
        Administración Huata antes de producción.
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Parámetro</th>
              <th>Mínimo</th>
              <th>Máximo</th>
              <th>Unidad</th>
            </tr>
          </thead>
          <tbody>
            @for (p of parametros(); track p.id) {
              <tr>
                <td>{{ p.nombre }}</td>
                <td>{{ p.valorMin ?? '—' }}</td>
                <td>{{ p.valorMax ?? '—' }}</td>
                <td>{{ p.unidad ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class CalidadComponent implements OnInit {
  readonly tab = signal<Tab>('registrar');
  readonly productores = signal<Productor[]>([]);
  readonly recojosProductor = signal<Recojo[]>([]);
  readonly historial = signal<ControlCalidad[]>([]);
  readonly seguimiento = signal<IncidenciaCalidad[]>([]);
  readonly parametros = signal<ParametroCalidad[]>([]);

  productorId?: number;
  control = { ...CONTROL_VACIO };
  nuevaIncidencia = { tipo: '', descripcion: '' };

  constructor(
    private readonly calidadService: CalidadService,
    private readonly productoresService: ProductoresService,
    private readonly recojoService: RecojoService,
    private readonly route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.calidadService.parametros().subscribe((p) => this.parametros.set(p));

    this.route.queryParamMap.subscribe((params) => {
      const tabParam = params.get('tab') as Tab | null;
      if (tabParam) {
        this.tab.set(tabParam);
      }
      const productorParam = params.get('productorId');
      if (productorParam) {
        this.productorId = Number(productorParam);
        this.cargarProductorSeleccionado();
      }
    });
  }

  cargarRecojosDeProductor(): void {
    if (!this.productorId) {
      return;
    }
    this.recojoService.listar({ productorId: this.productorId, size: 50 }).subscribe((pg) => {
      this.recojosProductor.set(pg.content.filter((r) => r.estado !== 'CANCELLED'));
    });
  }

  cargarProductorSeleccionado(): void {
    if (!this.productorId) {
      return;
    }
    this.calidadService.historial(this.productorId).subscribe((h) => this.historial.set(h));
    this.calidadService.seguimiento(this.productorId).subscribe((s) => this.seguimiento.set(s));
    this.cargarRecojosDeProductor();
  }

  registrarControl(): void {
    if (!this.control.recojoId) {
      return;
    }
    this.calidadService.registrarControl({ ...this.control, recojoId: this.control.recojoId }).subscribe(() => {
      this.control = { ...CONTROL_VACIO };
      if (this.productorId) {
        this.calidadService.historial(this.productorId).subscribe((h) => this.historial.set(h));
      }
    });
  }

  registrarIncidencia(): void {
    if (!this.productorId) {
      return;
    }
    this.calidadService
      .registrarIncidencia({ productorId: this.productorId, tipo: this.nuevaIncidencia.tipo, descripcion: this.nuevaIncidencia.descripcion })
      .subscribe(() => {
        this.nuevaIncidencia = { tipo: '', descripcion: '' };
        this.calidadService.seguimiento(this.productorId!).subscribe((s) => this.seguimiento.set(s));
      });
  }

  avanzarIncidencia(i: IncidenciaCalidad): void {
    const siguiente: EstadoIncidencia = i.estado === 'ABIERTA' ? 'EN_SEGUIMIENTO' : 'CERRADA';
    this.calidadService.cambiarEstadoIncidencia(i.id, siguiente).subscribe(() => this.cargarProductorSeleccionado());
  }
}
