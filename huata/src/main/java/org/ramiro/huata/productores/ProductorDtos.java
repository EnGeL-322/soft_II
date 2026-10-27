package org.ramiro.huata.productores;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.ramiro.huata.common.DatosPersonales;

/**
 * Alta y edicion de proveedor.
 *
 * <p>{@code codigo} ya NO se recibe del cliente: el codigo del proveedor <b>es su DNI</b> y lo deriva
 * el servidor (ver {@code ProductorService.aplicarDatos}). Aceptarlo aparte permitia que el codigo y
 * el documento dijeran cosas distintas, que es como acabaron en la base codigos tipo "12" y "11222".
 */
record ProductorRequest(
        // Opcional (pedido del cliente, 2026-10-24). Si se escribe es su codigo; si no, se genera uno.
        @Pattern(regexp = "^$|" + DatosPersonales.DNI_REGEX, message = DatosPersonales.DNI_MENSAJE)
                String documentoIdentidad,
        @NotBlank @Size(max = 150)
                @Pattern(regexp = DatosPersonales.NOMBRE_REGEX, message = DatosPersonales.NOMBRE_MENSAJE)
                String nombres,
        @NotBlank @Size(max = 150)
                @Pattern(regexp = DatosPersonales.NOMBRE_REGEX, message = DatosPersonales.NOMBRE_MENSAJE)
                String apellidos,
        @Pattern(regexp = DatosPersonales.TELEFONO_OPCIONAL_REGEX, message = DatosPersonales.TELEFONO_MENSAJE)
                String telefono,
        Long rutaId,
        String notas,
        Long acopiadorResponsableId,
        String zonaPrincipal,
        Integer ordenEnRuta,
        String username,
        String password) {}

record ProductorEstadoRequest(EstadoProductor estado, String motivo) {}

/** 7.4: la marca se concede o se retira, y el motivo queda en la bitacora. */
record ProductorExcelenciaRequest(boolean excelencia, String motivo) {}

/**
 * Solo la ruta (y su orden) del proveedor, para traerlo o moverlo desde "Rutas y mapa" sin rehacer
 * la ficha. {@code ordenEnRuta} nulo = sin orden fijo dentro de la ruta.
 */
record ProductorRutaRequest(@jakarta.validation.constraints.NotNull Long rutaId, Integer ordenEnRuta) {}

record ProductorResponse(
        Long id,
        String codigo,
        String nombres,
        String apellidos,
        String documentoIdentidad,
        String telefono,
        Long rutaId,
        String rutaNombre,
        EstadoProductor estado,
        String notas,
        boolean excelencia,
        boolean activo,
        Long acopiadorResponsableId,
        String acopiadorResponsableNombre,
        String zonaPrincipal,
        Integer ordenEnRuta,
        Long usuarioId,
        String username) {

    static ProductorResponse from(Productor p) {
        return new ProductorResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombres(),
                p.getApellidos(),
                p.getDocumentoIdentidad(),
                p.getTelefono(),
                p.getRuta() != null ? p.getRuta().getId() : null,
                p.getRuta() != null ? p.getRuta().getNombre() : null,
                p.getEstado(),
                p.getNotas(),
                p.isExcelencia(),
                p.isActivo(),
                p.getAcopiadorResponsable() != null ? p.getAcopiadorResponsable().getId() : null,
                p.getAcopiadorResponsable() != null
                        ? p.getAcopiadorResponsable().getNombres() + " " + p.getAcopiadorResponsable().getApellidos()
                        : null,
                p.getZonaPrincipal(),
                p.getOrdenEnRuta(),
                p.getUsuario() != null ? p.getUsuario().getId() : null,
                p.getUsuario() != null ? p.getUsuario().getUsername() : null);
    }
}
