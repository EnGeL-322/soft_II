package org.ramiro.huata.liquidaciones;

/**
 * Tipos de ajuste que se aplican al bruto de una liquidacion (DESARROLLO.md regla 7.7).
 *
 * <p>El signo lo decide el TIPO, no el numero guardado: por eso {@link #aplicar(double)} toma el
 * valor absoluto del monto. Asi es imposible que una penalidad grabada como -50 termine sumando 50
 * por un doble negativo, que es el error facil de cometer si se confia en el signo del dato.
 *
 * <p>Los signos estan confirmados contra {@code adjustmentValue()} del prototipo y resuelven la duda
 * que quedaba abierta en DESARROLLO1.md 6.4.
 */
public enum TipoAjuste {
    /** Queso que el proveedor se lleva y se le descuenta del pago. */
    DESCUENTO_QUESO(-1),
    /** Sancion por incumplimiento. */
    PENALIDAD(-1),
    /** Dinero entregado antes del cierre del ciclo. */
    ADELANTO(-1),
    /** Unico ajuste que suma. */
    BONIFICACION(1);

    private final int signo;

    TipoAjuste(int signo) {
        this.signo = signo;
    }

    /** Devuelve el monto con el signo que corresponde al tipo. */
    public double aplicar(double monto) {
        return signo * Math.abs(monto);
    }

    /** true si el ajuste reduce lo que se paga al proveedor. */
    public boolean esDescuento() {
        return signo < 0;
    }
}
