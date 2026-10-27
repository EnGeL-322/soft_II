package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Una partida del libro diario, con sus lineas y los totales que deben coincidir (partida doble). */
public record AsientoContableResponse(
        Long id,
        LocalDate fecha,
        String glosa,
        String comentario,
        OrigenAsiento origenTipo,
        Long origenId,
        List<AsientoLineaResponse> lineas,
        BigDecimal totalDebe,
        BigDecimal totalHaber) {
    static AsientoContableResponse from(AsientoContable a) {
        List<AsientoLineaResponse> lineas = a.getLineas().stream().map(AsientoLineaResponse::from).toList();
        BigDecimal totalDebe = lineas.stream().map(AsientoLineaResponse::debe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHaber = lineas.stream().map(AsientoLineaResponse::haber).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AsientoContableResponse(
                a.getId(), a.getFecha(), a.getGlosa(), a.getComentario(), a.getOrigenTipo(), a.getOrigenId(),
                lineas, totalDebe, totalHaber);
    }
}
