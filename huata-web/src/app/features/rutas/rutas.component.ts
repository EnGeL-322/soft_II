import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CentroAcopio, Ruta } from '../../core/models/models';
import { RutasService } from '../../core/services/rutas.service';

/**
 * "Rutas y centros" agrupa Ruta + CentroAcopio: son el mismo bloque funcional en el backend
 * (RutaController) y la version oficial del alcance prioriza esto sobre una lista fija de zonas
 * (C-05, ver PROMPT_ARQUITECTURA_HUATA.md).
 */
@Component({
  selector: 'app-rutas',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Rutas y movilidad</div>
        <h1>Rutas y centros de acopio</h1>
        <p>
          La "zona" es un campo libre dentro de cada ruta: no hay una lista fija de zonas en el sistema, se
          configura aquí.
        </p>
      </div>
    </div>

    <div class="tabs">
      <button type="button" class="tab-btn" [class.active]="tab() === 'rutas'" (click)="tab.set('rutas')">Rutas</button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'centros'" (click)="tab.set('centros')">
        Centros de acopio
      </button>
    </div>

    @if (tab() === 'rutas') {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="crearRuta()">
        <div class="form-grid">
          <div class="field">
            <label>Nombre de la ruta</label>
            <input type="text" [(ngModel)]="nuevaRuta.nombre" name="nombre" required />
          </div>
          <div class="field">
            <label>Zona (texto libre)</label>
            <input type="text" [(ngModel)]="nuevaRuta.zona" name="zona" />
          </div>
          <div class="field">
            <label>Descripción</label>
            <input type="text" [(ngModel)]="nuevaRuta.descripcion" name="descripcion" />
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-primary">+ Agregar ruta</button>
        </div>
      </form>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Zona</th>
              <th>Descripción</th>
            </tr>
          </thead>
          <tbody>
            @for (r of rutas(); track r.id) {
              <tr>
                <td>{{ r.nombre }}</td>
                <td>{{ r.zona ?? '—' }}</td>
                <td>{{ r.descripcion ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    } @else {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="crearCentro()">
        <div class="form-grid">
          <div class="field">
            <label>Nombre del centro</label>
            <input type="text" [(ngModel)]="nuevoCentro.nombre" name="nombreCentro" required />
          </div>
          <div class="field">
            <label>Ubicación</label>
            <input type="text" [(ngModel)]="nuevoCentro.ubicacion" name="ubicacion" />
          </div>
          <div class="field">
            <label>Ruta asociada</label>
            <select [(ngModel)]="nuevoCentro.rutaId" name="rutaId">
              <option [ngValue]="undefined">Sin asignar</option>
              @for (r of rutas(); track r.id) {
                <option [ngValue]="r.id">{{ r.nombre }}</option>
              }
            </select>
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-primary">+ Agregar centro</button>
        </div>
      </form>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Nombre</th>
              <th>Ubicación</th>
              <th>Ruta</th>
            </tr>
          </thead>
          <tbody>
            @for (c of centros(); track c.id) {
              <tr>
                <td>{{ c.nombre }}</td>
                <td>{{ c.ubicacion ?? '—' }}</td>
                <td>{{ c.rutaNombre ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class RutasComponent implements OnInit {
  readonly tab = signal<'rutas' | 'centros'>('rutas');
  readonly rutas = signal<Ruta[]>([]);
  readonly centros = signal<CentroAcopio[]>([]);

  nuevaRuta = { nombre: '', zona: '', descripcion: '' };
  nuevoCentro: { nombre: string; ubicacion: string; rutaId?: number } = { nombre: '', ubicacion: '' };

  constructor(private readonly rutasService: RutasService) {}

  ngOnInit(): void {
    this.cargarRutas();
    this.cargarCentros();
  }

  private cargarRutas(): void {
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
  }

  private cargarCentros(): void {
    this.rutasService.listarCentros().subscribe((pg) => this.centros.set(pg.content));
  }

  crearRuta(): void {
    this.rutasService.crear(this.nuevaRuta).subscribe(() => {
      this.nuevaRuta = { nombre: '', zona: '', descripcion: '' };
      this.cargarRutas();
    });
  }

  crearCentro(): void {
    this.rutasService.crearCentro(this.nuevoCentro).subscribe(() => {
      this.nuevoCentro = { nombre: '', ubicacion: '' };
      this.cargarCentros();
    });
  }
}
