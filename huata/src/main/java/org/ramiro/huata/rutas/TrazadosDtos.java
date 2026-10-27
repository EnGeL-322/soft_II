package org.ramiro.huata.rutas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que manda la app movil al pintar el recorrido del dia.
 *
 * <p>No trae {@code rutaId} ni {@code acopiadorId}: los dos los resuelve el servidor desde la sesion
 * y la asignacion del dia. Si los aceptara del cliente, un acopiador podria colgar su trazado de la
 * ruta de otro.
 *
 * <p>{@code fecha} es opcional y por defecto es hoy en Lima; se admite para que un trazado que
 * estuvo en la cola offline llegue con el dia en que se pinto y no con el dia en que se sincronizo.
 */
record TrazadoDiaRequest(
        LocalDate fecha,
        boolean sigueRutaDesignada,
        @Size(max = 500) String observacion,
        // Dos puntos es el minimo para que exista un recorrido; uno solo no es una linea.
        @NotEmpty @Size(min = 2, message = "Un recorrido necesita al menos dos puntos")
                List<@Valid TrazadoPuntoRequest> puntos) {}

record TrazadoPuntoRequest(@NotNull Integer secuencia, @NotNull Double latitud, @NotNull Double longitud) {}

record TrazadoPuntoResponse(Integer secuencia, Double latitud, Double longitud) {
    static TrazadoPuntoResponse from(TrazadoAcopiadorPunto p) {
        return new TrazadoPuntoResponse(p.getSecuencia(), p.getLatitud(), p.getLongitud());
    }
}

/**
 * Un trazado pintado, con el acopiador y la ruta a los que pertenece.
 *
 * <p>{@code esPredeterminado} dice si este acopiador ya tiene un recorrido habitual propio, para que
 * la web no tenga que cruzar dos listas y para que la app pueda avisar de que lo que pinte hoy no
 * cambia su predeterminado por si solo.
 */
record TrazadoAcopiadorResponse(
        Long id,
        Long acopiadorId,
        String acopiadorNombre,
        Long rutaId,
        String rutaNombre,
        String zona,
        TipoTrazado tipo,
        LocalDate fecha,
        boolean sigueRutaDesignada,
        String observacion,
        Long origenTrazadoId,
        Instant predeterminadoEn,
        String predeterminadoPor,
        List<TrazadoPuntoResponse> puntos) {

    static TrazadoAcopiadorResponse from(TrazadoAcopiador t, List<TrazadoAcopiadorPunto> puntos) {
        return new TrazadoAcopiadorResponse(
                t.getId(),
                t.getAcopiador().getId(),
                t.getAcopiador().getNombres() + " " + t.getAcopiador().getApellidos(),
                t.getRuta().getId(),
                t.getRuta().getNombre(),
                t.getRuta().getZona(),
                t.getTipo(),
                t.getFecha(),
                t.isSigueRutaDesignada(),
                t.getObservacion(),
                t.getOrigenTrazadoId(),
                t.getPredeterminadoEn(),
                t.getPredeterminadoPor(),
                puntos.stream().map(TrazadoPuntoResponse::from).toList());
    }
}

/**
 * Lo que la app movil necesita saber al abrir "Pintar ruta del dia": que ruta le toca, que pinto ya
 * hoy (si pinto), cual es su recorrido habitual y cual es el de la ruta.
 *
 * <p>Los tres trazados viajan juntos en una sola respuesta a proposito: la app los necesita los tres
 * para dibujar el mapa, y en una conexion rural tres peticiones son tres oportunidades de fallar.
 */
record MiTrazadoResponse(
        Long acopiadorId,
        String acopiadorNombre,
        Long rutaId,
        String rutaNombre,
        String zona,
        LocalDate fecha,
        TrazadoAcopiadorResponse delDia,
        TrazadoAcopiadorResponse predeterminado,
        /** Geometria de la ruta ({@code ruta_puntos}): el respaldo de quien no tiene trazado propio. */
        List<TrazadoPuntoResponse> deLaRuta) {}
