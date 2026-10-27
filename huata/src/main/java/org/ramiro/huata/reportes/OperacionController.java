package org.ramiro.huata.reportes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operacion")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.PANEL_READ + "')")
public class OperacionController {
    private final OperacionService operacion;
    /**
     * Desde V33 los litros de planta se CALCULAN de las rutas: {@code litrosPlanta} se acepta para no
     * romper clientes viejos, pero se ignora.
     */
    public record CierreRequest(@PositiveOrZero Double litrosPlanta, @Size(max = 1000) String observacion) {}

    @GetMapping("/{fecha}")
    public OperacionService.ResumenDia resumen(@PathVariable LocalDate fecha) { return operacion.resumen(fecha); }

    @PutMapping("/{fecha}/cierre")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_CERRAR_JORNADA + "')")
    public CierreAcopio cerrar(@PathVariable LocalDate fecha, @Valid @RequestBody CierreRequest datos) {
        return operacion.cerrar(fecha, datos.observacion());
    }

    /** Lo que el cierre va a comparar: para mostrarlo antes de cerrar. */
    @GetMapping("/{fecha}/totales")
    public OperacionService.TotalesDia totales(@PathVariable LocalDate fecha) {
        return operacion.totales(fecha);
    }

    @PostMapping("/{fecha}/reabrir")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_CERRAR_JORNADA + "')")
    public CierreAcopio reabrir(@PathVariable LocalDate fecha) { return operacion.reabrir(fecha); }
}

