package org.ramiro.huata.produccion;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de productos terminados. Lo leen todas las pantallas que eligen un producto (recetas,
 * produccion, venta, precios, inventario); lo edita quien edita recetas (recetas.write), porque
 * crear un producto es el primer paso de definir que se fabrica — y el Productor de queso, que no
 * tiene ese permiso, no decide el catalogo.
 */
@RestController
@RequestMapping("/api/v1/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permiso.RECETAS_READ + "', '" + Permiso.PRODUCCION_READ + "', '"
            + Permiso.QUESOS_READ + "', '" + Permiso.INVENTARIO_READ + "', '" + Permiso.PARAMETROS_READ + "')")
    public List<ProductoResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return productoService.listar(incluirInactivos).stream()
                .map(p -> ProductoResponse.from(p, productoService.lotesRegistrados(p.getId())))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductoResponse.from(productoService.crear(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return ProductoResponse.from(productoService.actualizar(id, request));
    }

    @PatchMapping("/{id}/baja")
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ProductoResponse darDeBaja(@PathVariable Long id) {
        return ProductoResponse.from(productoService.cambiarEstado(id, false));
    }

    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ProductoResponse reactivar(@PathVariable Long id) {
        return ProductoResponse.from(productoService.cambiarEstado(id, true));
    }

    /** Dias que dura el producto. Se configura en Configuracion (parametros.write) o en Recetas. */
    @PatchMapping("/{id}/vencimiento")
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "') or hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ProductoResponse cambiarVencimiento(@PathVariable Long id, @Valid @RequestBody VidaUtilRequest request) {
        return ProductoResponse.from(productoService.cambiarVidaUtil(id, request.vidaUtilDias()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
