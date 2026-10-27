package org.ramiro.huata.ubicaciones;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * @param fecha momento en que el dispositivo CAPTURO el punto. Es opcional por compatibilidad (la
 *     web lo reporta en vivo y el servidor pone la hora), pero la app movil debe enviarlo siempre:
 *     un punto guardado sin cobertura llega minutos u horas despues, y sin este dato el recorrido
 *     quedaria dibujado con la hora de envio en vez de la del recorrido real.
 */
record UbicacionRequest(
        @NotNull Double latitud,
        @NotNull Double longitud,
        Double precisionMetros,
        Double velocidadMs,
        Instant fecha) {}

/** Envio por lotes de los puntos que quedaron pendientes sin cobertura (RN-18). */
record UbicacionLoteRequest(@NotEmpty List<@Valid UbicacionRequest> puntos) {}

/**
 * @param recibidos puntos que quedaron guardados
 * @param duplicados puntos que ya estaban (un reintento tras un corte a medio envio no duplica el
 *     recorrido)
 */
record UbicacionLoteResponse(int recibidos, int duplicados) {}

record UbicacionResponse(
        Long acopiadorId,
        String acopiadorNombre,
        Double latitud,
        Double longitud,
        Double precisionMetros,
        Double velocidadMs,
        Instant fecha) {

    static UbicacionResponse from(UbicacionGps u) {
        return new UbicacionResponse(
                u.getAcopiador().getId(),
                u.getAcopiador().getNombres() + " " + u.getAcopiador().getApellidos(),
                u.getLatitud(),
                u.getLongitud(),
                u.getPrecisionMetros(),
                u.getVelocidadMs(),
                u.getFecha());
    }
}
