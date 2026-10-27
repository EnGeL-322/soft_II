package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Precios de venta del queso. Se editan en Configuracion, no en la pantalla de venta.
 *
 * <p>Como la tarifa de leche, no se corrige la fila vigente: se crea una nueva con su fecha de
 * inicio. Reescribir la vigente cambiaria el precio de ventas ya emitidas.
 */
public record TarifaQuesoRequest(
        @NotNull Long productoId,
        @NotNull @PositiveOrZero Double precioPublicoGeneral,
        @NotNull @PositiveOrZero Double precioMayorista,
        @NotNull @PositiveOrZero Double precioProveedor,
        @NotNull LocalDate vigenteDesde,
        /** Con fecha de fin el periodo es una promocion: al terminar vuelve el precio anterior. */
        LocalDate vigenteHasta,
        @Size(max = 120) String descripcion,
        /** Codigo de 6 digitos enviado al correo de quien cambia los precios. Lo exige el controlador. */
        String codigo) {}
