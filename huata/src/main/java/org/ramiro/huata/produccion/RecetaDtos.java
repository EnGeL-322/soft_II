package org.ramiro.huata.produccion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.ramiro.huata.inventario.UnidadMedida;

/**
 * Un ingrediente de la receta: uno que ya existe ({@code materiaPrimaId}) o uno que se escribe por
 * primera vez ({@code nombre} + {@code unidadMedida}). Los nuevos se crean en la misma transaccion que
 * la receta: si la receta no se guarda, tampoco quedan insumos sueltos en el inventario.
 */
record RecetaItemRequest(
        Long materiaPrimaId,
        @Size(max = 120) String nombre,
        UnidadMedida unidadMedida,
        @NotNull @Positive BigDecimal cantidadPorUnidad) {

    RecetaItemRequest(Long materiaPrimaId, BigDecimal cantidadPorUnidad) {
        this(materiaPrimaId, null, null, cantidadPorUnidad);
    }
}

record RecetaRequest(
        @NotNull Long productoId,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        BigDecimal rendimientoLitrosPorUnidad,
        String observaciones,
        @NotEmpty List<@Valid RecetaItemRequest> items) {}

record RecetaItemResponse(Long materiaPrimaId, String materiaPrimaNombre, String unidadMedida, BigDecimal cantidadPorUnidad) {

    static RecetaItemResponse from(RecetaItem item) {
        return new RecetaItemResponse(
                item.getMateriaPrima().getId(),
                item.getMateriaPrima().getNombre(),
                item.getMateriaPrima().getUnidadMedida().name(),
                item.getCantidadPorUnidad());
    }
}

record RecetaResponse(
        Long id,
        Long productoId,
        String productoNombre,
        String unidadVenta,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        BigDecimal rendimientoLitrosPorUnidad,
        String observaciones,
        List<RecetaItemResponse> items) {

    static RecetaResponse from(RecetaQueso r) {
        return new RecetaResponse(
                r.getId(),
                r.getProducto().getId(),
                r.getProducto().getNombre(),
                r.getProducto().getUnidadVenta(),
                r.getVigenteDesde(),
                r.getVigenteHasta(),
                r.getRendimientoLitrosPorUnidad(),
                r.getObservaciones(),
                r.getItems().stream().map(RecetaItemResponse::from).toList());
    }
}

record SimulacionRequest(@NotNull @Positive Integer cantidadObjetivo) {}

/** Una fila de la simulacion: cuanto haria falta de una materia prima y si el stock actual alcanza. */
record SimulacionItemResponse(
        Long materiaPrimaId,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal cantidadRequerida,
        BigDecimal stockActual,
        boolean alcanza) {}

record SimulacionResponse(Long recetaId, Integer cantidadObjetivo, List<SimulacionItemResponse> items, boolean alcanzaTodo) {}

// ---- Capacidad de produccion: cuanto se puede producir con lo que hay en el almacen ----

/**
 * Un insumo de la receta frente a su stock. {@code alcanzaPara} son unidades completas del producto;
 * {@code limitante} marca el que decide el maximo.
 */
record CapacidadItemResponse(
        Long materiaPrimaId,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal cantidadPorUnidad,
        BigDecimal stockActual,
        int alcanzaPara,
        boolean limitante,
        boolean esLeche) {}

/**
 * Lo que necesita la pantalla "Registrar produccion" en una sola respuesta. Sin producto elegido solo
 * trae la leche disponible (la tarjeta de arriba). Sin receta, {@code tieneReceta} es false y no hay
 * maximo: la pantalla avisa en vez de inventar uno.
 */
record CapacidadProduccionResponse(
        Long productoId,
        String productoNombre,
        String unidadVenta,
        Long recetaId,
        boolean tieneReceta,
        boolean recetaLlevaLeche,
        Integer maximoProducible,
        Long limitanteId,
        String limitanteNombre,
        BigDecimal lecheDisponible,
        List<CapacidadItemResponse> items) {}
