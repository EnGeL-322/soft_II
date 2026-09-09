package org.ramiro.huata.rutas;

import java.time.Instant;

/**
 * Una fila del "Plan operativo de recojo": la ruta, quién la tiene asignada ese día (si alguien) y
 * lo que realmente se recogió. Público porque también lo usa el módulo de exportación.
 */
public record PlanRutaDia(
        Long rutaId,
        String rutaNombre,
        String zona,
        Long asignacionId,
        Long acopiadorId,
        String acopiadorNombre,
        String movilidad,
        String observacion,
        EstadoPlanRuta estado,
        double litros,
        long recepciones,
        Instant ultimoRegistro) {}
