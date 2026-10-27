package org.ramiro.huata.quesos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El lote con su stock. {@code unidadesVendidas} y {@code stock} no son columnas: se calculan sobre
 * el kardex (movimientos_inventario), de modo que no pueden quedar desfasados de las filas que los
 * explican.
 */
record LoteQuesoResponse(
        Long id,
        String codigo,
        LocalDate fecha,
        Long productoId,
        String productoNombre,
        String unidadVenta,
        OrigenLeche origenLeche,
        Double litrosDestinados,
        Integer unidadesProducidas,
        Integer unidadesVendidas,
        Integer stock,
        Double rendimientoLitrosPorUnidad,
        String responsable,
        String observaciones,
        Long recetaId,
        Integer cantidadObjetivo,
        Integer merma,
        BigDecimal costoMateriaPrima,
        BigDecimal costoManoObra,
        BigDecimal costoTotal,
        BigDecimal costoUnitario,
        Integer quesosReparados,
        Integer quesosRecorte,
        Double recorteKg,
        LocalDate fechaVencimiento) {

    static LoteQuesoResponse from(QueseriaService.LoteConStock l) {
        LoteQueso lote = l.lote();
        return new LoteQuesoResponse(
                lote.getId(),
                lote.getCodigo(),
                lote.getFecha(),
                lote.getProducto().getId(),
                lote.getProducto().getNombre(),
                lote.getProducto().getUnidadVenta(),
                lote.getOrigenLeche(),
                lote.getLitrosDestinados(),
                lote.getUnidadesProducidas(),
                l.unidadesVendidas(),
                l.stock(),
                lote.rendimientoLitrosPorUnidad(),
                lote.getResponsable(),
                lote.getObservaciones(),
                lote.getReceta() != null ? lote.getReceta().getId() : null,
                lote.getCantidadObjetivo(),
                lote.merma(),
                lote.getCostoMateriaPrima(),
                lote.getCostoManoObra(),
                lote.getCostoTotal(),
                lote.getCostoUnitario(),
                lote.getQuesosReparados(),
                lote.getQuesosRecorte(),
                lote.getRecorteKg(),
                lote.getFechaVencimiento());
    }
}

/** Precio, IGV y estado de cobro ya resueltos por el servidor al momento de la venta (ver {@link VentaQuesoRequest}). */
record VentaQuesoResponse(
        Long id,
        Long loteId,
        String loteCodigo,
        String productoNombre,
        LocalDate fecha,
        String cliente,
        Integer unidades,
        TipoComprador tipoComprador,
        Double precioUnitario,
        Double total,
        Double montoIgv,
        CondicionVenta condicion,
        MedioCobro medioCobro,
        EstadoCobro estado,
        boolean anulada,
        String motivoAnulacion) {

    static VentaQuesoResponse from(VentaQueso v) {
        return new VentaQuesoResponse(
                v.getId(),
                v.getLote().getId(),
                v.getLote().getCodigo(),
                v.getLote().getProducto().getNombre(),
                v.getFecha(),
                v.getCliente(),
                v.getUnidades(),
                v.getTipoComprador(),
                v.getPrecioUnitario(),
                v.total(),
                v.getMontoIgv(),
                v.getCondicion(),
                v.getMedioCobro(),
                v.getEstado(),
                !v.isActivo(),
                v.getMotivoAnulacion());
    }
}

record AnularVentaRequest(@NotBlank @Size(max = 200) String motivo) {}

/**
 * Los cuatro KPI de la pantalla de venta. Se calculan en el backend y no en el navegador porque el
 * stock disponible es el mismo numero que decide si una venta se acepta: si la pantalla lo calculara
 * por su cuenta, podria ofrecer unidades que el servidor va a rechazar.
 */
record ResumenVentasResponse(
        Double ventaAcumulada, int operaciones, int stockDisponible, Double porCobrar) {}


/**
 * Un periodo de precios de un producto. {@code promocion} no es una columna: es un periodo con fecha
 * de fin, que es justo lo que lo hace temporal (al terminar vuelve a regir el precio anterior).
 */
record TarifaQuesoResponse(
        Long id,
        Long productoId,
        String productoNombre,
        Double precioPublicoGeneral,
        Double precioMayorista,
        Double precioProveedor,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta,
        String descripcion,
        boolean promocion) {

    static TarifaQuesoResponse from(TarifaQueso t) {
        return new TarifaQuesoResponse(
                t.getId(),
                t.getProducto().getId(),
                t.getProducto().getNombre(),
                t.getPrecioPublicoGeneral(),
                t.getPrecioMayorista(),
                t.getPrecioProveedor(),
                t.getVigenteDesde(),
                t.getVigenteHasta(),
                t.getDescripcion(),
                t.getVigenteHasta() != null);
    }
}
