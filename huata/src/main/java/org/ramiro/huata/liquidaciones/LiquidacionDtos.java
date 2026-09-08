package org.ramiro.huata.liquidaciones;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

record TarifaLecheRequest(
        @NotNull Double precioNormal, @NotNull Double precioObservada, @NotNull LocalDate vigenteDesde, LocalDate vigenteHasta) {}

record TarifaLecheResponse(Long id, Double precioNormal, Double precioObservada, LocalDate vigenteDesde, LocalDate vigenteHasta) {
    static TarifaLecheResponse from(TarifaLeche t) {
        return new TarifaLecheResponse(t.getId(), t.getPrecioNormal(), t.getPrecioObservada(), t.getVigenteDesde(), t.getVigenteHasta());
    }
}

record LiquidacionCalcularRequest(@NotNull Long productorId, @NotNull LocalDate periodoInicio, @NotNull LocalDate periodoFin) {}

record CambiarEstadoLiquidacionRequest(@NotNull EstadoLiquidacion estado) {}

record LiquidacionDetalleResponse(Long recojoId, java.time.Instant fechaHora, Double litros, Double precioAplicado, Double subtotal) {
    static LiquidacionDetalleResponse from(LiquidacionDetalle d) {
        return new LiquidacionDetalleResponse(
                d.getRecojo().getId(), d.getRecojo().getFechaHora(), d.getLitros(), d.getPrecioAplicado(), d.getSubtotal());
    }
}

record LiquidacionResponse(
        Long id,
        Long productorId,
        String productorNombre,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        Double litrosTotales,
        Double montoTotal,
        EstadoLiquidacion estado,
        List<LiquidacionDetalleResponse> detalle) {

    static LiquidacionResponse from(Liquidacion l, List<LiquidacionDetalle> detalles) {
        return new LiquidacionResponse(
                l.getId(),
                l.getProductor().getId(),
                l.getProductor().getNombres() + " " + l.getProductor().getApellidos(),
                l.getPeriodoInicio(),
                l.getPeriodoFin(),
                l.getLitrosTotales(),
                l.getMontoTotal(),
                l.getEstado(),
                detalles == null ? List.of() : detalles.stream().map(LiquidacionDetalleResponse::from).toList());
    }
}
