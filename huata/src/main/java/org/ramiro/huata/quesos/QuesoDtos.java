package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record QuesoRequest(
        @NotNull LocalDate fecha,
        @NotBlank String fuente,
        @NotNull Double producidosKg,
        Double vendidosKg,
        Double totalVenta,
        String observaciones) {}

record QuesoResponse(
        Long id, LocalDate fecha, String fuente, Double producidosKg, Double vendidosKg, Double totalVenta, String observaciones) {
    static QuesoResponse from(Queso q) {
        return new QuesoResponse(q.getId(), q.getFecha(), q.getFuente(), q.getProducidosKg(), q.getVendidosKg(), q.getTotalVenta(), q.getObservaciones());
    }
}
