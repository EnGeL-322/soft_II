package org.ramiro.huata.produccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ProductoRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotBlank @Size(max = 40) String unidadVenta,
        @Size(max = 500) String descripcion,
        /** Dias que dura desde que se produce; null = sin dato. */
        @jakarta.validation.constraints.Positive @jakarta.validation.constraints.Max(3650) Integer vidaUtilDias) {

    ProductoRequest(String nombre, String unidadVenta, String descripcion) {
        this(nombre, unidadVenta, descripcion, null);
    }
}

/**
 * {@code lotesRegistrados}: cuantas producciones tiene (anuladas incluidas). Solo lo trae el listado,
 * para que la pantalla diga de antemano si el producto se puede eliminar; null en el resto.
 */
record ProductoResponse(
        Long id,
        /** P-0001: sale del id. */
        String codigo,
        String nombre,
        String unidadVenta,
        String descripcion,
        boolean activo,
        Integer vidaUtilDias,
        Long lotesRegistrados) {

    static ProductoResponse from(Producto p) {
        return from(p, null);
    }

    static ProductoResponse from(Producto p, Long lotesRegistrados) {
        return new ProductoResponse(
                p.getId(),
                p.codigo(),
                p.getNombre(),
                p.getUnidadVenta(),
                p.getDescripcion(),
                p.isActivo(),
                p.getVidaUtilDias(),
                lotesRegistrados);
    }
}

/** Dias que dura un producto desde que se produce. */
record VidaUtilRequest(
        @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive
                @jakarta.validation.constraints.Max(3650) Integer vidaUtilDias) {}
