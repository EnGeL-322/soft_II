package org.ramiro.huata.rutas;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Puntos establecidos" (pedido del cliente): puntos de referencia que administracion define a mano
 * en el mapa. Mismos permisos que el resto de "Rutas y movilidad" -- {@code rutas.read} para
 * consultar (lo que ya tiene ACOPIADOR) y {@code rutas.write} para crear/editar/desactivar (solo
 * ADMINISTRADOR, ver MatrizPermisos): es exactamente el mismo reparto que ya usa la geometria de la
 * ruta y el poligono de zona.
 */
@RestController
@RequestMapping("/api/v1/puntos-establecidos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
public class PuntoEstablecidoController {

    private final PuntoEstablecidoService service;

    @GetMapping
    @PreAuthorize("hasAuthority('rutas.read') and (!#incluirInactivos or hasAuthority('rutas.write'))")
    public List<PuntoEstablecidoResponse> listar(@RequestParam(required = false) Long rutaId,
            @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return incluirInactivos ? service.listarParaGestion(rutaId) : service.listar(rutaId);
    }

    /** Lo que ve la app movil del acopiador: los puntos de su ruta del dia y los asignados a el directamente. */
    @GetMapping("/me")
    public List<PuntoEstablecidoResponse> deMiAcopiador(Authentication authentication) {
        return service.deMiAcopiador(authentication.getName());
    }

    @GetMapping("/me/snapshot")
    public SnapshotPuntosResponse snapshot(Authentication authentication) {
        return service.snapshotDeMiAcopiador(authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "') and (#request.proveedorId() == null or hasAuthority('" + Permiso.PROVEEDORES_WRITE + "'))")
    public ResponseEntity<PuntoEstablecidoResponse> crear(@Valid @RequestBody PuntoEstablecidoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PuntoEstablecidoResponse.from(service.crear(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "') and (#request.proveedorId() == null or hasAuthority('" + Permiso.PROVEEDORES_WRITE + "'))")
    public PuntoEstablecidoResponse actualizar(@PathVariable Long id, @Valid @RequestBody PuntoEstablecidoRequest request) {
        return PuntoEstablecidoResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public PuntoEstablecidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoPuntoRequest request) {
        return PuntoEstablecidoResponse.from(service.cambiarEstado(id, request.activo()));
    }

    @DeleteMapping("/{id}/definitivo")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
