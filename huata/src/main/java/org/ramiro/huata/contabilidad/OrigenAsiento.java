package org.ramiro.huata.contabilidad;

/** Que operacion de negocio genero automaticamente el asiento. */
public enum OrigenAsiento {
    COMPRA,
    PRODUCCION_CONSUMO,
    PRODUCCION_MANO_OBRA,
    PRODUCCION_TERMINADO,
    VENTA,
    VENTA_COSTO,
    ANULACION_VENTA,
    LIQUIDACION_PAGO,
    LIQUIDACION_REAPERTURA,
    /** Leche recibida en planta que entra al almacen (V33), o su correccion. */
    RECEPCION_LECHE,
    /** Ajuste manual de stock: merma, conteo fisico, leche descartada (V59). */
    AJUSTE_INVENTARIO,
    /** Reversion de un lote anulado: devuelve insumos y saca el producto terminado (V59). */
    ANULACION_PRODUCCION,
    /** Reversion de una compra anulada (V59). */
    ANULACION_COMPRA
}
