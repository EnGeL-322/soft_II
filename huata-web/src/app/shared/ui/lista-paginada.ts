import { DestroyRef, inject, signal } from '@angular/core';
import { Observable, Subscription, finalize } from 'rxjs';
import { PageResponse } from '../../core/models/models';
import { paginaDisponible } from './paginacion-local';
import { TAMANO_PAGINA_PREDETERMINADO } from './paginador.component';

/**
 * Estado de una tabla paginada en el servidor: página, tamaño, carga y error. Cada pantalla repetía
 * lo mismo (buscar → página 0, cambiar página, cambiar tamaño, cancelar la consulta anterior, saltar
 * a la última página con datos si se borró el último registro); aquí está una sola vez.
 *
 * Se crea en un contexto de inyección (campo de un componente) y se cancela sola al destruirlo.
 *
 *   readonly lista = new ListaPaginada((page, size) => this.servicio.listar({ ...filtros, page, size }));
 *   <app-paginador [page]="lista.pagina()?.page ?? 0" [size]="lista.tamano()" ... />
 */
export class ListaPaginada<T> {
  readonly pagina = signal<PageResponse<T> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly tamano = signal<number>(TAMANO_PAGINA_PREDETERMINADO);

  private numero = 0;
  private consulta?: Subscription;
  private pausa?: ReturnType<typeof setTimeout>;

  constructor(
    private readonly consultar: (page: number, size: number) => Observable<PageResponse<T>>,
    private readonly mensajeError = 'No se pudo cargar la lista.',
  ) {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.pausa);
      this.consulta?.unsubscribe();
    });
  }

  /** Vuelve a la primera página: para cuando cambian los filtros. */
  buscar(): void {
    this.numero = 0;
    this.recargar();
  }

  /** Para el buscador de texto: espera a que se deje de escribir en vez de consultar por cada tecla. */
  buscarAlEscribir(): void {
    clearTimeout(this.pausa);
    this.pausa = setTimeout(() => this.buscar(), 300);
  }

  irA(page: number): void {
    this.numero = page;
    this.recargar();
  }

  cambiarTamano(size: number): void {
    this.tamano.set(size);
    this.buscar();
  }

  /** Repite la consulta en la página actual (tras guardar o eliminar). */
  recargar(): void {
    this.consulta?.unsubscribe();
    this.cargando.set(true);
    this.error.set('');
    this.consulta = this.consultar(this.numero, this.tamano())
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (pg) => {
          const otra = paginaDisponible(pg);
          if (otra !== null) this.irA(otra);
          else this.pagina.set(pg);
        },
        error: () => this.error.set(this.mensajeError),
      });
  }
}
