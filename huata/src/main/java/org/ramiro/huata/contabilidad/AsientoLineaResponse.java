package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;

public record AsientoLineaResponse(String codigoCuenta, String nombreCuenta, BigDecimal debe, BigDecimal haber) {
    static AsientoLineaResponse from(AsientoLinea l) {
        return new AsientoLineaResponse(l.getCuenta().getCodigo(), l.getCuenta().getNombre(), l.getDebe(), l.getHaber());
    }
}
