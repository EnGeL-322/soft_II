import { Component, computed, input } from '@angular/core';

export interface BarraSerie {
  etiqueta: string;
  valor: number;
  valorSecundario?: number;
  destacado?: boolean;
}

const ANCHO = 720;
const ALTO = 240;
const MARGEN_X = 44;
const MARGEN_SUP = 14;
const MARGEN_INF = 34;

/** Gráfico de barras (una o dos series) en SVG puro, con la paleta del sistema. */
@Component({
  selector: 'app-grafico-barras',
  standalone: true,
  template: `
    @if (barras().length > 0) {
      <svg
        [attr.viewBox]="'0 0 ' + ancho + ' ' + alto"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="etiquetaAccesible()"
        style="width:100%; height:100%; display:block"
      >
        @for (linea of lineasGuia(); track linea.y) {
          <line [attr.x1]="margenX" [attr.x2]="ancho - 8" [attr.y1]="linea.y" [attr.y2]="linea.y" stroke="#e6dcc4" stroke-width="1" />
          <text x="4" [attr.y]="linea.y + 4" font-size="10" fill="#7a6f57">{{ linea.texto }}</text>
        }

        @for (b of geometria(); track b.etiqueta) {
          <rect
            [attr.x]="b.x"
            [attr.y]="b.y"
            [attr.width]="b.ancho"
            [attr.height]="b.alto"
            rx="3"
            [attr.fill]="b.destacado ? '#a15344' : '#539479'"
          />
          @if (b.altoSecundario !== null) {
            <rect
              [attr.x]="b.x + b.ancho + 2"
              [attr.y]="b.ySecundario"
              [attr.width]="b.ancho"
              [attr.height]="b.altoSecundario"
              rx="3"
              fill="#9d7736"
            />
          }
          <text [attr.x]="b.xTexto" [attr.y]="alto - 12" font-size="10" fill="#7a6f57" text-anchor="middle">
            {{ b.etiqueta }}
          </text>
        }
      </svg>
    } @else {
      <p class="muted" style="padding:16px">Sin datos para graficar.</p>
    }
  `,
})
export class GraficoBarrasComponent {
  barras = input.required<BarraSerie[]>();
  etiquetaSerie = input('Serie');

  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly margenX = MARGEN_X;

  protected readonly maximo = computed(() => {
    const valores = this.barras().flatMap((b) => [b.valor, b.valorSecundario ?? 0]);
    return Math.max(1, ...valores);
  });

  private altoUtil(): number {
    return ALTO - MARGEN_SUP - MARGEN_INF;
  }

  private y(valor: number): number {
    return MARGEN_SUP + this.altoUtil() - (valor / this.maximo()) * this.altoUtil();
  }

  protected readonly geometria = computed(() => {
    const barras = this.barras();
    const dosSeries = barras.some((b) => b.valorSecundario != null);
    const anchoDisponible = ANCHO - MARGEN_X - 12;
    const paso = anchoDisponible / barras.length;
    const anchoBarra = Math.max(6, (paso * 0.55) / (dosSeries ? 2 : 1));

    return barras.map((b, i) => {
      const xBase = MARGEN_X + i * paso + (paso - anchoBarra * (dosSeries ? 2 : 1)) / 2;
      const yPrincipal = this.y(b.valor);
      const ySecundario = b.valorSecundario != null ? this.y(b.valorSecundario) : 0;
      const base = MARGEN_SUP + this.altoUtil();
      return {
        etiqueta: b.etiqueta,
        destacado: !!b.destacado,
        x: xBase,
        ancho: anchoBarra,
        y: yPrincipal,
        alto: Math.max(1, base - yPrincipal),
        ySecundario,
        altoSecundario: b.valorSecundario != null ? Math.max(1, base - ySecundario) : null,
        xTexto: xBase + anchoBarra * (dosSeries ? 1 : 0.5),
      };
    });
  });

  protected readonly lineasGuia = computed(() => {
    const max = this.maximo();
    return [0, 0.5, 1].map((fraccion) => ({
      y: this.y(max * fraccion),
      texto: Math.round(max * fraccion).toString(),
    }));
  });

  protected readonly etiquetaAccesible = computed(
    () => `${this.etiquetaSerie()}: ${this.barras().length} categorías, máximo ${Math.round(this.maximo())}`,
  );
}
