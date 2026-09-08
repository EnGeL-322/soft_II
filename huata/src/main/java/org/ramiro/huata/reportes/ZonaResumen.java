package org.ramiro.huata.reportes;

/**
 * "Zonas y rendimiento": compara produccion de la ultima semana vs la anterior, por zona.
 * Publico porque tambien lo consume el modulo de exportacion (hoja ResumenZonas).
 */
public record ZonaResumen(
        String zona,
        double litrosPeriodoAnterior,
        double litrosPeriodoActual,
        double variacionPorciento,
        double participacionPorciento) {}
