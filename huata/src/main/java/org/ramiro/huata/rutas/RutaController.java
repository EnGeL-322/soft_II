package org.ramiro.huata.rutas;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RutaController {

    private final RutaService rutaService;

    // ---- Rutas ----

    @GetMapping("/rutas")
    public PageResponse<RutaResponse> listarRutas(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return rutaService.buscarRutas(q, PageRequest.of(page, size, Sort.by("nombre"))).map(RutaResponse::from);
    }

    @PostMapping("/rutas")
    public ResponseEntity<RutaResponse> crearRuta(@Valid @RequestBody RutaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RutaResponse.from(rutaService.crearRuta(request)));
    }

    @PutMapping("/rutas/{id}")
    public RutaResponse actualizarRuta(@PathVariable Long id, @Valid @RequestBody RutaRequest request) {
        return RutaResponse.from(rutaService.actualizarRuta(id, request));
    }

    @DeleteMapping("/rutas/{id}")
    public ResponseEntity<Void> inactivarRuta(@PathVariable Long id) {
        rutaService.inactivarRuta(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Centros de acopio ----

    @GetMapping("/centros-acopio")
    public PageResponse<CentroAcopioResponse> listarCentros(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return rutaService
                .buscarCentros(q, PageRequest.of(page, size, Sort.by("nombre")))
                .map(CentroAcopioResponse::from);
    }

    @PostMapping("/centros-acopio")
    public ResponseEntity<CentroAcopioResponse> crearCentro(@Valid @RequestBody CentroAcopioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CentroAcopioResponse.from(rutaService.crearCentro(request)));
    }

    @PutMapping("/centros-acopio/{id}")
    public CentroAcopioResponse actualizarCentro(@PathVariable Long id, @Valid @RequestBody CentroAcopioRequest request) {
        return CentroAcopioResponse.from(rutaService.actualizarCentro(id, request));
    }

    @DeleteMapping("/centros-acopio/{id}")
    public ResponseEntity<Void> inactivarCentro(@PathVariable Long id) {
        rutaService.inactivarCentro(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Asignaciones ----

    @GetMapping("/asignaciones-ruta")
    public List<AsignacionRutaResponse> asignaciones(
            @RequestParam LocalDate fecha, @RequestParam(required = false) Long acopiadorId) {
        return rutaService.asignacionesPorFecha(fecha, acopiadorId).stream()
                .map(AsignacionRutaResponse::from)
                .toList();
    }

    @PostMapping("/asignaciones-ruta")
    public ResponseEntity<AsignacionRutaResponse> asignar(@Valid @RequestBody AsignacionRutaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AsignacionRutaResponse.from(rutaService.asignar(request)));
    }

    @PatchMapping("/asignaciones-ruta/{id}")
    public AsignacionRutaResponse actualizarAsignacion(@PathVariable Long id, @RequestBody AsignacionActualizarRequest request) {
        return AsignacionRutaResponse.from(rutaService.actualizarAsignacion(id, request));
    }

    // ---- Plan operativo ----

    @GetMapping("/rutas/plan-operativo")
    public List<PlanRutaDia> planOperativo(@RequestParam LocalDate fecha) {
        return rutaService.planOperativo(fecha);
    }
}
