package org.ramiro.huata.recojo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

record ConciliacionRutaRequest(
        @NotNull LocalDate fecha,
        @NotNull Long rutaId,
        Long acopiadorId,
        @NotNull @PositiveOrZero Double litrosRecibidosPlanta,
        // El responsable ya no se recibe: es la cuenta que registra (ver ConciliacionRutaService).
        String observacion) {}

/**
 * Una parada de la ruta ya registrada: cuanto se acopio en ese proveedor y a que hora (RF-53).
 *
 * <p>Las paradas todavia no visitadas (blanco) no aparecen aqui: esta lista son hechos registrados,
 * no la plantilla de la ruta. Quien necesita el plan completo lo pide a {@code /rutas/{id}/proveedores}.
 */
record ConciliacionVisitaResponse(
        Long recojoId,
        Long productorId,
        String productorCodigo,
        String productorNombre,
        Instant fechaHora,
        Double litros,
        FuenteRecojo fuente,
        EstadoVisita estadoVisita,
        String motivoNoEntrega,
        String acopiadorNombre,
        /** Resultado del control de calidad; null si todavia no se analizo. */
        org.ramiro.huata.calidad.ResultadoCalidad calidad) {}

/**
 * El total recolectado y la diferencia se calculan, no se leen de una columna (ver
 * {@link ConciliacionRuta}). Positivo = llego menos a planta de lo recolectado.
 *
 * <p>{@code totalRecolectado} se desglosa en {@code litrosEnRuta} (lo que el acopiador cargo en el
 * camino) y {@code litrosEntregadosEnPlanta} (proveedores que llevaron su leche directamente). Los
 * dos suman el total, pero solo el primero viajo en la movilidad: contar una entrega directa como
 * "recolectado en ruta" haria aparecer una diferencia que nunca existio.
 */
record ConciliacionRutaResponse(
        Long id,
        LocalDate fecha,
        Long rutaId,
        String rutaNombre,
        /** Zona de la ruta: la conciliacion tambien se lee agrupada por zona (sector). */
        String zona,
        Long acopiadorId,
        String acopiadorNombre,
        Double totalRecolectado,
        Double litrosEnRuta,
        Double litrosEntregadosEnPlanta,
        Integer proveedoresAcopiados,
        Integer proveedoresNoEntregaron,
        Instant ultimoAcopio,
        Double litrosRecibidosPlanta,
        Double diferencia,
        /** Leche de la ruta que no entra al almacen: rechazada, o observada si asi se configuro. */
        Double litrosNoAptos,
        /** Lo que entro al inventario: recibido en planta menos no aptos. Null si planta no registro. */
        Double litrosAlInventario,
        String responsable,
        String observacion,
        List<ConciliacionVisitaResponse> detalle,
        /**
         * Si hay algo que recibir de esta ruta: visitas del acopiador o un total ya anotado. Falso cuando
         * solo hubo entregas directas en planta (ya estan en planta, no hay llegada que anotar). La
         * pantalla quita de la lista una fila que llega en vivo con false.
         */
        boolean requiereRecepcion) {}

/**
 * Lo que sale por el WebSocket de acopio cada vez que cambia una fila de la conciliacion.
 *
 * <p>Lleva la fila ya recalculada por el servidor en vez de solo el recojo nuevo: si el navegador
 * tuviera que sumar el litraje por su cuenta, dos clientes con distinto historial de mensajes
 * (uno recien conectado, otro que perdio un mensaje) mostrarian totales distintos. Es el mismo
 * criterio anti-drift que ya aplica {@link ConciliacionRuta} al no guardar el total.
 */
record EventoAcopioTiempoReal(String tipo, ConciliacionRutaResponse conciliacion) {

    static final String RECOJO = "RECOJO";
    static final String PLANTA = "PLANTA";
}
