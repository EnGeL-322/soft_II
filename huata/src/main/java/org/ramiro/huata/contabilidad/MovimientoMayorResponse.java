package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Una fila del libro mayor de una cuenta: el movimiento y el saldo acumulado despues de aplicarlo. */
public record MovimientoMayorResponse(
        LocalDate fecha, Long asientoId, String glosa, BigDecimal debe, BigDecimal haber, BigDecimal saldo) {}
