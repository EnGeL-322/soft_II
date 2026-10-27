package org.ramiro.huata.produccion;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "¿Cuanto puedo producir con lo que hay?": la pantalla de registrar produccion.
 *
 * <p>Acepta {@code produccion.read} o {@code quesos.read}, el mismo par que ya aceptan listar y crear
 * lotes ({@code QueseriaController}): Secretaria registra produccion con {@code quesos.write} pero no
 * tiene {@code recetas.read}, y sin esto no podria ver la receta que se va a descontar. No se toca la
 * matriz de roles.
 */
@RestController
@RequestMapping("/api/v1/produccion/capacidad")
@RequiredArgsConstructor
public class CapacidadProduccionController {

    private final RecetaService recetaService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_READ + "') or hasAuthority('" + Permiso.QUESOS_READ + "')")
    public CapacidadProduccionResponse capacidad(
            @RequestParam(required = false) Long productoId, @RequestParam(required = false) LocalDate fecha) {
        return recetaService.capacidad(productoId, fecha != null ? fecha : LocalDate.now());
    }
}
