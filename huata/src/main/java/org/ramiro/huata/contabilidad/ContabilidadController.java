package org.ramiro.huata.contabilidad;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan de cuentas, libro diario y libro mayor de la queseria (RN-26, RN-25). Solo lectura: los
 * asientos se generan automaticamente desde compras, produccion y venta de queso.
 */
@RestController
@RequestMapping("/api/v1/contabilidad")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.CONTABILIDAD_READ + "')")
public class ContabilidadController {

    private final AsientoContableService asientoContableService;

    @GetMapping("/plan-cuentas")
    public List<PlanCuentaResponse> planCuentas() {
        return asientoContableService.planCuentas();
    }

    @GetMapping("/libro-diario")
    public List<AsientoContableResponse> libroDiario(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return asientoContableService.libroDiario(desde, hasta);
    }

    @GetMapping("/libro-mayor")
    public LibroMayorResponse libroMayor(
            @RequestParam String cuenta,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return asientoContableService.libroMayor(cuenta, desde, hasta);
    }

    @GetMapping("/libro-mayor/cuentas")
    public List<PlanCuentaResponse> cuentasConMovimiento() {
        return asientoContableService.cuentasConMovimiento();
    }
}
