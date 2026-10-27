package org.ramiro.huata.compras;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/compras/proveedores")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.COMPRAS_READ + "')")
public class ProveedorCompraController {

    private final ProveedorCompraService service;

    @GetMapping
    public List<ProveedorCompraResponse> listar() {
        return service.listar().stream().map(ProveedorCompraResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public ResponseEntity<ProveedorCompraResponse> crear(@Valid @RequestBody ProveedorCompraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProveedorCompraResponse.from(service.crear(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public ProveedorCompraResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorCompraRequest request) {
        return ProveedorCompraResponse.from(service.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.COMPRAS_WRITE + "')")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        service.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
