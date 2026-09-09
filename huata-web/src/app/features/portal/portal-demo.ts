export type ResultadoDemo = 'Apto' | 'Observado' | 'Retirado';
export interface ControlDemo {
  id: string; fecha: string; proveedor: string; codigo: string; zona: string;
  leche: number; agua: number; pureza: number; resultado: ResultadoDemo;
}
// Registros sintéticos del prototipo y de las referencias del portal público.
// No representan controles de proveedores reales.
const filas: [string, number, string, number, number, number, ResultadoDemo][] = [
  ['2026-08-26', 1, 'Quillallaka', 163, 0, 100, 'Apto'],
  ['2026-08-26', 2, 'Santa Bárbara', 41, 0, 100, 'Apto'],
  ['2026-08-26', 3, 'I Collana', 57.5, 2.9, 95, 'Observado'],
  ['2026-08-26', 4, 'II Collana', 73, 0, 100, 'Apto'],
  ['2026-08-26', 5, 'Faon y Yasin', 37.5, 0, 100, 'Apto'],
  ['2026-08-25', 1, 'Quillallaka', 137, 0, 100, 'Apto'],
  ['2026-08-25', 3, 'I Collana', 48, 3.1, 93.5, 'Observado'],
  ['2026-08-24', 1, 'Quillallaka', 154.5, 0, 100, 'Apto'],
  ['2026-08-24', 5, 'Faon y Yasin', 38, 7.5, 80.3, 'Retirado'],
  ['2026-08-22', 3, 'I Collana', 39.5, 3.1, 92.2, 'Observado'],
  ['2026-08-20', 1, 'Quillallaka', 73.5, 0, 100, 'Apto'],
];
export const CONTROLES_DEMO: ControlDemo[] = filas.map(([fecha, numero, zona, leche, agua, pureza, resultado], i) => ({
  id: `PUB-${i + 1}`, fecha, proveedor: `PROVEEDOR DEMO ${String.fromCharCode(64 + numero)}`,
  codigo: `DEMO-${String(numero).padStart(3, '0')}`, zona, leche, agua, pureza, resultado,
}));
