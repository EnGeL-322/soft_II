package org.ramiro.huata.productores;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

// ---- Ubicacion del proveedor (2.6) ----

record ProductorUbicacionRequest(
        @NotNull Double latitud, @NotNull Double longitud, Double precisionMetros, @NotNull TipoUbicacion tipo) {}

record ProductorUbicacionResponse(
        Long id,
        Long productorId,
        String productorNombre,
        Double latitud,
        Double longitud,
        Double precisionMetros,
        TipoUbicacion tipo,
        LocalDate fecha) {

    static ProductorUbicacionResponse from(ProductorUbicacion u) {
        return new ProductorUbicacionResponse(
                u.getId(),
                u.getProductor().getId(),
                u.getProductor().getNombres() + " " + u.getProductor().getApellidos(),
                u.getLatitud(),
                u.getLongitud(),
                u.getPrecisionMetros(),
                u.getTipo(),
                u.getFecha());
    }
}

// ---- Solicitud de cambio de zona (2.5) ----

record SolicitudCambioZonaRequest(@NotBlank String zonaSolicitada, String motivo) {}

record SolicitudCambioZonaDecisionRequest(@NotNull boolean aprobar, String observacion) {}

record SolicitudCambioZonaResponse(
        Long id,
        Long productorId,
        String productorNombre,
        String zonaActual,
        String zonaSolicitada,
        String motivo,
        EstadoSolicitudZona estado,
        LocalDate fechaSolicitud,
        LocalDate fechaDecision,
        String aprobador,
        String observacionDecision) {

    static SolicitudCambioZonaResponse from(SolicitudCambioZona s) {
        return new SolicitudCambioZonaResponse(
                s.getId(),
                s.getProductor().getId(),
                s.getProductor().getNombres() + " " + s.getProductor().getApellidos(),
                s.getZonaActual(),
                s.getZonaSolicitada(),
                s.getMotivo(),
                s.getEstado(),
                s.getFechaSolicitud(),
                s.getFechaDecision(),
                s.getAprobador(),
                s.getObservacionDecision());
    }
}
