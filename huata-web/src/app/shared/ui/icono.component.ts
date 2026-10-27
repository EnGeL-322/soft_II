import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Trazos SVG (24x24, linea de 1.75) de todos los iconos del sistema. Un solo juego de iconos, del
 * mismo grosor y tamaño, en lugar de caracteres Unicode sueltos que cada fuente dibuja distinto.
 * Sin dependencias: agregar un icono es agregar una entrada aqui.
 */
const TRAZOS = {
  inicio: 'M3 10.5 12 3l9 7.5M5 9v11h5v-6h4v6h5V9',
  calidad: 'M9 3h6M10 3v6.5L4.6 18.2A1.8 1.8 0 0 0 6.1 21h11.8a1.8 1.8 0 0 0 1.5-2.8L14 9.5V3M7.5 14h9',
  acopio: 'M8 2h8M9 2v3l-2 3v12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2V8l-2-3V2M7 13h10',
  planta: 'M2 20h20M4 20V9l5 3V9l5 3V5h6v15M8 16h1M12 16h1M16 16h1',
  entrega: 'M12 3v10M8 9l4 4 4-4M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4',
  rutas: 'M9 4 3 6.5v13.5l6-2.5 6 2.5 6-2.5V4l-6 2.5L9 4ZM9 4v13.5M15 6.5V20',
  zonas: 'M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21ZM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z',
  proveedores: 'M16 20v-1.5a3.5 3.5 0 0 0-3.5-3.5h-5A3.5 3.5 0 0 0 4 18.5V20M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM20 20v-1.5a3.5 3.5 0 0 0-2.5-3.35M15.5 4.15a3.5 3.5 0 0 1 0 6.7',
  cambio: 'M17 3l4 4-4 4M21 7H8M7 21l-4-4 4-4M3 17h13',
  acopiadores: 'M10 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM3 20v-1.5A3.5 3.5 0 0 1 6.5 15h5M15 18l2 2 4-4',
  recetas: 'M4 4.5A1.5 1.5 0 0 1 5.5 3H20v15H5.5A1.5 1.5 0 0 0 4 19.5v-15ZM4 19.5A1.5 1.5 0 0 0 5.5 21H20M8 7h8M8 11h6',
  produccion: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18ZM12 7v5l3 2',
  compras: 'M3 4h2l2.4 11.2a1.5 1.5 0 0 0 1.5 1.3h8.6a1.5 1.5 0 0 0 1.5-1.2L21 8H6.2M9 21h.01M18 21h.01',
  inventario: 'M21 8 12 3 3 8v8l9 5 9-5V8ZM3 8l9 5 9-5M12 13v8',
  ventas: 'M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8ZM7.5 7.5h.01',
  pagos: 'M3 7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7ZM3 10h18M7 15h3',
  contabilidad: 'M5 3h14a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM8 7h8M8 11h.01M12 11h.01M16 11h.01M8 15h.01M12 15h.01M16 15h.01',
  usuarios: 'M12 3 4 6v5c0 5 3.4 8.6 8 10 4.6-1.4 8-5 8-10V6l-8-3ZM12 11a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM8 16a4 4 0 0 1 8 0',
  ajustes: 'M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0M16 4v4M10 10v4M18 16v4',
  menu: 'M4 6h16M4 12h16M4 18h16',
  contraer: 'M15 6l-6 6 6 6M20 4v16',
  expandir: 'M9 6l6 6-6 6M4 4v16',
  buscar: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14ZM20 20l-4-4',
  salir: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  externo: 'M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5',
  flecha: 'M5 12h14M13 6l6 6-6 6',
  anterior: 'M15 6l-6 6 6 6',
  siguiente: 'M9 6l6 6-6 6',
  abajo: 'M6 9l6 6 6-6',
  calendario: 'M7 3v4M17 3v4M4 9h16M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z',
  alerta: 'M12 9v4M12 17h.01M10.3 3.9 2.4 18a2 2 0 0 0 1.7 3h15.8a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z',
  editar: 'M4 20h4L18.5 9.5a2.1 2.1 0 0 0-4-4L4 16v4ZM13.5 6.5l4 4',
  estrella: 'M12 3l2.8 5.7 6.2.9-4.5 4.4 1.1 6.2L12 17.3l-5.6 2.9 1.1-6.2L3 9.6l6.2-.9L12 3Z',
  pausa: 'M9 5v14M15 5v14',
  bloquear: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM5.6 5.6l12.8 12.8',
  activar: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM8 12.5l2.7 2.7L16 9.5',
  eliminar: 'M4 7h16M10 11v6M14 11v6M5 7l1 12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2l1-12M9 7V4h6v3',
  llave: 'M15.5 8.5a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0ZM12 12v9M12 17h3M12 20h2',
  ruta: 'M6 19a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM18 9a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM6 15V9a4 4 0 0 1 4-4h6M18 9v6a4 4 0 0 1-4 4H8',
  reportes: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  agregar: 'M12 5v14M5 12h14',
  historial: 'M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2',
  lista: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  persona: 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1',
  precio: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM15 9.5c-.5-1-1.6-1.5-3-1.5-1.7 0-3 .8-3 2s1.3 1.7 3 2 3 .8 3 2-1.3 2-3 2c-1.4 0-2.5-.5-3-1.5M12 6.5v11',
} as const;

export type NombreIcono = keyof typeof TRAZOS;

@Component({
  selector: 'app-icono',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'icono', 'aria-hidden': 'true' },
  template: `<svg
    [attr.width]="tamano()"
    [attr.height]="tamano()"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
  >
    <path [attr.d]="trazo()" />
  </svg>`,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      line-height: 0;
    }
  `,
})
export class IconoComponent {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(18);
  protected readonly trazo = computed(() => TRAZOS[this.nombre()]);
}
