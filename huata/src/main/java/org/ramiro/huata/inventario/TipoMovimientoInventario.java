package org.ramiro.huata.inventario;

/**
 * Tipo de un movimiento del kardex. {@code cantidad} en {@link MovimientoInventario} siempre se
 * guarda positiva; la direccion (entrada o salida) la decide este enum, no el signo del numero.
 */
public enum TipoMovimientoInventario {
    ENTRADA_COMPRA(true),
    SALIDA_PRODUCCION_CONSUMO(false),
    ENTRADA_PRODUCCION_TERMINADO(true),
    SALIDA_VENTA(false),
    ENTRADA_ANULACION_VENTA(true),
    AJUSTE_ENTRADA(true),
    AJUSTE_SALIDA(false),
    /** Leche recibida en planta: el total de una ruta o una entrega directa del proveedor (V31). */
    ENTRADA_ACOPIO(true);

    private final boolean entrada;

    TipoMovimientoInventario(boolean entrada) {
        this.entrada = entrada;
    }

    public boolean esEntrada() {
        return entrada;
    }
}
