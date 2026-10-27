package org.ramiro.huata.compras;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CompraRequest(
        @NotNull Long proveedorCompraId,
        @NotNull LocalDate fecha,
        @Size(max = 60) String documento,
        @Size(max = 500) String observaciones,
        @NotEmpty List<@Valid CompraItemRequest> items) {}
