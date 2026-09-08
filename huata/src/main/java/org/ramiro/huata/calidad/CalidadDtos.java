package org.ramiro.huata.calidad;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record ParametroCalidadRequest(String nombre, Double valorMin, Double valorMax, String unidad, String descripcion) {}

record ParametroCalidadResponse(Long id, String nombre, Double valorMin, Double valorMax, String unidad, String descripcion) {
    static ParametroCalidadResponse from(ParametroCalidad p) {
        return new ParametroCalidadResponse(p.getId(), p.getNombre(), p.getValorMin(), p.getValorMax(), p.getUnidad(), p.getDescripcion());
    }
}

record ControlCalidadRequest(
        @NotNull Long recojoId,
        Double densidad,
        Double puntoCongelacion,
        Double acidez,
        Double temperatura,
        Double grasa,
        Double solidosNoGrasos,
        Double solidosTotales,
        String revisionVisual,
        @NotNull ResultadoCalidad resultado,
        String equipoUtilizado,
        String responsable) {}

record ControlCalidadResponse(
        Long id,
        Long recojoId,
        Long productorId,
        String productorNombre,
        Double densidad,
        Double puntoCongelacion,
        Double acidez,
        Double temperatura,
        Double grasa,
        Double solidosNoGrasos,
        Double solidosTotales,
        String revisionVisual,
        ResultadoCalidad resultado,
        String equipoUtilizado,
        String responsable,
        Instant fecha) {

    static ControlCalidadResponse from(ControlCalidad c) {
        return new ControlCalidadResponse(
                c.getId(),
                c.getRecojo().getId(),
                c.getRecojo().getProductor().getId(),
                c.getRecojo().getProductor().getNombres() + " " + c.getRecojo().getProductor().getApellidos(),
                c.getDensidad(),
                c.getPuntoCongelacion(),
                c.getAcidez(),
                c.getTemperatura(),
                c.getGrasa(),
                c.getSolidosNoGrasos(),
                c.getSolidosTotales(),
                c.getRevisionVisual(),
                c.getResultado(),
                c.getEquipoUtilizado(),
                c.getResponsable(),
                c.getCreatedAt());
    }
}

record IncidenciaCalidadRequest(
        @NotNull Long productorId, Long recojoId, @NotNull String tipo, String descripcion, Instant fecha) {}

record IncidenciaCalidadResponse(
        Long id,
        Long productorId,
        String productorNombre,
        Long recojoId,
        String tipo,
        String descripcion,
        Instant fecha,
        EstadoIncidencia estado) {

    static IncidenciaCalidadResponse from(IncidenciaCalidad i) {
        return new IncidenciaCalidadResponse(
                i.getId(),
                i.getProductor().getId(),
                i.getProductor().getNombres() + " " + i.getProductor().getApellidos(),
                i.getRecojo() != null ? i.getRecojo().getId() : null,
                i.getTipo(),
                i.getDescripcion(),
                i.getFecha(),
                i.getEstado());
    }
}

record CambiarEstadoIncidenciaRequest(@NotNull EstadoIncidencia estado) {}

record EvidenciaCalidadRequest(Long controlCalidadId, Long incidenciaId, @NotNull TipoEvidencia tipo, @NotNull String urlArchivo) {}

record EvidenciaCalidadResponse(Long id, Long controlCalidadId, Long incidenciaId, TipoEvidencia tipo, String urlArchivo, EstadoEvidencia estado) {
    static EvidenciaCalidadResponse from(EvidenciaCalidad e) {
        return new EvidenciaCalidadResponse(
                e.getId(),
                e.getControlCalidad() != null ? e.getControlCalidad().getId() : null,
                e.getIncidencia() != null ? e.getIncidencia().getId() : null,
                e.getTipo(),
                e.getUrlArchivo(),
                e.getEstado());
    }
}
