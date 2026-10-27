/** Unidad de medida como la lee una persona ("kg", "litros"), para mostrarla junto a una cantidad. */
export function etiquetaUnidad(unidad: string | null | undefined): string {
  if (!unidad) return '—';
  const legibles: Record<string, string> = {
    KG: 'kg',
    KILOGRAMO: 'kg',
    LITRO: 'litros',
    UNIDAD: 'unidades',
    GRAMO: 'g',
    ML: 'ml',
  };
  return legibles[unidad] ?? unidad.toLowerCase();
}
