package org.ramiro.huata.quesos;

/**
 * Condicion de pago de la venta (selector "Condición" del prototipo).
 *
 * <p>La condicion decide el estado de cobro inicial, y lo decide AQUI: si lo eligiera el formulario,
 * nada impediria registrar una venta al contado que nace "por cobrar", y el KPI de cobranzas pasaria
 * a depender de lo que marque cada persona.
 */
public enum CondicionVenta {
    CONTADO,
    CREDITO_7D;

    /** Al contado el dinero entra con la venta; a credito queda pendiente hasta que se cobre. */
    public EstadoCobro estadoInicial() {
        return this == CONTADO ? EstadoCobro.COBRADO : EstadoCobro.POR_COBRAR;
    }
}
