import { Component, DestroyRef, ElementRef, inject, output, signal } from '@angular/core';
import { Observable, Subscription, catchError, forkJoin, map, of } from 'rxjs';
import { PERMISO } from '../../../core/config/navegacion';
import { PageResponse } from '../../../core/models/models';
import { AcopiadoresService } from '../../../core/services/acopiadores.service';
import { AuthService } from '../../../core/services/auth.service';
import { ProductoresService } from '../../../core/services/productores.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import type { Pestana } from '../usuarios.component';

interface Coincidencia {
  titulo: string;
  detalle: string;
}

interface Grupo {
  tipo: Pestana;
  etiqueta: string;
  total: number;
  filas: Coincidencia[];
}

/** Cuántas coincidencias se muestran por tipo; el resto se ve al abrir la pestaña. */
const POR_TIPO = 4;

/**
 * Busca a la vez en cuentas internas, acopiadores y proveedores (solo en los que la sesión puede ver)
 * y muestra lo encontrado agrupado. Elegir un grupo o una persona abre su pestaña con la búsqueda
 * ya puesta. Usa los mismos buscadores del backend: cada palabra, en cualquier dato y sin tildes.
 */
@Component({
  selector: 'app-buscador-general',
  standalone: true,
  host: { '(document:click)': 'alClicFuera($event)' },
  template: `
    <div class="bg">
      <input
        type="search"
        class="bg-input"
        placeholder="Buscar en todos: nombre, DNI o código, teléfono, correo, ruta o zona…"
        aria-label="Buscar en todos los usuarios"
        [value]="texto()"
        (input)="escribir($any($event.target).value)"
        (focus)="abierto.set(!!texto())"
        (keydown.escape)="abierto.set(false)"
      />
      @if (abierto() && texto().trim().length >= 2) {
        <div class="bg-panel" role="listbox" aria-label="Resultados">
          @if (buscando()) {
            <p class="bg-vacio">Buscando…</p>
          } @else if (!grupos().length) {
            <p class="bg-vacio">Nadie coincide con «{{ texto() }}».</p>
          }
          @for (g of grupos(); track g.tipo) {
            <section class="bg-grupo">
              <button type="button" class="bg-cabecera" (click)="elegir(g.tipo, texto())">
                <span>{{ g.etiqueta }}</span>
                <small>{{ g.total }} {{ g.total === 1 ? 'resultado' : 'resultados' }} · ver todos</small>
              </button>
              @for (f of g.filas; track $index) {
                <button type="button" class="bg-fila" role="option" (click)="elegir(g.tipo, f.titulo)">
                  <strong>{{ f.titulo }}</strong>
                  <small>{{ f.detalle }}</small>
                </button>
              }
            </section>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .bg {
      position: relative;
      margin-bottom: 14px;
    }
    .bg-input {
      width: 100%;
      box-sizing: border-box;
      border: 1px solid var(--line);
      border-radius: 10px;
      padding: 10px 14px;
      font-size: 13px;
      background: #fff;
    }
    .bg-panel {
      position: absolute;
      top: calc(100% + 6px);
      left: 0;
      right: 0;
      z-index: 20;
      max-height: 420px;
      overflow: auto;
      padding: 6px;
      border: 1px solid var(--line);
      border-radius: 10px;
      background: #fff;
      box-shadow: 0 12px 32px rgba(16, 43, 37, 0.14);
    }
    .bg-grupo + .bg-grupo {
      margin-top: 4px;
      padding-top: 4px;
      border-top: 1px solid #efece5;
    }
    .bg-cabecera,
    .bg-fila {
      display: flex;
      width: 100%;
      align-items: baseline;
      justify-content: space-between;
      gap: 12px;
      padding: 7px 10px;
      border: 0;
      border-radius: 7px;
      background: none;
      color: var(--text);
      font: inherit;
      text-align: left;
      cursor: pointer;
    }
    .bg-cabecera span {
      font-size: 10.5px;
      font-weight: 700;
      letter-spacing: 0.06em;
      text-transform: uppercase;
      color: var(--muted);
    }
    .bg-cabecera small {
      font-size: 11px;
      color: var(--green);
    }
    .bg-fila strong {
      font-size: 12.5px;
      font-weight: 600;
    }
    .bg-fila small {
      font-size: 11px;
      color: var(--muted);
      white-space: nowrap;
    }
    .bg-cabecera:hover,
    .bg-fila:hover,
    .bg-cabecera:focus-visible,
    .bg-fila:focus-visible {
      background: #f2f5f1;
      outline: none;
    }
    .bg-vacio {
      margin: 0;
      padding: 10px;
      font-size: 12px;
      color: var(--muted);
    }
  `,
})
export class BuscadorGeneralComponent {
  private readonly auth = inject(AuthService);
  private readonly usuarios = inject(UsuariosService);
  private readonly acopiadores = inject(AcopiadoresService);
  private readonly productores = inject(ProductoresService);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  /** Abre la pestaña con ese texto en su buscador. */
  readonly elegido = output<{ tipo: Pestana; q: string }>();

  readonly texto = signal('');
  readonly abierto = signal(false);
  readonly buscando = signal(false);
  readonly grupos = signal<Grupo[]>([]);

  private pausa?: ReturnType<typeof setTimeout>;
  private consulta?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      clearTimeout(this.pausa);
      this.consulta?.unsubscribe();
    });
  }

  escribir(valor: string): void {
    this.texto.set(valor);
    this.abierto.set(true);
    clearTimeout(this.pausa);
    if (valor.trim().length < 2) {
      this.grupos.set([]);
      return;
    }
    this.buscando.set(true);
    this.pausa = setTimeout(() => this.buscar(valor.trim()), 300);
  }

  /** La búsqueda pasa al buscador de la pestaña: aquí se vacía para no tener dos a la vez. */
  elegir(tipo: Pestana, q: string): void {
    this.abierto.set(false);
    this.texto.set('');
    this.grupos.set([]);
    this.elegido.emit({ tipo, q });
  }

  alClicFuera(evento: Event): void {
    if (!this.elemento.nativeElement.contains(evento.target as Node)) this.abierto.set(false);
  }

  private buscar(q: string): void {
    this.consulta?.unsubscribe();
    const pedidos: Observable<Grupo>[] = [];
    if (this.auth.puede(PERMISO.usuariosRead)) {
      pedidos.push(
        grupo('internos', 'Internos', this.usuarios.listar(q, 0, POR_TIPO, 'ACTIVOS', true), (u) => ({
          titulo: u.nombreCompleto,
          detalle: u.username,
        })),
      );
    }
    if (this.auth.puede(PERMISO.acopiadoresRead)) {
      pedidos.push(
        grupo('acopiadores', 'Acopiadores', this.acopiadores.buscar({ q, size: POR_TIPO }), (a) => ({
          titulo: `${a.nombres} ${a.apellidos}`,
          detalle: [a.zona, a.rutaNombre].filter(Boolean).join(' · '),
        })),
      );
    }
    if (this.auth.puede(PERMISO.proveedoresRead)) {
      pedidos.push(
        grupo('proveedores', 'Proveedores', this.productores.listar({ q, size: POR_TIPO }), (p) => ({
          titulo: `${p.nombres} ${p.apellidos}`,
          detalle: [p.codigo, p.zonaPrincipal ?? p.rutaNombre].filter(Boolean).join(' · '),
        })),
      );
    }
    this.consulta = forkJoin(pedidos).subscribe((grupos) => {
      this.grupos.set(grupos.filter((g) => g.total > 0));
      this.buscando.set(false);
    });
  }
}

/** Un tipo de persona: si su consulta falla, no tumba a los demás (sale vacío). */
function grupo<T>(
  tipo: Pestana,
  etiqueta: string,
  consulta: Observable<PageResponse<T>>,
  fila: (x: T) => Coincidencia,
): Observable<Grupo> {
  return consulta.pipe(
    map((pg) => ({ tipo, etiqueta, total: pg.totalElements, filas: pg.content.map(fila) })),
    catchError(() => of({ tipo, etiqueta, total: 0, filas: [] })),
  );
}
