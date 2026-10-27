package org.ramiro.huata.produccion;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receta/BOM de queso. Separada de {@code produccion.write} a proposito: Productor de queso puede
 * crear y cerrar lotes (produccion.write) sin poder tocar la receta maestra ni sus costos
 * (recetas.write) — es la restriccion del CLAUDE.md aplicada como permiso real, no solo un boton
 * oculto.
 */
@RestController
@RequestMapping("/api/v1/produccion/recetas")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RECETAS_READ + "')")
public class RecetaController {

    private final RecetaService recetaService;

    @GetMapping
    public List<RecetaResponse> listar() {
        return recetaService.listar();
    }

    /** La version vigente de la receta de un producto, para prellenar la pantalla de crear lote. */
    @GetMapping("/vigente")
    public RecetaResponse vigente(
            @RequestParam Long productoId, @RequestParam(required = false) LocalDate fecha) {
        return recetaService.vigente(productoId, fecha != null ? fecha : LocalDate.now());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.RECETAS_WRITE + "')")
    public ResponseEntity<RecetaResponse> crear(@Valid @RequestBody RecetaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RecetaResponse.from(recetaService.crear(request)));
    }

    /**
     * Simulacion: cuanto haria falta de cada materia prima para producir N unidades. La puede pedir
     * cualquiera con produccion.read (no exige recetas.read): es la pantalla de "crear lote", no la
     * de mantenimiento de recetas.
     */
    @PostMapping("/{id}/simular")
    @PreAuthorize("hasAuthority('" + Permiso.PRODUCCION_READ + "')")
    public SimulacionResponse simular(@PathVariable Long id, @Valid @RequestBody SimulacionRequest request) {
        return recetaService.simular(id, request.cantidadObjetivo());
    }
}
