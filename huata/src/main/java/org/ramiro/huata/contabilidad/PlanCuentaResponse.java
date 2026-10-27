package org.ramiro.huata.contabilidad;

public record PlanCuentaResponse(Long id, String codigo, String nombre, TipoCuenta tipo) {
    static PlanCuentaResponse from(PlanCuenta c) {
        return new PlanCuentaResponse(c.getId(), c.getCodigo(), c.getNombre(), c.getTipo());
    }
}
