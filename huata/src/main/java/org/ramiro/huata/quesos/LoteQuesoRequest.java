package org.ramiro.huata.quesos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO de la pantalla "Registrar produccion".
 *
 * <p><b>El consumo no lo manda el cliente</b> (decision del cliente, 2026-09-30): el servidor
 * descuenta exactamente receta x {@code cantidadObjetivo}. Antes se aceptaba una lista de consumos
 * editable ("lo que se uso de verdad") y el inventario bajaba lo que la persona escribiera, no lo que
 * dice la receta. Una APK vieja que todavia mande {@code consumos} no falla: el campo se ignora
 * ({@code ignoreUnknown}, explicito para no depender de la configuracion global de Jackson).
 *
 * <p>{@code cantidadObjetivo} es lo que se puso a producir (sobre eso se consume);
 * {@code unidadesProducidas} es lo que salio y entra a "Guardado para vender". La diferencia es la
 * merma. Si no llega objetivo, se consume sobre lo producido.
 *
 * <p>{@code litrosDestinados} es opcional: si la receta lleva la leche del acopio, son los litros que
 * se descuentan y lo pone el servidor. Solo se usa lo que mande el cliente cuando la receta no lleva
 * leche (datos anteriores a esta regla).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoteQuesoRequest(
        @NotNull LocalDate fecha,
        @NotNull Long productoId,
        @NotNull OrigenLeche origenLeche,
        @Positive Double litrosDestinados,
        @NotNull @Positive Integer unidadesProducidas,
        @NotBlank @Size(max = 120) String responsable,
        @Size(max = 500) String observaciones,
        @NotNull Long recetaId,
        @Positive Integer cantidadObjetivo,
        List<@Valid ManoObraItemRequest> manoObra,
        @PositiveOrZero Integer quesosReparados,
        @PositiveOrZero Integer quesosRecorte,
        @PositiveOrZero Double recorteKg,
        /** Opcional: si falta, se calcula con los dias que dura el producto. */
        LocalDate fechaVencimiento,
        /** Opcional (V61): la orden de produccion que este lote cumple. */
        Long ordenId) {

    /** Sin orden de produccion: el registro directo, como antes de V61. */
    public LoteQuesoRequest(
            LocalDate fecha,
            Long productoId,
            OrigenLeche origenLeche,
            Double litrosDestinados,
            Integer unidadesProducidas,
            String responsable,
            String observaciones,
            Long recetaId,
            Integer cantidadObjetivo,
            List<ManoObraItemRequest> manoObra,
            Integer quesosReparados,
            Integer quesosRecorte,
            Double recorteKg,
            LocalDate fechaVencimiento) {
        this(fecha, productoId, origenLeche, litrosDestinados, unidadesProducidas, responsable, observaciones,
                recetaId, cantidadObjetivo, manoObra, quesosReparados, quesosRecorte, recorteKg, fechaVencimiento, null);
    }

    /** Sin fecha de vencimiento: la calcula el servidor con los dias que dura el producto. */
    public LoteQuesoRequest(
            LocalDate fecha,
            Long productoId,
            OrigenLeche origenLeche,
            Double litrosDestinados,
            Integer unidadesProducidas,
            String responsable,
            String observaciones,
            Long recetaId,
            Integer cantidadObjetivo,
            List<ManoObraItemRequest> manoObra,
            Integer quesosReparados,
            Integer quesosRecorte,
            Double recorteKg) {
        this(fecha, productoId, origenLeche, litrosDestinados, unidadesProducidas, responsable, observaciones,
                recetaId, cantidadObjetivo, manoObra, quesosReparados, quesosRecorte, recorteKg, null, null);
    }

    /** Sobre cuanto se calcula el consumo: lo que se puso a producir, o lo producido si no se informo. */
    public int cantidadAConsumir() {
        return cantidadObjetivo != null ? cantidadObjetivo : unidadesProducidas;
    }
}
