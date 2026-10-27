import { Component, ElementRef, HostListener, effect, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogoService, TipoDialogo } from '../../core/notificaciones/dialogo.service';

const ICONO: Record<TipoDialogo, string> = { confirmacion: '?', peligro: '!', aviso: '!', info: 'i' };

/**
 * Ventana de confirmación única de la app. Se coloca una sola vez en la raíz y muestra lo que
 * pida DialogoService. Accesible: role="alertdialog", foco atrapado dentro mientras está
 * abierta, Escape cancela y al cerrar el foco vuelve a donde estaba.
 *
 * Seguridad al cerrar: Escape y clic fuera cancelan solo si no hay riesgo de perder lo escrito;
 * en una acción peligrosa el clic fuera no hace nada (hay que elegir un botón).
 */
@Component({
  selector: 'app-dialogo',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (servicio.actual(); as d) {
      <div class="fondo" (mousedown)="clicFuera($event)">
        <div
          #caja
          class="caja"
          [class]="'caja caja-' + tipo()"
          role="alertdialog"
          aria-modal="true"
          [attr.aria-labelledby]="'dlg-titulo-' + d.id"
          [attr.aria-describedby]="d.opciones.mensaje ? 'dlg-mensaje-' + d.id : null"
          (keydown)="teclado($event)"
        >
          <div class="cabecera">
            <span class="icono" aria-hidden="true">{{ icono[tipo()] }}</span>
            <h2 [id]="'dlg-titulo-' + d.id">{{ d.opciones.titulo }}</h2>
            <button type="button" class="cerrar" (click)="cancelar()" aria-label="Cerrar">×</button>
          </div>
          @if (d.opciones.mensaje) {
            <p class="mensaje" [id]="'dlg-mensaje-' + d.id">{{ d.opciones.mensaje }}</p>
          }
          <form (ngSubmit)="aceptar()">
            @if (d.conTexto) {
              <label class="campo" [for]="'dlg-texto-' + d.id">
                {{ d.opciones.etiqueta }}
                @if (!d.opciones.obligatorio) {
                  <span class="opcional">(opcional)</span>
                }
              </label>
              <textarea
                [id]="'dlg-texto-' + d.id"
                name="texto"
                rows="3"
                [(ngModel)]="texto"
                [placeholder]="d.opciones.placeholder ?? ''"
                [attr.aria-required]="d.opciones.obligatorio ? 'true' : null"
                data-foco
              ></textarea>
            }
            <div class="botones">
              <button type="button" class="btn-cancelar" (click)="cancelar()" [attr.data-foco]="tipo() === 'peligro' && !d.conTexto ? '' : null">
                {{ d.opciones.cancelar ?? 'Cancelar' }}
              </button>
              <button
                type="submit"
                class="btn-confirmar"
                [disabled]="d.conTexto && d.opciones.obligatorio && !texto.trim()"
                [attr.data-foco]="tipo() !== 'peligro' && !d.conTexto ? '' : null"
              >
                {{ d.opciones.confirmar ?? 'Aceptar' }}
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
  styleUrl: './dialogo.css',
})
export class DialogoHostComponent {
  protected readonly servicio = inject(DialogoService);
  protected readonly icono = ICONO;
  protected texto = '';
  private readonly caja = viewChild<ElementRef<HTMLElement>>('caja');
  private focoAnterior: HTMLElement | null = null;

  constructor() {
    // Al abrir: recuerda el foco, prepara el texto y enfoca el control indicado. Al cerrar: lo devuelve.
    effect(() => {
      const d = this.servicio.actual();
      // Mientras hay una confirmación abierta el fondo no se desplaza ni recibe clics.
      document.body.style.overflow = d ? 'hidden' : '';
      if (d) {
        if (!this.focoAnterior) this.focoAnterior = document.activeElement as HTMLElement | null;
        this.texto = d.opciones.valorInicial ?? '';
        queueMicrotask(() => this.caja()?.nativeElement.querySelector<HTMLElement>('[data-foco]')?.focus());
      } else if (this.focoAnterior) {
        const volver = this.focoAnterior;
        this.focoAnterior = null;
        queueMicrotask(() => volver.focus?.());
      }
    });
  }

  protected tipo(): TipoDialogo {
    return this.servicio.actual()?.opciones.tipo ?? 'confirmacion';
  }

  aceptar(): void {
    const d = this.servicio.actual();
    if (!d) return;
    if (d.conTexto) {
      if (d.opciones.obligatorio && !this.texto.trim()) return;
      this.servicio.responder(this.texto.trim());
    } else {
      this.servicio.responder(true);
    }
  }

  cancelar(): void {
    this.servicio.responder(this.servicio.actual()?.conTexto ? null : false);
  }

  /** Cerrar sin elegir solo si no se pierde nada: nunca en peligro ni con texto escrito. */
  private puedeCerrarseSolo(): boolean {
    return this.tipo() !== 'peligro' && !this.texto.trim();
  }

  clicFuera(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget && this.puedeCerrarseSolo()) this.cancelar();
  }

  @HostListener('document:keydown.escape', ['$event'])
  escape(evento: Event): void {
    if (!this.servicio.actual()) return;
    evento.preventDefault();
    if (this.tipo() === 'peligro' || !this.texto.trim()) this.cancelar();
  }

  /** Mantiene el Tab dentro de la ventana mientras está abierta. */
  teclado(evento: KeyboardEvent): void {
    if (evento.key !== 'Tab') return;
    const enfocables = [
      ...(this.caja()?.nativeElement.querySelectorAll<HTMLElement>('button:not([disabled]), textarea, input') ?? []),
    ];
    if (!enfocables.length) return;
    const primero = enfocables[0];
    const ultimo = enfocables[enfocables.length - 1];
    if (evento.shiftKey && document.activeElement === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }
}
