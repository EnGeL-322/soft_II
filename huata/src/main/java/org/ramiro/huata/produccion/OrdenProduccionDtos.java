package org.ramiro.huata.produccion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Planificar una produccion. Sin fecha: hoy. */
record OrdenProduccionRequest(
        @NotNull Long productoId,
        @NotNull @Positive @Max(1_000_000) Integer cantidad,
        LocalDate fechaPlan,
        @Size(max = 500) String observaciones) {}

record AnularOrdenRequest(@NotBlank @Size(max = 300) String motivo) {}

/**
 * Un insumo de la orden: cuanto necesita y cuanto le toca del almacen despues de reservar lo de las
 * ordenes planificadas antes que ella.
 */
record OrdenLineaResponse(
        Long materiaPrimaId,
        String materiaPrimaCodigo,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal necesita,
        BigDecimal disponible,
        BigDecimal falta,
        boolean esLeche) {}

record OrdenProduccionResponse(
        Long id,
        String codigo,
        Long productoId,
        String productoCodigo,
        String productoNombre,
        String unidadVenta,
        Long recetaId,
        Integer cantidad,
        LocalDate fechaPlan,
        OrdenProduccion.Estado estado,
        /** Solo en PENDIENTE: si el almacen alcanza para producirla ya. Nulo en el resto. */
        Boolean lista,
        List<OrdenLineaResponse> lineas,
        Long loteId,
        String loteCodigo,
        String observaciones,
        String motivoAnulacion,
        String registradoPor) {}

/**
 * Lo que falta comprar para las ordenes pendientes. {@code enCamino}: ya comprado y sin recibir, que
 * se descuenta para no comprar dos veces. La leche se informa pero no se compra.
 */
record NecesidadResponse(
        Long materiaPrimaId,
        String materiaPrimaCodigo,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal requerido,
        BigDecimal disponible,
        BigDecimal enCamino,
        BigDecimal aComprar,
        BigDecimal costoReferencial,
        boolean esLeche,
        /** Codigos de las ordenes que lo piden. */
        List<String> ordenes) {}
