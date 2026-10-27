import { Recojo } from '../../core/models/models';

/**
 * Entregas directas vistas por fechas: el periodo elegido (día, semana de liquidación, mes o rango
 * libre), su desplazamiento con las flechas y la agrupación por día con sus totales. Funciones
 * puras para poder probarlas sin la pantalla.
 */
export type PeriodoEntregas = 'DIA' | 'SEMANA' | 'MES' | 'RANGO';

/** Fechas locales 'yyyy-mm-dd', ambas incluidas. */
export interface RangoFechas {
  desde: string;
  hasta: string;
}

export interface DiaEntregas {
  fecha: string;
  /** Todas las del día, de la más reciente a la más antigua (incluye canceladas). */
  entregas: Recojo[];
  /** Totales sin las canceladas: no entraron a planta. */
  litros: number;
  cantidad: number;
  proveedores: number;
  canceladas: number;
}

export interface ResumenEntregas {
  litros: number;
  entregas: number;
  proveedores: number;
  promedio: number;
  pendientesAnalisis: number;
  canceladas: number;
  diasConEntrega: number;
}

export function fechaIsoLocal(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${dia}`;
}

/** Mediodía local: sumar o restar días nunca cruza de fecha por un cambio de horario. */
export function deIso(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d, 12);
}

function sumarDias(iso: string, dias: number): string {
  const d = deIso(iso);
  d.setDate(d.getDate() + dias);
  return fechaIsoLocal(d);
}

function diasEntre(desde: string, hasta: string): number {
  return Math.round((deIso(hasta).getTime() - deIso(desde).getTime()) / 86_400_000);
}

/**
 * Rango que cubre el periodo. La semana es la de liquidación (jueves → miércoles, CLAUDE.md §8), para
 * que el total coincida con lo que se paga ese viernes.
 */
export function rangoDePeriodo(periodo: PeriodoEntregas, referencia: string, rango?: RangoFechas): RangoFechas {
  switch (periodo) {
    case 'DIA':
      return { desde: referencia, hasta: referencia };
    case 'SEMANA': {
      const desdeJueves = (deIso(referencia).getDay() - 4 + 7) % 7;
      const desde = sumarDias(referencia, -desdeJueves);
      return { desde, hasta: sumarDias(desde, 6) };
    }
    case 'MES': {
      const d = deIso(referencia);
      return {
        desde: fechaIsoLocal(new Date(d.getFullYear(), d.getMonth(), 1, 12)),
        hasta: fechaIsoLocal(new Date(d.getFullYear(), d.getMonth() + 1, 0, 12)),
      };
    }
    case 'RANGO': {
      const desde = rango?.desde || referencia;
      const hasta = rango?.hasta || referencia;
      return desde <= hasta ? { desde, hasta } : { desde: hasta, hasta: desde };
    }
  }
}

/** Periodo anterior (-1) o siguiente (+1): un día, una semana, un mes o un rango del mismo largo. */
export function moverPeriodo(
  periodo: PeriodoEntregas,
  referencia: string,
  rango: RangoFechas,
  paso: 1 | -1,
): { referencia: string; rango: RangoFechas } {
  switch (periodo) {
    case 'DIA':
      return { referencia: sumarDias(referencia, paso), rango };
    case 'SEMANA':
      return { referencia: sumarDias(referencia, 7 * paso), rango };
    case 'MES': {
      const d = deIso(referencia);
      return { referencia: fechaIsoLocal(new Date(d.getFullYear(), d.getMonth() + paso, 1, 12)), rango };
    }
    case 'RANGO': {
      const actual = rangoDePeriodo('RANGO', referencia, rango);
      const largo = diasEntre(actual.desde, actual.hasta) + 1;
      const nuevo = { desde: sumarDias(actual.desde, largo * paso), hasta: sumarDias(actual.hasta, largo * paso) };
      return { referencia: nuevo.desde, rango: nuevo };
    }
  }
}

/** Límites del rango como instantes para el backend (hasta incluido: último milisegundo del día). */
export function instantesDelRango(rango: RangoFechas): { desde: string; hasta: string } {
  const desde = deIso(rango.desde);
  desde.setHours(0, 0, 0, 0);
  const hasta = deIso(rango.hasta);
  hasta.setHours(23, 59, 59, 999);
  return { desde: desde.toISOString(), hasta: hasta.toISOString() };
}

/** Todas las fechas del rango, para dibujar también los días sin entregas. */
export function diasDelRango(rango: RangoFechas): string[] {
  const n = diasEntre(rango.desde, rango.hasta);
  return Array.from({ length: n + 1 }, (_, i) => sumarDias(rango.desde, i));
}

function proveedoresDistintos(recojos: Recojo[]): number {
  return new Set(recojos.map((r) => r.productorId)).size;
}

/** Agrupa por día local, del más reciente al más antiguo. */
export function agruparPorDia(recojos: Recojo[]): DiaEntregas[] {
  const porDia = new Map<string, Recojo[]>();
  for (const r of recojos) {
    const fecha = fechaIsoLocal(new Date(r.fechaHora));
    const lista = porDia.get(fecha);
    if (lista) lista.push(r);
    else porDia.set(fecha, [r]);
  }
  return [...porDia.entries()]
    .sort(([a], [b]) => b.localeCompare(a))
    .map(([fecha, lista]) => {
      const entregas = [...lista].sort((a, b) => b.fechaHora.localeCompare(a.fechaHora));
      const vigentes = entregas.filter((r) => r.estado !== 'CANCELLED');
      return {
        fecha,
        entregas,
        litros: vigentes.reduce((s, r) => s + r.litros, 0),
        cantidad: vigentes.length,
        proveedores: proveedoresDistintos(vigentes),
        canceladas: entregas.length - vigentes.length,
      };
    });
}

export function resumirEntregas(recojos: Recojo[]): ResumenEntregas {
  const vigentes = recojos.filter((r) => r.estado !== 'CANCELLED');
  const litros = vigentes.reduce((s, r) => s + r.litros, 0);
  return {
    litros,
    entregas: vigentes.length,
    proveedores: proveedoresDistintos(vigentes),
    promedio: vigentes.length ? litros / vigentes.length : 0,
    pendientesAnalisis: vigentes.filter((r) => !r.resultadoCalidad).length,
    canceladas: recojos.length - vigentes.length,
    diasConEntrega: new Set(vigentes.map((r) => fechaIsoLocal(new Date(r.fechaHora)))).size,
  };
}
