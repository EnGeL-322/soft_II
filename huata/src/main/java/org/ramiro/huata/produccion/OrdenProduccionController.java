package org.ramiro.huata.produccion;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ordenes de produccion (V61). Mismos permisos que registrar y consultar lotes
 * ({@code QueseriaController}): quien produce las planifica.
 */
@RestController
@RequestMapping("/api/v1/produccion")
@RequiredArgsConstructor
public class OrdenProduccionController {

    private final OrdenProduccionService ordenService;

    @GetMapping("/ordenes")
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_READ + "') or hasAuthority('" + Permiso.QUESOS_READ + "')")
    public List<OrdenProduccionResponse> listar() {
        return ordenService.listar();
    }

    @PostMapping("/ordenes")
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_WRITE + "') or hasAuthority('" + Permiso.QUESOS_WRITE + "')")
    public ResponseEntity<OrdenProduccionResponse> crear(@Valid @RequestBody OrdenProduccionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ordenService.crear(req));
    }

    @PostMapping("/ordenes/{id}/anular")
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_WRITE + "') or hasAuthority('" + Permiso.QUESOS_WRITE + "')")
    public OrdenProduccionResponse anular(@PathVariable Long id, @Valid @RequestBody AnularOrdenRequest req) {
        return ordenService.anular(id, req.motivo());
    }

    /** Lo que hay que comprar para las ordenes pendientes. Tambien lo consulta Compras. */
    @GetMapping("/necesidades")
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_READ + "') or hasAuthority('" + Permiso.QUESOS_READ
            + "') or hasAuthority('" + Permiso.COMPRAS_READ + "')")
    public List<NecesidadResponse> necesidades() {
        return ordenService.necesidades();
    }
}
