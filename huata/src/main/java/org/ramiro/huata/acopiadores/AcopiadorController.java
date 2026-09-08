package org.ramiro.huata.acopiadores;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/acopiadores")
@RequiredArgsConstructor
public class AcopiadorController {

    private final AcopiadorService acopiadorService;

    @GetMapping
    public PageResponse<AcopiadorResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return acopiadorService
                .buscar(q, PageRequest.of(page, size, Sort.by("nombres")))
                .map(AcopiadorResponse::from);
    }

    @GetMapping("/{id}")
    public AcopiadorResponse obtener(@PathVariable Long id) {
        return AcopiadorResponse.from(acopiadorService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<AcopiadorResponse> crear(@Valid @RequestBody AcopiadorRequest request) {
        Acopiador creado = acopiadorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AcopiadorResponse.from(creado));
    }

    @PutMapping("/{id}")
    public AcopiadorResponse actualizar(@PathVariable Long id, @Valid @RequestBody AcopiadorRequest request) {
        return AcopiadorResponse.from(acopiadorService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        acopiadorService.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
