package org.ramiro.huata.recojo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.ramiro.huata.calidad.ResultadoCalidad;

record RecojoRequest(
        String clientUuid,
        @NotNull Long productorId,
        Long acopiadorId,
        Long rutaId,
        @NotNull Instant fechaHora,
        @NotNull @Positive Double litros,
        Double temperatura,
        FuenteRecojo fuente,
        Integer numeroEnvases,
        Double capacidadReferenciaL,
        String responsableRecepcion,
        Double litrosAppMovil) {}

record RecojoCancelRequest(String motivo) {}

record RecojoResponse(
        Long id,
        String clientUuid,
        Long productorId,
        String productorNombre,
        Long acopiadorId,
        String acopiadorNombre,
        Long rutaId,
        String rutaNombre,
        Instant fechaHora,
        Double litros,
        Double temperatura,
        EstadoRecojo estado,
        String motivoCancelacion,
        FuenteRecojo fuente,
        Integer numeroEnvases,
        Double capacidadReferenciaL,
        String responsableRecepcion,
        Double litrosAppMovil,
        ResultadoCalidad resultadoCalidad) {

    static RecojoResponse from(Recojo r, ResultadoCalidad resultadoCalidad) {
        return new RecojoResponse(
                r.getId(),
                r.getClientUuid(),
                r.getProductor().getId(),
                r.getProductor().getNombres() + " " + r.getProductor().getApellidos(),
                r.getAcopiador() != null ? r.getAcopiador().getId() : null,
                r.getAcopiador() != null ? r.getAcopiador().getNombres() + " " + r.getAcopiador().getApellidos() : null,
                r.getRuta() != null ? r.getRuta().getId() : null,
                r.getRuta() != null ? r.getRuta().getNombre() : null,
                r.getFechaHora(),
                r.getLitros(),
                r.getTemperatura(),
                r.getEstado(),
                r.getMotivoCancelacion(),
                r.getFuente(),
                r.getNumeroEnvases(),
                r.getCapacidadReferenciaL(),
                r.getResponsableRecepcion(),
                r.getLitrosAppMovil(),
                resultadoCalidad);
    }
}
