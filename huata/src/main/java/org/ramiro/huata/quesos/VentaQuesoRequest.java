package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Ni el precio, ni el estado de cobro, ni la fecha viajan en la peticion.
 *
 * <p>El precio lo pone la tarifa vigente segun {@code tipoComprador} (ver {@link TipoComprador}): si
 * lo escribiera quien vende, la lista de precios de la cooperativa seria una sugerencia. El estado
 * lo decide la condicion (ver {@link CondicionVenta}); si lo mandara el formulario, podria
 * registrarse una venta al contado que nace "por cobrar". La fecha es siempre la de hoy segun el
 * servidor (ver {@code QueseriaService.registrarVenta}): si viajara desde el formulario, el reloj
 * del celular u otra fecha manipulada podria fechar la venta antes o despues de cuando realmente
 * ocurrio.
 */
public record VentaQuesoRequest(
        @NotNull Long loteId,
        @NotBlank @Size(max = 150) String cliente,
        @NotNull @Positive Integer unidades,
        @NotNull TipoComprador tipoComprador,
        @NotNull CondicionVenta condicion,
        @NotNull MedioCobro medioCobro) {}
