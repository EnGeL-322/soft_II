import { Component, computed, input, output } from '@angular/core';

/** Tamaños de página que ofrece todo el sistema. El primero es el predeterminado. */
export const TAMANOS_PAGINA = [10, 20, 50, 100] as const;
export const TAMANO_PAGINA_PREDETERMINADO = TAMANOS_PAGINA[0];

/** Un número de página o un hueco "…" entre números. */
export type ItemPagina = number | 'hueco';

/**
 * Números a mostrar para `total` páginas estando en `actual` (ambos desde 0): siempre la primera,
 * la última y las vecinas de la actual; lo demás se resume con "…". Nunca más de 7 elementos.
 */
export function numerosDePagina(actual: number, total: number): ItemPagina[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);
  const vecinas = new Set([0, total - 1, actual - 1, actual, actual + 1]);
  if (actual <= 3) [1, 2, 3, 4].forEach((p) => vecinas.add(p));
  if (actual >= total - 4) [total - 5, total - 4, total - 3, total - 2].forEach((p) => vecinas.add(p));
  const paginas = [...vecinas].filter((p) => p >= 0 && p < total).sort((a, b) => a - b);
  const items: ItemPagina[] = [];
  paginas.forEach((p, i) => {
    if (i > 0 && p - paginas[i - 1] > 1) items.push('hueco');
    items.push(p);
  });
  return items;
}

/**
 * Paginación única de todas las tablas: indicador "Mostrando 1–10 de 245", registros por página,
 * Anterior · números · Siguiente. Sin registros no se muestra. No carga datos: emite la página o el tamaño elegido y quien lo
 * usa pide esa página al servidor o recorta su lista (ver PaginacionLocal).
 *
 * `page` empieza en 0, igual que PageResponse del backend.
 */
@Component({
  selector: 'app-paginador',
  standalone: true,
  template: `
    <!-- Sin registros no se muestra: la tabla ya dice que está vacía. -->
    @if (total() > 0) {
    <nav class="paginador" aria-label="Paginación">
      <span class="paginador-info" aria-live="polite">
        Mostrando <b>{{ desde() }}–{{ hasta() }}</b> de <b>{{ total() }}</b>
        {{ total() === 1 ? 'registro' : 'registros' }}
      </span>
      @if (total() > tamanos[0]) {
        <label class="paginador-tamano">
          <span>Por página</span>
          <select [value]="size()" (change)="cambiarTamano($event)" aria-label="Registros por página">
            @for (t of tamanos; track t) {
              <option [value]="t" [selected]="t === size()">{{ t }}</option>
            }
          </select>
        </label>
      }
      @if (paginas() > 1) {
        <div class="paginador-botones">
          <button type="button" (click)="ir(actual() - 1)" [disabled]="actual() === 0" aria-label="Página anterior">
            ‹ <span class="paginador-texto">Anterior</span>
          </button>
          @for (item of numeros(); track $index) {
            @if (item === 'hueco') {
              <span class="paginador-hueco" aria-hidden="true">…</span>
            } @else {
              <button
                type="button"
                class="paginador-numero"
                [class.activa]="item === actual()"
                [attr.aria-current]="item === actual() ? 'page' : null"
                [attr.aria-label]="'Página ' + (item + 1)"
                (click)="ir(item)"
              >
                {{ item + 1 }}
              </button>
            }
          }
          <button
            type="button"
            (click)="ir(actual() + 1)"
            [disabled]="actual() >= paginas() - 1"
            aria-label="Página siguiente"
          >
            <span class="paginador-texto">Siguiente</span> ›
          </button>
        </div>
      }
    </nav>
    }
  `,
})
export class PaginadorComponent {
  /** Página actual, desde 0. */
  readonly page = input(0);
  /** Registros por página. */
  readonly size = input<number>(TAMANO_PAGINA_PREDETERMINADO);
  /** Total de registros (de todas las páginas), ya filtrados. */
  readonly total = input(0);

  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();

  protected readonly tamanos = TAMANOS_PAGINA;

  readonly paginas = computed(() => Math.max(1, Math.ceil(this.total() / Math.max(1, this.size()))));
  /** La página pedida, ajustada si quedó fuera de rango (p. ej. tras eliminar el último registro). */
  readonly actual = computed(() => Math.min(Math.max(0, this.page()), this.paginas() - 1));
  readonly desde = computed(() => (this.total() === 0 ? 0 : this.actual() * this.size() + 1));
  readonly hasta = computed(() => Math.min(this.total(), (this.actual() + 1) * this.size()));
  readonly numeros = computed(() => numerosDePagina(this.actual(), this.paginas()));

  ir(pagina: number): void {
    const destino = Math.min(Math.max(0, pagina), this.paginas() - 1);
    if (destino !== this.actual()) this.pageChange.emit(destino);
  }

  cambiarTamano(evento: Event): void {
    const tamano = Number((evento.target as HTMLSelectElement).value);
    if (tamano && tamano !== this.size()) this.sizeChange.emit(tamano);
  }
}
