import { DOCUMENT } from '@angular/common';
import { Component, ElementRef, OnDestroy, inject, input, output, viewChild } from '@angular/core';

/**
 * La ventana de las fichas de Usuarios (cuenta interna, acopiador y proveedor): misma
 * cabecera, cuerpo con scroll y pie fijo. Antes cada pantalla tenía su propio modal y se veían
 * distintos; ahora cambiar el aspecto de una ficha es cambiarlo aquí.
 *
 * Contenido proyectado: `[pasos]` debajo de la cabecera, el cuerpo por defecto y `[pie]` a la
 * derecha del pie (botones). `resumen` es el texto pequeño a la izquierda del pie.
 */
@Component({
  selector: 'app-ventana-ficha',
  standalone: true,
  styleUrl: '../accesos-dialog.css',
  template: `
    <dialog
      #ventana
      class="acc-dialog"
      [class.angosta]="angosta()"
      [attr.aria-label]="titulo()"
      (cancel)="alCancelar($event)"
      (close)="alCerrar()"
    >
      <header class="acc-header">
        <div>
          <div class="kicker">{{ kicker() }}</div>
          <h2>{{ titulo() }}</h2>
        </div>
        <button class="acc-close" type="button" aria-label="Cerrar" [disabled]="ocupado()" (click)="cerrar()">
          ×
        </button>
      </header>
      <ng-content select="[pasos]" />
      <div class="acc-body">
        @if (error()) {
          <p class="note red" role="alert">{{ error() }}</p>
        }
        @if (aviso()) {
          <p class="note" role="status">{{ aviso() }}</p>
        }
        <ng-content />
      </div>
      <footer class="acc-footer">
        <span>{{ resumen() }}</span>
        <div class="actions">
          <ng-content select="[pie]" />
        </div>
      </footer>
    </dialog>
  `,
})
export class VentanaFichaComponent implements OnDestroy {
  private readonly document = inject(DOCUMENT);
  private readonly ventana = viewChild.required<ElementRef<HTMLDialogElement>>('ventana');

  readonly kicker = input('');
  readonly titulo = input.required<string>();
  readonly resumen = input('');
  readonly error = input('');
  readonly aviso = input('');
  /** Mientras guarda no se puede cerrar: la respuesta del servidor quedaría sin nadie que la muestre. */
  readonly ocupado = input(false);
  /** Fichas cortas (acopiador, proveedor): la de permisos necesita el ancho completo. */
  readonly angosta = input(false);
  readonly cerrada = output<void>();

  private overflowAnterior: string | null = null;

  get abierta(): boolean {
    return this.ventana().nativeElement.open;
  }

  abrir(): void {
    const dialogo = this.ventana().nativeElement;
    if (dialogo.open) return;
    this.overflowAnterior = this.document.body.style.overflow;
    this.document.body.style.overflow = 'hidden';
    dialogo.showModal();
  }

  /**
   * Cierra sin mirar `ocupado`: la ficha la llama justo al recibir la respuesta, cuando el input
   * aún no ha recibido el `false` (no hubo detección de cambios) y se quedaría abierta. Lo que
   * impide cerrar mientras guarda es que × y Cancelar estén deshabilitados y que Esc se ignore.
   */
  cerrar(): void {
    this.ventana().nativeElement.close();
  }

  alCancelar(event: Event): void {
    event.preventDefault();
    if (!this.ocupado()) this.cerrar();
  }

  alCerrar(): void {
    this.restaurarScroll();
    this.cerrada.emit();
  }

  ngOnDestroy(): void {
    this.restaurarScroll();
  }

  private restaurarScroll(): void {
    if (this.overflowAnterior !== null) {
      this.document.body.style.overflow = this.overflowAnterior;
      this.overflowAnterior = null;
    }
  }
}
