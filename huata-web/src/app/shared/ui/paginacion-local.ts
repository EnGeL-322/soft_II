import { signal } from '@angular/core';
import { TAMANO_PAGINA_PREDETERMINADO } from './paginador.component';

/**
 * Paginación en el navegador para listas que ya están cargadas y son pocas (avisos del día,
 * planilla, ventas filtradas...). Para listas grandes se usa la paginación del servidor.
 *
 * Uso en la plantilla, siempre con la lista YA filtrada y ordenada:
 *
 *   @for (x of pag.ver(filtrados(), claveFiltros()); track x.id) { ... }
 *   <app-paginador [page]="pag.pagina(filtrados().length)" [size]="pag.tamano()"
 *     [total]="filtrados().length" (pageChange)="pag.irA($event)" (sizeChange)="pag.cambiarTamano($event)" />
 *
 * `clave` resume los filtros activos (texto, estado, fecha...): si cambia, se vuelve a la página 1
 * sin escribir señales durante el render. Si se elimina un registro y la página queda vacía, se
 * muestra la última página disponible.
 */
export class PaginacionLocal {
  readonly tamano = signal<number>(TAMANO_PAGINA_PREDETERMINADO);
  /** Página elegida y los filtros vigentes cuando se eligió. */
  private readonly estado = signal({ pagina: 0, clave: '' });
  private claveActual = '';

  constructor(tamano: number = TAMANO_PAGINA_PREDETERMINADO) {
    this.tamano.set(tamano);
  }

  /** Página efectiva (desde 0) para una lista de `total` registros. */
  pagina(total: number, clave: string = this.claveActual): number {
    const e = this.estado();
    const pedida = e.clave === clave ? e.pagina : 0;
    const ultima = Math.max(0, Math.ceil(total / this.tamano()) - 1);
    return Math.min(pedida, ultima);
  }

  /** Registros de la página actual. */
  ver<T>(lista: readonly T[], clave = ''): T[] {
    this.claveActual = clave;
    const inicio = this.pagina(lista.length, clave) * this.tamano();
    return lista.slice(inicio, inicio + this.tamano());
  }

  irA(pagina: number): void {
    this.estado.set({ pagina: Math.max(0, pagina), clave: this.claveActual });
  }

  cambiarTamano(tamano: number): void {
    this.tamano.set(tamano);
    this.reiniciar();
  }

  reiniciar(): void {
    this.estado.set({ pagina: 0, clave: this.claveActual });
  }
}

/**
 * Paginación del servidor: si la página recibida quedó vacía pero hay registros (por ejemplo, se
 * eliminó el último de esa página), devuelve la última página con datos para volver a pedirla.
 */
export function paginaDisponible(pg: { page: number; totalPages: number; content: readonly unknown[] }): number | null {
  return pg.content.length === 0 && pg.page > 0 && pg.totalPages > 0 ? pg.totalPages - 1 : null;
}
