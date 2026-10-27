package org.ramiro.huata.recojo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.ramiro.huata.calidad.ResultadoCalidad;

record RecojoRequest(
        String clientUuid,
        @NotNull Long productorId,
        Long acopiadorId,
        Long rutaId,
        @NotNull Instant fechaHora,
        // PositiveOrZero y no Positive: un NO_ENTREGADO (6.3, rojo) se registra con 0 litros. Que un
        // ACOPIADO tenga que ser > 0 lo exige RecojoService, no la anotacion, porque la regla depende
        // de estadoVisita.
        @NotNull @PositiveOrZero Double litros,
        Double temperatura,
        FuenteRecojo fuente,
        Integer numeroEnvases,
        Double capacidadReferenciaL,
        String responsableRecepcion,
        Double litrosAppMovil,
        EstadoVisita estadoVisita,
        String motivoNoEntrega,
        // GPS del acopiador al momento de registrar (V28): opcional a proposito -- un recojo de
        // fuente PLANTA, o un cliente viejo, puede no mandarlo. Sin los dos no hay con que comparar
        // contra la ubicacion del proveedor, asi que RecojoService no marca distancia ni bandera.
        Double latitud,
        Double longitud,
        // Ticket del analizador, si lo hubo. Va como bloque aparte y no como campos sueltos porque
        // es una entidad propia (medicion_instrumento, V7) y no un atributo del recojo.
        org.ramiro.huata.ingesta.LecturaInstrumento medicion) {}

record RecojoCancelRequest(String motivo) {}

/**
 * Corrige el recojo existente en vez de crear uno nuevo (una entrega diaria por proveedor,
 * RecojoService.registrar). El motivo es obligatorio: no hay correccion silenciosa de un numero que
 * alimenta la liquidacion.
 */
record RecojoCorreccionRequest(@NotNull @PositiveOrZero Double litros, @NotBlank String motivo) {}

enum PeriodoReporteAcopiador {
    DIA,
    SEMANA
}

record ReporteAcopiadorDia(
        LocalDate fecha,
        double litros,
        long recepciones,
        long proveedoresVisitados,
        long noEntregados) {}

record ReporteAcopiadorResponse(
        Long acopiadorId,
        String acopiadorNombre,
        Long rutaId,
        String rutaNombre,
        String zona,
        LocalDate desde,
        LocalDate hasta,
        double litros,
        long recepciones,
        long proveedoresVisitados,
        long noEntregados,
        List<ReporteAcopiadorDia> dias) {}

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
        EstadoVisita estadoVisita,
        String motivoNoEntrega,
        Double distanciaProveedorM,
        Double distanciaPuntoEstablecidoM,
        boolean gpsFueraDeRango,
        boolean observado,
        String motivoObservacion,
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
                r.getEstadoVisita(),
                r.getMotivoNoEntrega(),
                r.getDistanciaProveedorM(),
                r.getDistanciaPuntoEstablecidoM(),
                r.isGpsFueraDeRango(),
                r.isObservado(),
                r.getMotivoObservacion(),
                resultadoCalidad);
    }
}
