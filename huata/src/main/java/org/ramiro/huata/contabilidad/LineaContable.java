package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;

/**
 * Una linea debe/haber a registrar, sin resolver todavia la cuenta (lo hace el servicio por codigo).
 *
 * <p>Publico a proposito: es el unico tipo de este paquete que otros modulos (compras, produccion,
 * ventas de queso) necesitan para pedirle un asiento a {@link AsientoContableService}.
 */
public record LineaContable(String codigoCuenta, BigDecimal debe, BigDecimal haber) {
    public static LineaContable debe(String codigoCuenta, BigDecimal monto) {
        return new LineaContable(codigoCuenta, monto, BigDecimal.ZERO);
    }

    public static LineaContable haber(String codigoCuenta, BigDecimal monto) {
        return new LineaContable(codigoCuenta, BigDecimal.ZERO, monto);
    }
}
