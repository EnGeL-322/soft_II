package org.ramiro.huata.usuarios;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles como plantillas de permisos, y el catalogo de vistas con el que se arman. */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.USUARIOS_READ + "')")
public class RolController {

    private final RolService rolService;

    @GetMapping
    public List<RolResponse> listar() {
        return rolService.listar();
    }

    /** Modulos -> vistas -> acciones: lo que la pantalla de roles pinta como matriz. */
    @GetMapping("/catalogo-vistas")
    public List<ModuloAccesoResponse> catalogo() {
        return rolService.catalogo();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public ResponseEntity<RolResponse> crear(@Valid @RequestBody RolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rolService.crear(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public RolResponse actualizar(@PathVariable Long id, @Valid @RequestBody RolRequest request) {
        return rolService.actualizar(id, request);
    }

    @PostMapping("/{id}/restaurar")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public RolResponse restaurar(@PathVariable Long id) {
        return rolService.restaurarEstandar(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        rolService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
