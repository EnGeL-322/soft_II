package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Venta de un producto sin elegir lote: el servidor saca primero lo que vence antes (FEFO). Igual que
 * {@link VentaQuesoRequest}, ni el precio, ni el estado de cobro, ni la fecha viajan en la peticion.
 */
public record VentaPorProductoRequest(
        @NotNull Long productoId,
        @NotBlank @Size(max = 150) String cliente,
        @NotNull @Positive Integer unidades,
        @NotNull TipoComprador tipoComprador,
        @NotNull CondicionVenta condicion,
        @NotNull MedioCobro medioCobro) {}
