package org.ramiro.huata.liquidaciones;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

record TarifaLecheRequest(
        @NotNull Double precioNormal,
        @NotNull Double precioObservada,
        /** Opcional: una tarifa puede no ofrecer Excelencia. Si falta, el marcado cobra el normal. */
        Double precioExcelencia,
        @NotNull LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        /** Codigo de 6 digitos enviado al correo de quien cambia los precios. Lo exige el controlador. */
        String codigo) {}

record TarifaLecheResponse(Long id, Double precioNormal, Double precioObservada, Double precioExcelencia, LocalDate vigenteDesde, LocalDate vigenteHasta) {
    static TarifaLecheResponse from(TarifaLeche t) {
        return new TarifaLecheResponse(
                t.getId(), t.getPrecioNormal(), t.getPrecioObservada(), t.getPrecioExcelencia(),
                t.getVigenteDesde(), t.getVigenteHasta());
    }
}

record LiquidacionCalcularRequest(@NotNull Long productorId, @NotNull LocalDate periodoInicio, @NotNull LocalDate periodoFin) {}

record CambiarEstadoLiquidacionRequest(@NotNull EstadoLiquidacion estado) {}

/** Codigo de 6 digitos enviado al correo de quien pide la reapertura. Lo exige el controlador. */
record ReabrirLiquidacionRequest(@jakarta.validation.constraints.NotBlank String codigo) {}

record CerrarCicloRequest(@NotNull LocalDate periodoInicio, @NotNull LocalDate periodoFin) {}

/**
 * Resultado del "Pagar a todos". Distingue los proveedores liquidados en esta pasada de los que ya
 * estaban cerrados, para que la interfaz pueda decir la verdad en vez de un "listo" generico.
 */
record CierreCicloResponse(
        LocalDate periodoInicio,
        LocalDate periodoFin,
        int proveedoresLiquidados,
        int proveedoresYaCerrados,
        Double litrosTotales,
        Double montoNetoTotal,
        List<LiquidacionResponse> liquidaciones) {}

/**
 * El monto viaja siempre en positivo: el signo lo decide el tipo (regla 7.7). Se valida en el
 * servidor y no solo en el formulario, porque el importe acaba en la boleta del proveedor.
 */
record AjusteLiquidacionRequest(
        @NotNull TipoAjuste tipo, @NotNull @PositiveOrZero Double monto, @Size(max = 200) String motivo) {}

record AjusteLiquidacionResponse(Long id, TipoAjuste tipo, Double monto, Double montoConSigno, String motivo) {
    static AjusteLiquidacionResponse from(AjusteLiquidacion a) {
        return new AjusteLiquidacionResponse(a.getId(), a.getTipo(), a.getMonto(), a.montoConSigno(), a.getMotivo());
    }
}

record LiquidacionDetalleResponse(Long recojoId, java.time.Instant fechaHora, Double litros, Double precioAplicado, Double subtotal) {
    static LiquidacionDetalleResponse from(LiquidacionDetalle d) {
        return new LiquidacionDetalleResponse(
                d.getRecojo().getId(), d.getRecojo().getFechaHora(), d.getLitros(), d.getPrecioAplicado(), d.getSubtotal());
    }
}

record LiquidacionResponse(
        Long id,
        Long productorId,
        String productorNombre,
        LocalDate periodoInicio,
        LocalDate periodoFin,
        Double litrosTotales,
        Double montoTotal,
        Double ajustesTotal,
        Double montoNeto,
        EstadoLiquidacion estado,
        List<LiquidacionDetalleResponse> detalle,
        List<AjusteLiquidacionResponse> ajustes) {

    static LiquidacionResponse from(Liquidacion l, List<LiquidacionDetalle> detalles) {
        return from(l, detalles, List.of());
    }

    static LiquidacionResponse from(Liquidacion l, List<LiquidacionDetalle> detalles, List<AjusteLiquidacion> ajustes) {
        return new LiquidacionResponse(
                l.getId(),
                l.getProductor().getId(),
                l.getProductor().getNombres() + " " + l.getProductor().getApellidos(),
                l.getPeriodoInicio(),
                l.getPeriodoFin(),
                l.getLitrosTotales(),
                l.getMontoTotal(),
                l.getAjustesTotal(),
                l.getMontoNeto(),
                l.getEstado(),
                detalles == null ? List.of() : detalles.stream().map(LiquidacionDetalleResponse::from).toList(),
                ajustes == null ? List.of() : ajustes.stream().map(AjusteLiquidacionResponse::from).toList());
    }
}
