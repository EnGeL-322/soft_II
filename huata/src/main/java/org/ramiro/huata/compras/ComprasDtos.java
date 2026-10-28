package org.ramiro.huata.compras;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record ProveedorCompraRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 150) String contacto,
        @Size(max = 30) String documento,
        @Size(max = 500) String observaciones) {}

record ProveedorCompraResponse(
        Long id, String codigo, String nombre, String contacto, String documento, String observaciones) {

    static ProveedorCompraResponse from(ProveedorCompra p) {
        return new ProveedorCompraResponse(
                p.getId(), p.getCodigo(), p.getNombre(), p.getContacto(), p.getDocumento(), p.getObservaciones());
    }
}

record CompraItemResponse(
        Long id,
        Long materiaPrimaId,
        String materiaPrimaCodigo,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal cantidad,
        BigDecimal costoUnitario,
        BigDecimal total,
        /** Lo que ya llego al almacen (V61). */
        BigDecimal cantidadRecibida,
        BigDecimal pendiente) {

    static CompraItemResponse from(CompraItem item) {
        return new CompraItemResponse(
                item.getId(),
                item.getMateriaPrima().getId(),
                item.getMateriaPrima().codigo(),
                item.getMateriaPrima().getNombre(),
                item.getMateriaPrima().getUnidadMedida().name(),
                item.getCantidad(),
                item.getCostoUnitario(),
                item.total(),
                item.getCantidadRecibida(),
                item.pendiente());
    }
}

record CompraResponse(
        Long id,
        /** OC-0001: sale del id. */
        String codigo,
        Long proveedorCompraId,
        String proveedorCompraNombre,
        LocalDate fecha,
        String documento,
        String observaciones,
        List<CompraItemResponse> items,
        BigDecimal total,
        /** false si se anulo (V59); la compra sigue listada con su motivo. */
        boolean activo,
        String motivoAnulacion,
        /** Si ya llego lo comprado (V61). */
        EstadoCompra estado,
        /** Quien la registro (usuario de la sesion, por auditoria). */
        String registradoPor) {

    static CompraResponse from(Compra c) {
        return new CompraResponse(
                c.getId(),
                c.codigo(),
                c.getProveedor().getId(),
                c.getProveedor().getNombre(),
                c.getFecha(),
                c.getDocumento(),
                c.getObservaciones(),
                c.getItems().stream().map(CompraItemResponse::from).toList(),
                c.total(),
                c.isActivo(),
                c.getMotivoAnulacion(),
                c.getEstado(),
                c.getCreatedBy());
    }
}

/** Lo que llego de una compra (V61). Sin fecha: hoy. Sin documento: el de la compra. */
record RecepcionRequest(
        LocalDate fecha,
        @Size(max = 60) String documento,
        @jakarta.validation.constraints.NotEmpty List<@jakarta.validation.Valid RecepcionItemRequest> items) {}

record RecepcionItemRequest(
        @jakarta.validation.constraints.NotNull Long compraItemId,
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive BigDecimal cantidad,
        @Size(max = 60) String lote,
        LocalDate fechaVencimiento) {}

record RecepcionItemResponse(
        Long materiaPrimaId,
        String materiaPrimaCodigo,
        String materiaPrimaNombre,
        String unidadMedida,
        BigDecimal cantidad,
        String lote,
        LocalDate fechaVencimiento) {

    static RecepcionItemResponse from(RecepcionItem i) {
        var m = i.getCompraItem().getMateriaPrima();
        return new RecepcionItemResponse(
                m.getId(), m.codigo(), m.getNombre(), m.getUnidadMedida().name(), i.getCantidad(), i.getLote(),
                i.getFechaVencimiento());
    }
}

record RecepcionResponse(
        Long id,
        String codigo,
        Long compraId,
        String compraCodigo,
        String proveedorNombre,
        LocalDate fecha,
        String documento,
        String recibidoPor,
        BigDecimal total,
        List<RecepcionItemResponse> items) {

    static RecepcionResponse from(RecepcionCompra r) {
        return new RecepcionResponse(
                r.getId(),
                r.codigo(),
                r.getCompra().getId(),
                r.getCompra().codigo(),
                r.getCompra().getProveedor().getNombre(),
                r.getFecha(),
                r.getDocumento(),
                r.getCreatedBy(),
                r.total(),
                r.getItems().stream().map(RecepcionItemResponse::from).toList());
    }
}

/** Anular una compra registrada por error (V59). */
record AnularCompraRequest(@jakarta.validation.constraints.NotBlank @Size(max = 300) String motivo) {}
