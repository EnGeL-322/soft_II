package org.ramiro.huata.auditoria;

import org.ramiro.huata.common.Paginas;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.AUDITORIA_READ + "')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public PageResponse<RegistroAuditoria> listar(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return auditoriaService.listar(Paginas.de(page, size));
    }
}
