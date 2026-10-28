package org.ramiro.huata.compras;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code recibirAhora}: la compra al contado que llega con la factura entra al almacen en el mismo
 * acto. Nulo cuenta como si: es como funcionaba antes de V61, y asi un cliente que no lo envia (la
 * app movil) sigue igual. La web lo manda en false para dejar la compra pendiente de recepcion.
 */
public record CompraRequest(
        @NotNull Long proveedorCompraId,
        @NotNull LocalDate fecha,
        @Size(max = 60) String documento,
        @Size(max = 500) String observaciones,
        @NotEmpty List<@Valid CompraItemRequest> items,
        Boolean recibirAhora) {

    public CompraRequest(
            Long proveedorCompraId, LocalDate fecha, String documento, String observaciones, List<CompraItemRequest> items) {
        this(proveedorCompraId, fecha, documento, observaciones, items, null);
    }

    public boolean recibirEnElActo() {
        return recibirAhora == null || recibirAhora;
    }
}
