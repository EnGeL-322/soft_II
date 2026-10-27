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

/**
 * Reglas de acopio (V57). Leerlas solo exige sesion: la app del acopiador las necesita para saber si
 * debe exigir la geocerca y la distancia, y el acopiador no tiene parametros.read. Cambiarlas exige
 * parametros.write.
 */
@RestController
@RequestMapping("/api/v1/reglas-acopio")
@RequiredArgsConstructor
public class ReglasAcopioController {

    private final ReglasAcopioService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ReglasAcopioService.ReglasAcopio vigentes() {
        return service.vigentes();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ReglasAcopioService.ReglasAcopio guardar(@Valid @RequestBody ReglasAcopioService.ReglasAcopio reglas) {
        return service.guardar(reglas);
    }
}
