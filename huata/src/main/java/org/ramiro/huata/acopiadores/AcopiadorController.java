package org.ramiro.huata.acopiadores;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Sort;
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

@RestController
@RequestMapping("/api/v1/acopiadores")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.ACOPIADORES_READ + "')")
public class AcopiadorController {

    private final AcopiadorService acopiadorService;

    /** El acopiador logueado en la app movil, para saber quien es sin exponer el padron completo. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public AcopiadorResponse me(Authentication authentication) {
        return AcopiadorResponse.from(acopiadorService.obtenerPorUsuario(authentication.getName()));
    }

    @GetMapping
    public PageResponse<AcopiadorResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) String zona,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return acopiadorService
                .buscar(new AcopiadorService.Filtro(q, rutaId, zona), Paginas.de(page, size, Sort.by("nombres")))
                .map(AcopiadorResponse::from);
    }

    @GetMapping("/{id}")
    public AcopiadorResponse obtener(@PathVariable Long id) {
        return AcopiadorResponse.from(acopiadorService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIADORES_WRITE + "')")
    public ResponseEntity<AcopiadorResponse> crear(@Valid @RequestBody AcopiadorRequest request) {
        Acopiador creado = acopiadorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AcopiadorResponse.from(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIADORES_WRITE + "')")
    public AcopiadorResponse actualizar(@PathVariable Long id, @Valid @RequestBody AcopiadorRequest request) {
        return AcopiadorResponse.from(acopiadorService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIADORES_WRITE + "')")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        acopiadorService.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
