package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record QuesoRequest(
        @NotNull LocalDate fecha,
        @NotBlank String fuente,
        @NotNull @PositiveOrZero Double producidosKg,
        @PositiveOrZero Double vendidosKg,
        @PositiveOrZero Double totalVenta,
        @Size(max = 500) String observaciones,
        @Size(max = 150) String comprador,
        @Size(max = 80) String numeroBoleta) {}

record QuesoResponse(
        Long id, LocalDate fecha, String fuente, Double producidosKg, Double vendidosKg, Double totalVenta, String observaciones,
        String comprador, String numeroBoleta) {
    static QuesoResponse from(Queso q) {
        return new QuesoResponse(q.getId(), q.getFecha(), q.getFuente(), q.getProducidosKg(), q.getVendidosKg(), q.getTotalVenta(), q.getObservaciones(), q.getComprador(), q.getNumeroBoleta());
    }
}
