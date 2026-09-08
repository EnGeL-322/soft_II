package org.ramiro.huata.parametros;

import jakarta.validation.constraints.NotBlank;

record ParametroNegocioRequest(@NotBlank String clave, @NotBlank String valor, String descripcion) {}

record ParametroNegocioResponse(Long id, String clave, String valor, String descripcion) {
    static ParametroNegocioResponse from(ParametroNegocio p) {
        return new ParametroNegocioResponse(p.getId(), p.getClave(), p.getValor(), p.getDescripcion());
    }
}
