package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Que trabajador participo en el lote y cuanto costo su mano de obra. */
public record ManoObraItemRequest(@NotNull Long trabajadorId, @NotNull @PositiveOrZero BigDecimal monto) {}
