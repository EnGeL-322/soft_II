package org.ramiro.huata.recojo;

import java.time.LocalDate;

/**
 * Algo cambio en la conciliacion de una ruta: se registro un recojo, se cancelo, o planta anoto su
 * total. Lo consume {@link AcopioTiempoRealPublisher} <b>despues del commit</b>.
 *
 * <p>Lleva solo las coordenadas del cambio (fecha + ruta) y no la fila ya armada, a proposito: la
 * fila se recalcula al publicar, cuando la escritura ya esta confirmada. Armarla dentro de la
 * transaccion y difundirla al commit dejaria pasar numeros de una transaccion que todavia podia
 * fallar.
 */
record AcopioCambioEvent(LocalDate fecha, Long rutaId, String tipo) {}
