import { Liquidacion } from '../../core/models/models';

export interface DiaBoleta {
  abbr: string;
  litros: number;
  fecha: string;
}

/** Fechas del periodo y del detalle guardados en la liquidación, independientes del selector. */
export function diasDeBoleta(liquidacion: Liquidacion): DiaBoleta[] {
  const inicio = new Date(`${liquidacion.periodoInicio}T00:00:00Z`);
  const dias = ['DO', 'LU', 'MA', 'MI', 'JU', 'VI', 'SA'];
  return Array.from({ length: 7 }, (_, offset) => {
    const fecha = new Date(inicio);
    fecha.setUTCDate(fecha.getUTCDate() + offset);
    const iso = fecha.toISOString().slice(0, 10);
    return {
      abbr: dias[fecha.getUTCDay()],
      fecha: `${String(fecha.getUTCDate()).padStart(2, '0')}/${String(fecha.getUTCMonth() + 1).padStart(2, '0')}`,
      litros: liquidacion.detalle.filter((d) => d.fechaHora.slice(0, 10) === iso).reduce((s, d) => s + d.litros, 0),
    };
  });
}

export function descuentosDeBoleta(liquidacion: Liquidacion): number {
  return liquidacion.ajustes.reduce((s, a) => s + Math.max(0, -a.montoConSigno), 0);
}

/** Conserva visibles los registros incluidos por la API que no caben en las siete casillas. */
export function litrosAdicionalesDeBoleta(liquidacion: Liquidacion): number {
  const inicio = new Date(`${liquidacion.periodoInicio}T00:00:00Z`);
  const fin = new Date(inicio);
  fin.setUTCDate(fin.getUTCDate() + 6);
  const ultimaFecha = fin.toISOString().slice(0, 10);
  return liquidacion.detalle
    .filter((d) => d.fechaHora.slice(0, 10) < liquidacion.periodoInicio || d.fechaHora.slice(0, 10) > ultimaFecha)
    .reduce((s, d) => s + d.litros, 0);
}

export function bonificacionesDeBoleta(liquidacion: Liquidacion): number {
  return liquidacion.ajustes.reduce((s, a) => s + Math.max(0, a.montoConSigno), 0);
}

/** Las entregas sin importe no rebajan artificialmente la tarifa de las entregas valorizadas. */
export function tarifaDeBoleta(liquidacion: Liquidacion): string {
  const tarifas = [...new Set(liquidacion.detalle.filter((d) => d.precioAplicado > 0).map((d) => d.precioAplicado))]
    .sort((a, b) => a - b);
  if (!tarifas.length) return 'Sin entregas valorizadas';
  const precio = (n: number) => `S/ ${n.toLocaleString('es-PE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  return tarifas.length === 1 ? precio(tarifas[0]) : `${precio(tarifas[0])} – ${precio(tarifas[tarifas.length - 1])}`;
}

export function fechaDeBoleta(iso: string): string {
  return iso.split('-').reverse().join('/');
}
