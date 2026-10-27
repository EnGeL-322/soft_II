package org.ramiro.huata.compras;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CompraItemRequest(
        @NotNull Long materiaPrimaId, @NotNull @Positive BigDecimal cantidad, @NotNull @PositiveOrZero BigDecimal costoUnitario) {}
