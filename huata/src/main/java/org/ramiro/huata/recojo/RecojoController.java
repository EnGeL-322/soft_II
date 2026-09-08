package org.ramiro.huata.recojo;

import jakarta.validation.Valid;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recojos")
@RequiredArgsConstructor
public class RecojoController {

    private final RecojoService recojoService;

    @GetMapping
    public PageResponse<RecojoResponse> listar(
            @RequestParam(required = false) Long productorId,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) EstadoRecojo estado,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recojoService
                .buscar(productorId, rutaId, estado, desde, hasta, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fechaHora")))
                .map(RecojoResponse::from);
    }

    @GetMapping("/{id}")
    public RecojoResponse obtener(@PathVariable Long id) {
        return RecojoResponse.from(recojoService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<RecojoResponse> registrar(@Valid @RequestBody RecojoRequest request) {
        Recojo creado = recojoService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(RecojoResponse.from(creado));
    }

    @PatchMapping("/{id}/cancelar")
    public RecojoResponse cancelar(@PathVariable Long id, @RequestBody RecojoCancelRequest request) {
        return RecojoResponse.from(recojoService.cancelar(id, request));
    }
}
