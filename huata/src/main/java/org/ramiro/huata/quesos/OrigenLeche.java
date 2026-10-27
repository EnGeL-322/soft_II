package org.ramiro.huata.quesos;

/**
 * De donde sale la leche del lote. Es informativo: la leche que se descuenta la decide la receta (la
 * materia prima "Leche fresca", que entra al inventario desde el acopio, V31), no este campo.
 */
public enum OrigenLeche {
    ACOPIO_DEL_DIA,
    STOCK_CAMARA
}
