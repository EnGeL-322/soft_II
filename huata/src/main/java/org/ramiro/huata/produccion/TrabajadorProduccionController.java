package org.ramiro.huata.produccion;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/produccion/trabajadores")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_READ + "')")
public class TrabajadorProduccionController {

    private final TrabajadorProduccionService service;

    @GetMapping
    public List<TrabajadorProduccionResponse> listar() {
        return service.listar().stream().map(TrabajadorProduccionResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ResponseEntity<TrabajadorProduccionResponse> crear(@Valid @RequestBody TrabajadorProduccionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TrabajadorProduccionResponse.from(service.crear(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        service.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
