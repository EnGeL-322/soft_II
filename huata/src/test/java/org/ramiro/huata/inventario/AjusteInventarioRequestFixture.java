package org.ramiro.huata.inventario;

import java.math.BigDecimal;

/** {@link AjusteInventarioRequest} es package-private: los tests de otros paquetes lo arman aqui. */
public final class AjusteInventarioRequestFixture {

    private AjusteInventarioRequestFixture() {}

    public static AjusteInventarioRequest salida(Long materiaPrimaId, double cantidad, String motivo) {
        return new AjusteInventarioRequest(materiaPrimaId, AjusteInventario.Tipo.SALIDA, BigDecimal.valueOf(cantidad), null, motivo);
    }

    public static AjusteInventarioRequest entrada(Long materiaPrimaId, double cantidad, String motivo) {
        return new AjusteInventarioRequest(materiaPrimaId, AjusteInventario.Tipo.ENTRADA, BigDecimal.valueOf(cantidad), null, motivo);
    }
}
