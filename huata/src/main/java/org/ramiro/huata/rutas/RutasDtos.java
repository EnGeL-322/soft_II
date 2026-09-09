package org.ramiro.huata.rutas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record RutaRequest(@NotBlank String nombre, String zona, String descripcion) {}

record RutaResponse(Long id, String nombre, String zona, String descripcion, boolean activo) {
    static RutaResponse from(Ruta r) {
        return new RutaResponse(r.getId(), r.getNombre(), r.getZona(), r.getDescripcion(), r.isActivo());
    }
}

record CentroAcopioRequest(@NotBlank String nombre, String ubicacion, Long rutaId) {}

record CentroAcopioResponse(Long id, String nombre, String ubicacion, Long rutaId, String rutaNombre, boolean activo) {
    static CentroAcopioResponse from(CentroAcopio c) {
        return new CentroAcopioResponse(
                c.getId(),
                c.getNombre(),
                c.getUbicacion(),
                c.getRuta() != null ? c.getRuta().getId() : null,
                c.getRuta() != null ? c.getRuta().getNombre() : null,
                c.isActivo());
    }
}

record AsignacionRutaRequest(@NotNull Long rutaId, @NotNull Long acopiadorId, @NotNull LocalDate fecha, String movilidad) {}

record AsignacionActualizarRequest(String movilidad, String observacion) {}

record AsignacionRutaResponse(
        Long id,
        Long rutaId,
        String rutaNombre,
        Long acopiadorId,
        String acopiadorNombre,
        LocalDate fecha,
        String movilidad,
        String observacion) {
    static AsignacionRutaResponse from(AsignacionRuta a) {
        return new AsignacionRutaResponse(
                a.getId(),
                a.getRuta().getId(),
                a.getRuta().getNombre(),
                a.getAcopiador().getId(),
                a.getAcopiador().getNombres() + " " + a.getAcopiador().getApellidos(),
                a.getFecha(),
                a.getMovilidad(),
                a.getObservacion());
    }
}
