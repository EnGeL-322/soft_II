/**
 * Ciclos de acopio: de jueves a miércoles (regla 7.6).
 *
 * <p>Vive aquí y no dentro de un componente porque lo necesitan las tres pantallas de pagos. Tenerlo
 * repetido haría que un cambio en el día de corte —que es una decisión de la cooperativa, no del
 * código— hubiera que acordarse de replicarlo en cada una.
 */
export interface Ciclo {
  /** Fecha ISO (YYYY-MM-DD) del jueves en que abre el ciclo. */
  inicio: string;
  /** Fecha ISO del miércoles en que cierra. */
  fin: string;
  /** Texto para el selector: "03 set — 09 set 2026". */
  etiqueta: string;
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic'];

/** ISO local, no UTC: con toISOString() un ciclo se corre un día según la zona horaria. */
function iso(d: Date): string {
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function legible(inicio: Date, fin: Date): string {
  const dia = (d: Date) => String(d.getDate()).padStart(2, '0') + ' ' + MESES[d.getMonth()];
  return `${dia(inicio)} — ${dia(fin)} ${fin.getFullYear()}`;
}

/** Los últimos `cuantos` ciclos, del más reciente al más antiguo. El índice 0 es el ciclo en curso. */
export function ciclosRecientes(cuantos = 52): Ciclo[] {
  return Array.from({ length: cuantos }, (_, i) => {
    const inicio = new Date();
    // (getDay() + 3) % 7 retrocede hasta el jueves: domingo=0 -> 3 días atrás, jueves=4 -> 0.
    inicio.setDate(inicio.getDate() - ((inicio.getDay() + 3) % 7) - i * 7);
    const fin = new Date(inicio);
    fin.setDate(fin.getDate() + 6);
    return { inicio: iso(inicio), fin: iso(fin), etiqueta: legible(inicio, fin) };
  });
}
