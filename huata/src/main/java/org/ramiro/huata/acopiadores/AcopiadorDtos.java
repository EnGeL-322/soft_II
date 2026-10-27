package org.ramiro.huata.acopiadores;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.ramiro.huata.common.DatosPersonales;

/** Mismas reglas de forma que el proveedor, pero el DNI y el telefono del acopiador son opcionales. */
record AcopiadorRequest(
        @NotBlank @Size(max = 150)
                @Pattern(regexp = DatosPersonales.NOMBRE_REGEX, message = DatosPersonales.NOMBRE_MENSAJE)
                String nombres,
        @NotBlank @Size(max = 150)
                @Pattern(regexp = DatosPersonales.NOMBRE_REGEX, message = DatosPersonales.NOMBRE_MENSAJE)
                String apellidos,
        // Opcional (pedido del cliente, 2026-10-24); si se escribe, tiene que ser un DNI valido.
        @Pattern(regexp = "^$|" + DatosPersonales.DNI_REGEX, message = DatosPersonales.DNI_MENSAJE)
                String documentoIdentidad,
        @Pattern(regexp = DatosPersonales.TELEFONO_OPCIONAL_REGEX, message = DatosPersonales.TELEFONO_MENSAJE)
                String telefono,
        Long rutaId,
        String username,
        String password) {}

record AcopiadorResponse(
        Long id,
        String nombres,
        String apellidos,
        String documentoIdentidad,
        String telefono,
        Long rutaId,
        String rutaNombre,
        String zona,
        Long usuarioId,
        String username,
        boolean activo) {
    static AcopiadorResponse from(Acopiador a) {
        return new AcopiadorResponse(
                a.getId(),
                a.getNombres(),
                a.getApellidos(),
                a.getDocumentoIdentidad(),
                a.getTelefono(),
                a.getRuta() != null ? a.getRuta().getId() : null,
                a.getRuta() != null ? a.getRuta().getNombre() : null,
                a.getRuta() != null ? a.getRuta().getZona() : null,
                a.getUsuario() != null ? a.getUsuario().getId() : null,
                a.getUsuario() != null ? a.getUsuario().getUsername() : null,
                a.isActivo());
    }
}
