package org.ramiro.huata.calidad;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record ParametroCalidadRequest(String nombre, Double valorMin, Double valorMax, String unidad, String descripcion) {}

record ParametroCalidadResponse(Long id, String nombre, Double valorMin, Double valorMax, String unidad, String descripcion) {
    static ParametroCalidadResponse from(ParametroCalidad p) {
        return new ParametroCalidadResponse(p.getId(), p.getNombre(), p.getValorMin(), p.getValorMax(), p.getUnidad(), p.getDescripcion());
    }
}

/**
 * Un control cuelga de un recojo (recojoId) o, si el testeador se adelanto al acopiador, de un
 * proveedor (productorId) a la espera de que el acopiador registre el recojo. Uno de los dos es
 * obligatorio; lo valida CalidadService.
 */
record ControlCalidadRequest(
        Long recojoId,
        Long productorId,
        LugarAnalisis lugarAnalisis,
        Instant fechaHora,
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
        String responsable,
        AguaAnadida aguaAnadida,
        ResultadoLugol lugol,
        Olor olor,
        Aspecto aspecto,
        String motivoRechazo) {}

record ControlCalidadResponse(
        Long id,
        Long recojoId,
        Long productorId,
        String productorNombre,
        String productorCodigo,
        LugarAnalisis lugarAnalisis,
        Instant fechaHora,
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
        AguaAnadida aguaAnadida,
        ResultadoLugol lugol,
        Olor olor,
        Aspecto aspecto,
        String motivoRechazo,
        Instant fecha,
        String aprobadoPor,
        Instant aprobadoEn,
        String advertencia) {

    static ControlCalidadResponse from(ControlCalidad c) {
        return new ControlCalidadResponse(
                c.getId(),
                c.getRecojo() != null ? c.getRecojo().getId() : null,
                c.proveedor().getId(),
                c.proveedor().getNombres() + " " + c.proveedor().getApellidos(),
                c.proveedor().getCodigo(),
                c.getLugarAnalisis(),
                c.getFechaHora(),
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
                c.getAguaAnadida(),
                c.getLugol(),
                c.getOlor(),
                c.getAspecto(),
                c.getMotivoRechazo(),
                c.getCreatedAt(),
                c.getAprobadoPor(),
                c.getAprobadoEn(),
                c.getAdvertencia());
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

/**
 * {@code tieneArchivo} indica que la foto esta en el almacen propio y se descarga por
 * {@code urlArchivo} con el mismo token. No se devuelve la clave interna del almacen.
 */
record EvidenciaCalidadResponse(
        Long id,
        Long controlCalidadId,
        Long incidenciaId,
        TipoEvidencia tipo,
        String urlArchivo,
        EstadoEvidencia estado,
        boolean tieneArchivo,
        String tipoContenido,
        Long tamanoBytes,
        String textoOcr,
        String valoresOcr,
        Double confianzaOcr,
        String verificadoPor,
        Instant fecha) {
    static EvidenciaCalidadResponse from(EvidenciaCalidad e) {
        return new EvidenciaCalidadResponse(
                e.getId(),
                e.getControlCalidad() != null ? e.getControlCalidad().getId() : null,
                e.getIncidencia() != null ? e.getIncidencia().getId() : null,
                e.getTipo(),
                e.getUrlArchivo(),
                e.getEstado(),
                e.getClaveArchivo() != null,
                e.getTipoContenido(),
                e.getTamanoBytes(),
                e.getTextoOcr(),
                e.getValoresOcr(),
                e.getConfianzaOcr(),
                e.getVerificadoPor(),
                e.getCreatedAt());
    }
}
