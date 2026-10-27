import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NgForm } from '@angular/forms';

/**
 * Error general y errores por campo de una ficha. El backend responde 400/422 con `message` y, si
 * es de validación, `fields` ({ campo: motivo }); cada campo se marca en rojo con su motivo.
 */
export class ErroresForm {
  readonly general = signal('');
  readonly campos = signal<Record<string, string>>({});

  de(campo: string): string | null {
    return this.campos()[campo] ?? null;
  }

  limpiar(): void {
    this.general.set('');
    this.campos.set({});
  }

  mostrar(mensaje: string): void {
    this.general.set(mensaje);
  }

  /**
   * Antes de enviar: nombra solo lo que falta o está mal. Un mensaje fijo que listaba todos los campos
   * hacía creer que se pedía lo opcional (el DNI salía siempre, aunque faltara solo la ruta).
   */
  revisar(form: NgForm, etiquetas: Record<string, string>): boolean {
    const faltan: string[] = [];
    const mal: string[] = [];
    for (const [campo, etiqueta] of Object.entries(etiquetas)) {
      const control = form.controls[campo];
      if (!control || control.valid) continue;
      if (control.errors?.['required']) faltan.push(etiqueta);
      else mal.push(etiqueta);
    }
    if (!faltan.length && !mal.length) return true;
    form.control.markAllAsTouched();
    const partes = [
      faltan.length ? `Falta: ${faltan.join(', ')}.` : '',
      mal.length ? `Revisa el formato de: ${mal.join(', ')}.` : '',
    ];
    this.general.set(partes.filter(Boolean).join(' '));
    return false;
  }

  desdeServidor(e: HttpErrorResponse, porDefecto: string): void {
    const campos: Record<string, string> = e.error?.fields ?? {};
    this.campos.set(campos);
    this.general.set(
      Object.keys(campos).length ? 'Revisa los campos marcados en rojo.' : (e.error?.message ?? porDefecto),
    );
  }
}
