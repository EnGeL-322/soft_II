package org.ramiro.huata.quesos;

import jakarta.validation.Valid;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quesos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.QUESOS_READ + "')")
public class QuesoController {

    private final QuesoService quesoService;

    @GetMapping
    public List<QuesoResponse> listar(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return quesoService.listar(desde, hasta).stream().map(QuesoResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_WRITE + "')")
    public ResponseEntity<QuesoResponse> crear(@Valid @RequestBody QuesoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(QuesoResponse.from(quesoService.crear(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_WRITE + "')")
    public QuesoResponse actualizar(@PathVariable Long id, @Valid @RequestBody QuesoRequest request) {
        return QuesoResponse.from(quesoService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_WRITE + "')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        quesoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
