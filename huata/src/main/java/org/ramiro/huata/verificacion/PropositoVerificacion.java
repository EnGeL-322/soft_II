package org.ramiro.huata.verificacion;

/**
 * Para que se pidio el codigo. Un codigo emitido para cambiar precios no sirve para otra cosa: si
 * valiera para todo, pedir uno con la excusa mas inocua abriria la operacion mas delicada.
 *
 * <p>Cuando entren mas acciones sensibles (dar de baja un usuario, restaurar un respaldo) se anaden
 * aqui y en el CHECK de {@code codigos_verificacion}. Cada proposito nuevo tambien necesita su propio
 * permiso en {@link VerificacionController#solicitar}: el codigo no debe poder pedirse con un permiso
 * que no corresponde a la accion que protege.
 */
public enum PropositoVerificacion {
    /** Cambiar los precios de compra de leche o de venta de queso. */
    PRECIOS,
    /** Reabrir una liquidacion ya CERRADA (pagada) para poder recalcularla. */
    REAPERTURA_PAGO
}
