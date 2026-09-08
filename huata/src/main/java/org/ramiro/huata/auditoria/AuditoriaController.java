package org.ramiro.huata.auditoria;

import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRADOR')")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    @GetMapping
    public PageResponse<RegistroAuditoria> listar(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return auditoriaService.listar(PageRequest.of(page, size));
    }
}
