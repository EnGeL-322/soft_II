import { Component, computed, input, output } from '@angular/core';

/** Paginador reutilizable: junto al buscador de cada listado forma el patron "buscador + filtros + paginación". */
@Component({
  selector: 'app-paginador',
  standalone: true,
  template: `
    <div class="paginador">
      <span>{{ totalElements() }} resultados · página {{ page() + 1 }} de {{ totalPaginas() }}</span>
      <button type="button" (click)="anterior.emit()" [disabled]="page() === 0">‹ Anterior</button>
      <button type="button" (click)="siguiente.emit()" [disabled]="page() + 1 >= totalPaginas()">Siguiente ›</button>
    </div>
  `,
})
export class PaginadorComponent {
  page = input(0);
  totalElements = input(0);
  totalPages = input(1);

  anterior = output<void>();
  siguiente = output<void>();

  totalPaginas = computed(() => Math.max(this.totalPages(), 1));
}
