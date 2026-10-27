package org.ramiro.huata.portal;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal público (sin autenticación): calidad por zona (RF-48), incidencias nombradas del día
 * (RF-49) y consulta de un proveedor por su propio código. No expone datos personales (documento,
 * teléfono) ni de otros proveedores: solo código, zona y resultado.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;

    @GetMapping("/productores/{codigo}/calidad")
    public ConsultaPublica consultar(@PathVariable String codigo) {
        return portalService.consultarPorCodigo(codigo);
    }

    /** RF-48: qué zonas produjeron leche apta/observada/con adulteración ese día. */
    @GetMapping("/calidad/zonas")
    public List<ZonaCalidadPublica> zonasDelDia(
            @RequestParam(required = false) LocalDate fecha) {
        return portalService.zonasDelDia(fecha != null ? fecha : LocalDate.now(PortalService.ZONA_HORARIA));
    }

    /**
     * RF-49 + aviso activo a los demás proveedores: qué proveedor(es) tuvieron una incidencia de
     * calidad confirmada ese día. La app del proveedor consulta este endpoint al abrir para mostrar
     * el aviso (opción elegida para el ítem 4 de PROXIMA_FASE_PROMPT.md: sin push, con banner al
     * abrir la app).
     */
    @GetMapping("/incidencias/hoy")
    public ResumenPublicoHoy incidenciasDeHoy(@RequestParam(required = false) LocalDate fecha) {
        return portalService.resumenDelDia(fecha != null ? fecha : LocalDate.now(PortalService.ZONA_HORARIA));
    }
}
