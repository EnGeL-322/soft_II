import { Component, computed, input } from '@angular/core';

export interface PuntoSerie {
  etiqueta: string;
  valor: number;
  valorSecundario?: number;
}

const ANCHO = 720;
const ALTO = 220;
const MARGEN_X = 44;
const MARGEN_Y = 18;

/**
 * Gráfico de línea/área en SVG puro (sin librería externa): escala sola, se adapta al ancho del
 * contenedor y usa los mismos tokens de color del sistema.
 */
@Component({
  selector: 'app-grafico-lineas',
  standalone: true,
  template: `
    @if (puntos().length > 1) {
      <svg
        [attr.viewBox]="'0 0 ' + ancho + ' ' + alto"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="etiquetaAccesible()"
        style="width:100%; height:100%; display:block"
      >
        @for (linea of lineasGuia(); track linea.y) {
          <line [attr.x1]="margenX" [attr.x2]="ancho - 8" [attr.y1]="linea.y" [attr.y2]="linea.y" stroke="#e6dcc4" stroke-width="1" />
          <text [attr.x]="4" [attr.y]="linea.y + 4" font-size="10" fill="#7a6f57">{{ linea.texto }}</text>
        }

        @if (areaPath()) {
          <path [attr.d]="areaPath()" fill="rgba(83, 148, 121, 0.18)" />
        }
        <polyline [attr.points]="puntosLinea()" fill="none" stroke="#336751" stroke-width="2.5" stroke-linejoin="round" />

        @if (mostrarSecundaria()) {
          <polyline
            [attr.points]="puntosLineaSecundaria()"
            fill="none"
            stroke="#9d7736"
            stroke-width="2"
            stroke-dasharray="5 4"
          />
        }

        @for (p of coordenadas(); track p.x) {
          <circle [attr.cx]="p.x" [attr.cy]="p.y" r="3" fill="#fbf7ec" stroke="#336751" stroke-width="2" />
        }

        @for (e of etiquetasX(); track e.x) {
          <text [attr.x]="e.x" [attr.y]="alto - 2" font-size="10" fill="#7a6f57" text-anchor="middle">{{ e.texto }}</text>
        }
      </svg>
    } @else {
      <p class="muted" style="padding:16px">Sin datos suficientes para graficar.</p>
    }
  `,
})
export class GraficoLineasComponent {
  puntos = input.required<PuntoSerie[]>();
  etiquetaSerie = input('Serie');
  etiquetaSerieSecundaria = input('Serie secundaria');

  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly margenX = MARGEN_X;

  protected readonly maximo = computed(() => {
    const valores = this.puntos().flatMap((p) => [p.valor, p.valorSecundario ?? 0]);
    return Math.max(1, ...valores);
  });

  protected readonly mostrarSecundaria = computed(() => this.puntos().some((p) => (p.valorSecundario ?? 0) > 0));

  private x(indice: number): number {
    const paso = (ANCHO - MARGEN_X - 12) / Math.max(1, this.puntos().length - 1);
    return MARGEN_X + indice * paso;
  }

  private y(valor: number): number {
    const alto = ALTO - MARGEN_Y * 2 - 12;
    return MARGEN_Y + alto - (valor / this.maximo()) * alto;
  }

  protected readonly coordenadas = computed(() => this.puntos().map((p, i) => ({ x: this.x(i), y: this.y(p.valor) })));

  protected readonly puntosLinea = computed(() => this.coordenadas().map((c) => `${c.x},${c.y}`).join(' '));

  protected readonly puntosLineaSecundaria = computed(() =>
    this.puntos().map((p, i) => `${this.x(i)},${this.y(p.valorSecundario ?? 0)}`).join(' '),
  );

  protected readonly areaPath = computed(() => {
    const coords = this.coordenadas();
    if (coords.length < 2) return '';
    const base = ALTO - MARGEN_Y - 12;
    const inicio = `M ${coords[0].x} ${base}`;
    const linea = coords.map((c) => `L ${c.x} ${c.y}`).join(' ');
    return `${inicio} ${linea} L ${coords[coords.length - 1].x} ${base} Z`;
  });

  protected readonly lineasGuia = computed(() => {
    const max = this.maximo();
    return [0, 0.5, 1].map((fraccion) => ({
      y: this.y(max * fraccion),
      texto: Math.round(max * fraccion).toString(),
    }));
  });

  /** Solo algunas etiquetas del eje X para que no se amontonen. */
  protected readonly etiquetasX = computed(() => {
    const total = this.puntos().length;
    const paso = Math.max(1, Math.ceil(total / 7));
    return this.puntos()
      .map((p, i) => ({ x: this.x(i), texto: p.etiqueta, indice: i }))
      .filter((e) => e.indice % paso === 0);
  });

  protected readonly etiquetaAccesible = computed(
    () => `${this.etiquetaSerie()}: ${this.puntos().length} puntos, máximo ${Math.round(this.maximo())}`,
  );
}
