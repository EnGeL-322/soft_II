package org.ramiro.huata.acopiadores;

import jakarta.validation.constraints.NotBlank;

record AcopiadorRequest(
        @NotBlank String nombres, @NotBlank String apellidos, String documentoIdentidad, String telefono, Long usuarioId) {}

record AcopiadorResponse(
        Long id, String nombres, String apellidos, String documentoIdentidad, String telefono, boolean activo) {
    static AcopiadorResponse from(Acopiador a) {
        return new AcopiadorResponse(
                a.getId(), a.getNombres(), a.getApellidos(), a.getDocumentoIdentidad(), a.getTelefono(), a.isActivo());
    }
}
