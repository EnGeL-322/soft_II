package org.ramiro.huata.reportes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operacion")
@RequiredArgsConstructor
public class OperacionController {
    private final OperacionService operacion;
    public record CierreRequest(@NotNull @PositiveOrZero Double litrosPlanta, @Size(max = 1000) String observacion) {}

    @GetMapping("/{fecha}")
    public OperacionService.ResumenDia resumen(@PathVariable LocalDate fecha) { return operacion.resumen(fecha); }

    @PutMapping("/{fecha}/cierre")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CierreAcopio cerrar(@PathVariable LocalDate fecha, @Valid @RequestBody CierreRequest datos) {
        return operacion.cerrar(fecha, datos.litrosPlanta(), datos.observacion());
    }

    @PostMapping("/{fecha}/reabrir")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public CierreAcopio reabrir(@PathVariable LocalDate fecha) { return operacion.reabrir(fecha); }
}

