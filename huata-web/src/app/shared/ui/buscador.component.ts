import {
  Component,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  ViewChild,
  computed,
  forwardRef,
  signal,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

export interface OpcionBuscador {
  /** Lo que se guarda al elegir. `undefined` es la opción "todos / sin seleccionar". */
  valor: unknown;
  etiqueta: string;
  /** Texto secundario (zona, código…): se muestra y también se busca en él. */
  detalle?: string;
}

/** Dónde se dibuja la lista, en coordenadas de la ventana. */
interface PosicionLista {
  top: number | null;
  bottom: number | null;
  left: number;
  ancho: number;
  altoMaximo: number;
  haciaArriba: boolean;
}

/** Alto máximo de la lista y margen que se deja respecto al borde de la ventana. */
const ALTO_LISTA = 320;
const MARGEN = 8;
/** Por debajo de este espacio, si arriba hay más, la lista se abre hacia arriba. */
const ESPACIO_MINIMO = 200;
const ANCHO_MINIMO = 260;

/**
 * Selector en el que se puede ESCRIBIR para filtrar, en vez de un desplegable donde hay que bajar
 * a mano hasta encontrar la fila.
 *
 * <p>Con un `<select>` nativo, elegir un proveedor entre doscientos obliga a recorrer la lista
 * entera; aquí se teclea parte del nombre, del código o de la zona y la lista se reduce. Se
 * implementa {@link ControlValueAccessor} a propósito: los formularios siguen usando el mismo
 * `[(ngModel)]` que tenían con el `<select>`, así que cambiar un buscador no obliga a tocar la
 * lógica de la pantalla.
 *
 * <p>No se usó `<datalist>` (que sería más corto) porque devuelve el TEXTO escrito y no el valor
 * elegido: habría que adivinar a qué id corresponde lo tecleado, y dos proveedores que se llamen
 * igual lo romperían.
 *
 * <p><b>La lista se posiciona respecto a la ventana (`position: fixed`)</b>, no dentro de su
 * contenedor. Con `position: absolute` la cortaba cualquier ancestro con desplazamiento propio
 * (las tablas, el área principal) — y, en particular, la regla global `.card{overflow:hidden}`
 * (prototype-theme.css) recorta cualquier hijo `absolute` que se salga de una tarjeta, así que
 * quedaba a medias cerca del borde inferior. Ahora se calcula dónde cabe: se abre hacia abajo o,
 * si no hay sitio, hacia arriba, y sigue al campo al hacer scroll.
 */
@Component({
  selector: 'app-buscador',
  standalone: true,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BuscadorComponent),
      multi: true,
    },
  ],
  template: `
    <div class="buscador" [class.abierto]="abierto()">
      <input
        #entrada
        type="text"
        role="combobox"
        autocomplete="off"
        [attr.aria-expanded]="abierto()"
        [attr.aria-label]="etiqueta || placeholder"
        [attr.aria-controls]="listaId"
        [placeholder]="placeholder"
        [disabled]="deshabilitado()"
        [value]="texto()"
        (input)="alEscribir($any($event.target).value)"
        (focus)="abrir()"
        (keydown)="alTeclear($event)"
      />
      <button
        type="button"
        class="buscador-flecha"
        tabindex="-1"
        [disabled]="deshabilitado()"
        [attr.aria-label]="abierto() ? 'Cerrar lista' : 'Abrir lista'"
        (click)="alternar()"
      >
        ⌄
      </button>

      @if (abierto()) {
        <ul
          class="buscador-lista"
          [id]="listaId"
          role="listbox"
          [class.hacia-arriba]="posicion().haciaArriba"
          [style.top.px]="posicion().top"
          [style.bottom.px]="posicion().bottom"
          [style.left.px]="posicion().left"
          [style.width.px]="posicion().ancho"
          [style.max-height.px]="posicion().altoMaximo"
        >
          @for (o of filtradas(); track $index; let i = $index) {
            <li
              role="option"
              [attr.aria-selected]="o.valor === valor()"
              [class.resaltada]="i === resaltada()"
              [class.elegida]="o.valor === valor()"
              (mousedown)="elegir(o)"
              (mouseenter)="resaltada.set(i)"
            >
              <span>{{ o.etiqueta }}</span>
              @if (o.detalle) {
                <small>{{ o.detalle }}</small>
              }
            </li>
          } @empty {
            <li class="buscador-vacio" role="status">
              {{ sinCoincidencias ? sinCoincidencias(texto()) : 'Sin coincidencias para «' + texto() + '»' }}
            </li>
          }
        </ul>
      }
    </div>
  `,
  styleUrl: './buscador.component.css',
})
export class BuscadorComponent implements ControlValueAccessor, OnDestroy {
  /**
   * Señal y no campo: la lista filtrada y el texto mostrado dependen de ella, y las opciones suelen
   * llegar DESPUÉS del valor (un formulario que se abre con el insumo ya puesto).
   */
  private readonly _opciones = signal<OpcionBuscador[]>([]);
  @Input({ required: true }) set opciones(v: OpcionBuscador[] | null | undefined) {
    this._opciones.set(v ?? []);
  }
  get opciones(): OpcionBuscador[] {
    return this._opciones();
  }
  @Input() placeholder = 'Buscar…';
  /** Texto de la lista vacía según lo escrito: la pantalla puede explicar POR QUÉ no aparece. */
  @Input() sinCoincidencias?: (texto: string) => string;
  /** Etiqueta accesible cuando el campo no tiene un <label> asociado. */
  @Input() etiqueta = '';
  @Input() set disabled(v: boolean) {
    this.deshabilitado.set(v);
  }

  @ViewChild('entrada') entradaRef?: ElementRef<HTMLInputElement>;

  readonly listaId = `buscador-${Math.random().toString(36).slice(2, 9)}`;
  readonly valor = signal<unknown>(undefined);
  /** Lo tecleado. Solo cuenta mientras se está filtrando. */
  private readonly escrito = signal('');
  /** Mientras se filtra, el campo muestra lo tecleado; si no, la etiqueta de lo elegido. */
  private readonly filtrando = signal(false);
  readonly texto = computed(() => (this.filtrando() ? this.escrito() : this.etiquetaDe(this.valor())));
  readonly abierto = signal(false);
  readonly resaltada = signal(0);
  readonly deshabilitado = signal(false);
  readonly posicion = signal<PosicionLista>({
    top: null,
    bottom: null,
    left: 0,
    ancho: ANCHO_MINIMO,
    altoMaximo: ALTO_LISTA,
    haciaArriba: false,
  });

  /**
   * Un scroll dentro de CUALQUIER contenedor mueve el campo, y ese evento no llega a `window`
   * porque no burbujea: por eso se escucha en fase de captura sobre el documento.
   */
  private readonly alDesplazar = () => {
    if (this.abierto()) this.calcularPosicion();
  };
  private escuchando = false;

  /**
   * Todo lo que se lee aquí es señal. Antes `filtrando` era un campo común y el texto solo se leía
   * cuando era verdadero: al abrir la lista (filtrando = false) el computed quedaba sin dependencias
   * y no se recalculaba nunca más. Escribir «sal» seguía mostrando todas las opciones y Enter
   * elegía la primera («cuajo»). Ver la prueba de regresión en buscador.component.spec.ts.
   */
  readonly filtradas = computed(() => {
    const opciones = this._opciones();
    const q = this.filtrando() ? normalizar(this.escrito()) : '';
    if (!q) return opciones;
    return opciones.filter((o) => normalizar(`${o.etiqueta} ${o.detalle ?? ''}`).includes(q));
  });

  private alCambiar: (v: unknown) => void = () => {};
  private alTocar: () => void = () => {};

  // ---- ControlValueAccessor ----

  writeValue(v: unknown): void {
    this.valor.set(v);
    this.filtrando.set(false);
  }

  registerOnChange(fn: (v: unknown) => void): void {
    this.alCambiar = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.alTocar = fn;
  }

  setDisabledState(v: boolean): void {
    this.deshabilitado.set(v);
  }

  // ---- Interacción ----

  alEscribir(v: string): void {
    this.filtrando.set(true);
    this.escrito.set(v);
    this.resaltada.set(0);
    this.mostrarLista();
  }

  abrir(): void {
    if (this.deshabilitado()) return;
    this.filtrando.set(false);
    this.mostrarLista();
    this.resaltada.set(Math.max(0, this.filtradas().findIndex((o) => o.valor === this.valor())));
  }

  alternar(): void {
    if (this.abierto()) {
      this.cerrar();
    } else {
      this.abrir();
      this.entradaRef?.nativeElement.focus();
    }
  }

  /**
   * Al cerrar se restaura lo elegido: escribir a medias no cambia la selección. La única excepción
   * es haber escrito EXACTAMENTE el nombre de una opción (sin importar tildes ni mayúsculas): eso
   * no es escribir a medias, y salir con Tab o un clic afuera la elige.
   */
  cerrar(): void {
    const exacta = this.filtrando() ? this.opcionExacta() : null;
    if (exacta) {
      this.elegir(exacta);
      return;
    }
    this.abierto.set(false);
    this.dejarDeEscuchar();
    this.filtrando.set(false);
    this.alTocar();
  }

  elegir(o: OpcionBuscador): void {
    this.valor.set(o.valor);
    this.filtrando.set(false);
    this.abierto.set(false);
    this.dejarDeEscuchar();
    this.alCambiar(o.valor);
    this.alTocar();
  }

  alTeclear(evento: KeyboardEvent): void {
    if (this.deshabilitado()) return;
    const opciones = this.filtradas();
    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        if (!this.abierto()) this.abrir();
        else this.resaltada.update((i) => Math.min(i + 1, opciones.length - 1));
        break;
      case 'ArrowUp':
        evento.preventDefault();
        this.resaltada.update((i) => Math.max(i - 1, 0));
        break;
      case 'Enter': {
        if (!this.abierto()) return;
        evento.preventDefault();
        const elegida = opciones[this.resaltada()];
        if (elegida) this.elegir(elegida);
        break;
      }
      case 'Escape':
        if (this.abierto()) {
          evento.preventDefault();
          this.abierto.set(false);
          this.dejarDeEscuchar();
          this.filtrando.set(false);
        }
        break;
      case 'Tab':
        // Sin esto la lista quedaba abierta encima del campo siguiente.
        if (this.abierto()) this.cerrar();
        break;
      default:
        break;
    }
  }

  @HostListener('document:mousedown', ['$event'])
  alClicFuera(evento: MouseEvent): void {
    if (!this.abierto()) return;
    if (!this.entradaRef?.nativeElement.closest('.buscador')?.contains(evento.target as Node)) {
      this.cerrar();
    }
  }

  @HostListener('window:resize')
  alRedimensionar(): void {
    if (this.abierto()) this.calcularPosicion();
  }

  ngOnDestroy(): void {
    this.dejarDeEscuchar();
  }

  private mostrarLista(): void {
    this.calcularPosicion();
    this.abierto.set(true);
    if (!this.escuchando && typeof document !== 'undefined') {
      document.addEventListener('scroll', this.alDesplazar, true);
      this.escuchando = true;
    }
  }

  private dejarDeEscuchar(): void {
    if (this.escuchando) {
      document.removeEventListener('scroll', this.alDesplazar, true);
      this.escuchando = false;
    }
  }

  /**
   * Coloca la lista bajo el campo o, si abajo no cabe y arriba hay más sitio, encima. El alto se
   * limita al espacio disponible para que la lista nunca quede cortada por el borde de la ventana.
   */
  private calcularPosicion(): void {
    const campo = this.entradaRef?.nativeElement.closest('.buscador') as HTMLElement | null;
    if (!campo || typeof window === 'undefined') return;
    const r = campo.getBoundingClientRect();
    const altoVentana = window.innerHeight;
    const anchoVentana = window.innerWidth;

    const espacioAbajo = altoVentana - r.bottom - MARGEN;
    const espacioArriba = r.top - MARGEN;
    const haciaArriba = espacioAbajo < ESPACIO_MINIMO && espacioArriba > espacioAbajo;

    const ancho = Math.min(Math.max(r.width, ANCHO_MINIMO), anchoVentana - 2 * MARGEN);
    const left = Math.max(MARGEN, Math.min(r.left, anchoVentana - ancho - MARGEN));

    this.posicion.set({
      top: haciaArriba ? null : r.bottom + 4,
      bottom: haciaArriba ? altoVentana - r.top + 4 : null,
      left,
      ancho,
      altoMaximo: Math.max(120, Math.min(ALTO_LISTA, haciaArriba ? espacioArriba : espacioAbajo)),
      haciaArriba,
    });
  }

  private etiquetaDe(v: unknown): string {
    return this._opciones().find((o) => o.valor === v)?.etiqueta ?? '';
  }

  /** La única opción cuyo nombre es exactamente lo escrito, o null. */
  private opcionExacta(): OpcionBuscador | null {
    const q = normalizar(this.escrito());
    if (!q) return null;
    const iguales = this._opciones().filter((o) => normalizar(o.etiqueta) === q);
    return iguales.length === 1 ? iguales[0] : null;
  }
}

/** Sin tildes, sin mayúsculas y sin espacios de sobra: «Lúcuma» se encuentra escribiendo «lucuma». */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es')
    .replace(/\s+/g, ' ')
    .trim();
}
