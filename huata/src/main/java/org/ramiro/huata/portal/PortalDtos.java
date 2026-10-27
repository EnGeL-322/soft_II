package org.ramiro.huata.portal;

import java.time.LocalDate;
import java.util.List;

/** Fila del historial publicado: fecha, resultado y litros. Nunca datos personales. */
record HistorialPublico(String fecha, String resultado, Double litros) {}

/**
 * Respuesta del portal publico. Alcance deliberadamente minimo: codigo, zona, estado e historial
 * reciente. El alcance definitivo de publicacion lo tiene que aprobar por escrito la
 * administracion de Huata (DESARROLLO.md seccion 8.3).
 */
record ConsultaPublica(String codigo, String zona, String estado, List<HistorialPublico> historial) {}

/**
 * Calidad de una zona en una fecha (RF-48). {@code estado} es el peor resultado del dia:
 * ADULTERACION si hubo alguna RECHAZADA, OBSERVADA si hubo alguna OBSERVADA sin RECHAZADA, APTA en
 * el resto de los casos. No agrega nada que no venga de {@code ControlCalidad} registrado ese dia:
 * sin controles esa zona simplemente no aparece (regla 0: no inventar calidad).
 */
record ZonaCalidadPublica(
        String zona, long controles, long aceptadas, long observadas, long rechazadas, String estado) {}

/**
 * Un proveedor con mala calidad confirmada en el dia (RF-49). Se publica solo el codigo, nunca
 * nombre completo, documento ni telefono (RNF-12). La fuente es {@code IncidenciaCalidad}, no
 * cualquier {@code ControlCalidad} con resultado distinto de ACEPTADA: una incidencia es el evento
 * ya registrado/confirmado por calidad (RN-06), no una lectura aislada.
 *
 * <p>Definicion de "mala calidad" para este endpoint: toda incidencia abierta o en seguimiento
 * registrada ese dia, sin filtrar por tipo. La seccion 33 de CLAUDE.md deja esto como pregunta
 * abierta del negocio (que cuenta como primera/segunda deteccion); este es el criterio operativo
 * mas simple mientras no haya una definicion formal distinta.
 */
record IncidenciaPublica(String productorCodigo, String zona, String tipo, String fecha) {}

/** Resumen publico del dia: zonas + incidencias nombradas. Ambos parametrizables por fecha. */
record ResumenPublicoHoy(LocalDate fecha, List<ZonaCalidadPublica> zonas, List<IncidenciaPublica> incidencias) {}
