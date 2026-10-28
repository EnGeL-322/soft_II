import { Component, Input, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PERMISO } from '../../core/config/navegacion';
import { AuthService } from '../../core/services/auth.service';

/** Los pasos del proceso, en el orden en que pasa el producto. */
export type PasoProduccion = 'catalogo' | 'ordenes' | 'registrar' | 'camara' | 'vender';

interface Paso {
  clave: PasoProduccion;
  titulo: string;
  ayuda: string;
  ruta: string;
  vista: string;
  permisos: string[];
}

const PASOS: Paso[] = [
  { clave: 'catalogo', titulo: 'Catálogo y recetas', ayuda: 'Qué se hace y con qué', ruta: '/recetas', vista: 'catalogo', permisos: [PERMISO.recetasRead] },
  { clave: 'ordenes', titulo: 'Planificar', ayuda: 'Órdenes y qué comprar', ruta: '/produccion-queso', vista: 'ordenes', permisos: [PERMISO.produccionRead, PERMISO.quesosRead] },
  { clave: 'registrar', titulo: 'Producir', ayuda: 'Registrar el lote', ruta: '/produccion-queso', vista: 'registrar', permisos: [PERMISO.produccionWrite, PERMISO.quesosWrite] },
  { clave: 'camara', titulo: 'Cámara', ayuda: 'Lotes y vencimientos', ruta: '/produccion-queso', vista: 'historial', permisos: [PERMISO.produccionRead, PERMISO.quesosRead] },
  { clave: 'vender', titulo: 'Vender', ayuda: 'Sale primero lo que vence antes', ruta: '/venta-queso', vista: 'registrar', permisos: [PERMISO.ventasRegistrar] },
];

/**
 * El proceso de producción en una tira: dónde está la persona y qué sigue. Antes cada pantalla era
 * una isla (catálogo, órdenes, registro, historial, venta) y había que saber de memoria el orden.
 * Solo se muestran como enlace los pasos que el rol puede abrir; los demás quedan como referencia.
 */
@Component({
  selector: 'app-flujo-produccion',
  standalone: true,
  imports: [RouterLink],
  template: `
    <nav class="flujo no-print" aria-label="Proceso de producción">
      <ol>
        @for (p of pasos(); track p.clave; let i = $index) {
          <li [class.activo]="p.clave === activo()" [attr.aria-current]="p.clave === activo() ? 'step' : null">
            @if (p.puede) {
              <a [routerLink]="p.ruta" [queryParams]="{ vista: p.vista }">
                <span class="num">{{ i + 1 }}</span>
                <span class="texto"><strong>{{ p.titulo }}</strong><small>{{ contadores[p.clave] ?? p.ayuda }}</small></span>
              </a>
            } @else {
              <span class="paso-sin-acceso">
                <span class="num">{{ i + 1 }}</span>
                <span class="texto"><strong>{{ p.titulo }}</strong><small>{{ p.ayuda }}</small></span>
              </span>
            }
          </li>
        }
      </ol>
    </nav>
  `,
  styles: `
    .flujo { margin: 0 0 18px; }
    ol { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 6px; margin: 0; padding: 0; list-style: none; counter-reset: none; }
    li { position: relative; min-width: 0; }
    a, .paso-sin-acceso { display: flex; align-items: center; gap: 10px; height: 100%; padding: 10px 12px; border: 1px solid #dfe7f1; border-radius: 12px; background: #fff; color: inherit; text-decoration: none; }
    a:hover { border-color: #b9cbe2; background: #f7f9fc; }
    a:focus-visible { outline: none; box-shadow: var(--ring); }
    .paso-sin-acceso { opacity: 0.55; }
    .num { flex: none; display: grid; place-items: center; width: 26px; height: 26px; border-radius: 50%; background: #e6edf6; color: var(--marino, var(--text)); font-size: 13px; font-weight: 700; }
    .texto { display: flex; flex-direction: column; min-width: 0; }
    strong { font-size: 13.5px; color: var(--marino, var(--text)); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    small { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    li.activo a { border-color: var(--azul, var(--green)); background: #eef5ff; box-shadow: inset 0 -3px 0 var(--azul, var(--green)); }
    li.activo .num { background: var(--azul, var(--green)); color: #fff; }
    @media (max-width: 900px) {
      ol { grid-template-columns: none; grid-auto-flow: column; grid-auto-columns: minmax(150px, 1fr); overflow-x: auto; padding-bottom: 4px; }
    }
  `,
})
export class FlujoProduccionComponent {
  private readonly auth = inject(AuthService);
  readonly activo = signal<PasoProduccion | null>(null);

  @Input({ required: true }) set paso(v: PasoProduccion | null) {
    this.activo.set(v);
  }

  /** Texto corto bajo cada paso (por ejemplo «3 pendientes»); sin él se muestra la ayuda. */
  @Input() contadores: Partial<Record<PasoProduccion, string>> = {};

  readonly pasos = computed(() =>
    PASOS.map((p) => ({ ...p, puede: p.permisos.some((permiso) => this.auth.puede(permiso)) })),
  );
}
