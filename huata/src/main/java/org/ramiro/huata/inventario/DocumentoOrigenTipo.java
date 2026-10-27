package org.ramiro.huata.inventario;

/** Que clase de documento origino un movimiento del kardex ({@code documento_origen_id} apunta a el). */
public enum DocumentoOrigenTipo {
    COMPRA,
    PRODUCCION,
    VENTA,
    AJUSTE,
    /** Total recibido en planta de una ruta ({@code conciliaciones_ruta.id}). */
    CONCILIACION,
    /** Leche que el proveedor llevo el mismo a planta ({@code recojos.id}). */
    RECOJO
}
