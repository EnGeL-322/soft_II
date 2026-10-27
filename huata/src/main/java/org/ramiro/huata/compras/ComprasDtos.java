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
        Long id, Long materiaPrimaId, String materiaPrimaNombre, BigDecimal cantidad, BigDecimal costoUnitario, BigDecimal total) {

    static CompraItemResponse from(CompraItem item) {
        return new CompraItemResponse(
                item.getId(),
                item.getMateriaPrima().getId(),
                item.getMateriaPrima().getNombre(),
                item.getCantidad(),
                item.getCostoUnitario(),
                item.total());
    }
}

record CompraResponse(
        Long id,
        Long proveedorCompraId,
        String proveedorCompraNombre,
        LocalDate fecha,
        String documento,
        String observaciones,
        List<CompraItemResponse> items,
        BigDecimal total,
        /** false si se anulo (V59); la compra sigue listada con su motivo. */
        boolean activo,
        String motivoAnulacion) {

    static CompraResponse from(Compra c) {
        return new CompraResponse(
                c.getId(),
                c.getProveedor().getId(),
                c.getProveedor().getNombre(),
                c.getFecha(),
                c.getDocumento(),
                c.getObservaciones(),
                c.getItems().stream().map(CompraItemResponse::from).toList(),
                c.total(),
                c.isActivo(),
                c.getMotivoAnulacion());
    }
}

/** Anular una compra registrada por error (V59). */
record AnularCompraRequest(@jakarta.validation.constraints.NotBlank @Size(max = 300) String motivo) {}
