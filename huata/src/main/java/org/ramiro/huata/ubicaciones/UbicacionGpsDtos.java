package org.ramiro.huata.ubicaciones;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

record UbicacionRequest(@NotNull Double latitud, @NotNull Double longitud, Double precisionMetros, Double velocidadMs) {}

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
