package org.ramiro.huata.parametros;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reglas de la leche (V33): se leen con parametros.read y se cambian con parametros.write. */
@RestController
@RequestMapping("/api/v1/reglas-leche")
@RequiredArgsConstructor
public class ReglasLecheController {

    private final ReglasLecheService service;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_READ + "')")
    public ReglasLecheService.ReglasLeche vigentes() {
        return service.vigentes();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ReglasLecheService.ReglasLeche guardar(@Valid @RequestBody ReglasLecheService.ReglasLeche reglas) {
        return service.guardar(reglas);
    }
}
