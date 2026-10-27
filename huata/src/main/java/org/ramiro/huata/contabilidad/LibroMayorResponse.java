package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;
import java.util.List;

public record LibroMayorResponse(
        String codigoCuenta, String nombreCuenta, List<MovimientoMayorResponse> movimientos, BigDecimal saldoFinal) {}
