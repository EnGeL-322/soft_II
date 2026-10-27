import { HttpClient } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api-config';

export interface FilaImportacion {
  fila: number;
  codigo: string;
  nombre: string;
  accion: 'CREAR' | 'ACTUALIZAR' | 'ERROR';
  mensaje: string | null;
}

export interface ResultadoImportacion {
  aplicado: boolean;
  crear: number;
  actualizar: number;
  errores: number;
  filas: FilaImportacion[];
}

/**
 * Importar el padrón desde Excel (respaldo.write + proveedores.write). Siempre en dos pasos: el
 * backend primero hace la vista previa (no guarda nada) y solo con "Confirmar" aplica. Si alguna
 * fila tiene error, no se aplica ninguna; el mismo Excel que exporta esta pantalla sirve de plantilla.
 */
@Component({
  selector: 'app-importar-proveedores',
  standalone: true,
  template: `
    <section class="card no-print" aria-label="Importar proveedores desde Excel">
      <div class="table-title">
        <div>
          <h3>Importar proveedores desde Excel</h3>
          <p>
            Usa el Excel de <b>Exportar Excel</b> como plantilla. Si el código (DNI) existe se actualizan nombres,
            apellidos, teléfono y ruta; si no, se crea ya activo (necesita Usuario y Clave_inicial).
            Nunca borra ni cambia estados.
          </p>
        </div>
        <button type="button" class="btn btn-soft" (click)="cerrar.emit()">Cerrar</button>
      </div>

      <input type="file" accept=".xlsx" (change)="elegir($event)" [disabled]="cargando()" />

      @if (error()) {
        <p class="note red" role="alert">{{ error() }}</p>
      }

      @if (resultado(); as r) {
        <p class="note" [class.red]="r.errores > 0">
          @if (r.aplicado) {
            Importado: {{ r.crear }} altas y {{ r.actualizar }} actualizaciones.
          } @else {
            Vista previa: {{ r.crear }} altas · {{ r.actualizar }} actualizaciones · {{ r.errores }} con error.
            @if (r.errores > 0) { Corrige el archivo: si una fila tiene error no se importa ninguna. }
          }
        </p>
        <div class="tablebox">
          <table>
            <thead><tr><th>Fila</th><th>Código</th><th>Proveedor</th><th>Acción</th><th>Detalle</th></tr></thead>
            <tbody>
              @for (f of r.filas; track f.fila) {
                <tr>
                  <td>{{ f.fila }}</td>
                  <td>{{ f.codigo }}</td>
                  <td>{{ f.nombre }}</td>
                  <td>
                    <span class="badge" [class.badge-verde]="f.accion === 'CREAR'" [class.badge-azul]="f.accion === 'ACTUALIZAR'"
                      [class.badge-rojo]="f.accion === 'ERROR'">{{ etiqueta[f.accion] }}</span>
                  </td>
                  <td class="wrap">{{ f.mensaje ?? '' }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        @if (!r.aplicado && r.errores === 0) {
          <button type="button" class="btn btn-primary" [disabled]="cargando()" (click)="confirmar()">
            {{ cargando() ? 'Importando…' : 'Confirmar importación' }}
          </button>
        }
      }
    </section>
  `,
})
export class ImportarProveedoresComponent {
  private readonly http = inject(HttpClient);

  readonly cerrar = output<void>();
  /** Tras aplicar, para que la lista se recargue. */
  readonly importado = output<ResultadoImportacion>();

  readonly resultado = signal<ResultadoImportacion | null>(null);
  readonly error = signal('');
  readonly cargando = signal(false);
  readonly etiqueta = { CREAR: 'Alta', ACTUALIZAR: 'Actualiza', ERROR: 'Error' } as const;
  private archivo: File | null = null;

  elegir(evento: Event): void {
    this.archivo = (evento.target as HTMLInputElement).files?.[0] ?? null;
    this.resultado.set(null);
    if (this.archivo) this.enviar(false);
  }

  confirmar(): void {
    this.enviar(true);
  }

  private enviar(confirmar: boolean): void {
    if (!this.archivo || this.cargando()) return;
    const datos = new FormData();
    datos.append('archivo', this.archivo);
    this.cargando.set(true);
    this.error.set('');
    this.http
      .post<ResultadoImportacion>(`${API_BASE_URL}/importaciones/proveedores`, datos, { params: { confirmar } })
      .pipe(finalize(() => this.cargando.set(false)))
      .subscribe({
        next: (r) => {
          this.resultado.set(r);
          if (r.aplicado) this.importado.emit(r);
        },
        error: (e) => this.error.set(e.error?.message ?? 'No se pudo procesar el archivo.'),
      });
  }
}
