package org.ramiro.huata.recojo;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Comparar recolectado vs recibido en planta, por ruta y fecha (2.2.4). */
@RestController
@RequestMapping("/api/v1/conciliaciones-ruta")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.ACOPIO_READ + "')")
public class ConciliacionRutaController {

    private final ConciliacionRutaService service;

    @GetMapping
    public List<ConciliacionRutaResponse> deFecha(@RequestParam LocalDate fecha) {
        return service.deFecha(fecha);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.PLANTA_RECEPCION + "')")
    public ConciliacionRutaResponse registrar(@Valid @RequestBody ConciliacionRutaRequest request) {
        return service.registrar(request);
    }
}
