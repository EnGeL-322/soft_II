package org.ramiro.huata.productores;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productores")
@RequiredArgsConstructor
public class ProductorController {

    private final ProductorService productorService;

    @GetMapping
    public PageResponse<ProductorResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoProductor estado,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productorService
                .buscar(q, estado, rutaId, PageRequest.of(page, size, Sort.by("nombres")))
                .map(ProductorResponse::from);
    }

    @GetMapping("/{id}")
    public ProductorResponse obtener(@PathVariable Long id) {
        return ProductorResponse.from(productorService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ProductorResponse> crear(@Valid @RequestBody ProductorRequest request) {
        Productor creado = productorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductorResponse.from(creado));
    }

    @PutMapping("/{id}")
    public ProductorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductorRequest request) {
        return ProductorResponse.from(productorService.actualizar(id, request));
    }

    @PatchMapping("/{id}/estado")
    public ProductorResponse cambiarEstado(@PathVariable Long id, @RequestBody ProductorEstadoRequest request) {
        return ProductorResponse.from(productorService.cambiarEstado(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        productorService.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
