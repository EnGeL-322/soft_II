package org.ramiro.huata.calidad;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calidad")
@RequiredArgsConstructor
public class CalidadController {

    private final CalidadService calidadService;

    // ---- Parametros ----

    @GetMapping("/parametros")
    public List<ParametroCalidadResponse> parametros() {
        return calidadService.listarParametros().stream().map(ParametroCalidadResponse::from).toList();
    }

    @PostMapping("/parametros")
    public ResponseEntity<ParametroCalidadResponse> crearParametro(@RequestBody ParametroCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ParametroCalidadResponse.from(calidadService.guardarParametro(null, request)));
    }

    @PutMapping("/parametros/{id}")
    public ParametroCalidadResponse actualizarParametro(@PathVariable Long id, @RequestBody ParametroCalidadRequest request) {
        return ParametroCalidadResponse.from(calidadService.guardarParametro(id, request));
    }

    // ---- Controles ----

    @PostMapping("/controles")
    public ResponseEntity<ControlCalidadResponse> registrarControl(@Valid @RequestBody ControlCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ControlCalidadResponse.from(calidadService.registrarControl(request)));
    }

    @PutMapping("/controles/{id}")
    public ControlCalidadResponse actualizarControl(@PathVariable Long id, @Valid @RequestBody ControlCalidadRequest request) {
        return ControlCalidadResponse.from(calidadService.actualizarControl(id, request));
    }

    @GetMapping("/historial/{productorId}")
    public List<ControlCalidadResponse> historial(@PathVariable Long productorId) {
        return calidadService.historialPorProductor(productorId).stream().map(ControlCalidadResponse::from).toList();
    }

    // ---- Incidencias / seguimiento ----

    @GetMapping("/incidencias")
    public List<IncidenciaCalidadResponse> incidencias() {
        return calidadService.listarIncidencias().stream().map(IncidenciaCalidadResponse::from).toList();
    }

    @PostMapping("/incidencias")
    public ResponseEntity<IncidenciaCalidadResponse> registrarIncidencia(@Valid @RequestBody IncidenciaCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidenciaCalidadResponse.from(calidadService.registrarIncidencia(request)));
    }

    @PatchMapping("/incidencias/{id}/estado")
    public IncidenciaCalidadResponse cambiarEstadoIncidencia(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoIncidenciaRequest request) {
        return IncidenciaCalidadResponse.from(calidadService.cambiarEstadoIncidencia(id, request));
    }

    @GetMapping("/seguimiento/{productorId}")
    public List<IncidenciaCalidadResponse> seguimiento(@PathVariable Long productorId) {
        return calidadService.seguimientoPorProductor(productorId).stream().map(IncidenciaCalidadResponse::from).toList();
    }

    // ---- Evidencia ----

    @PostMapping("/evidencias")
    public ResponseEntity<EvidenciaCalidadResponse> registrarEvidencia(@Valid @RequestBody EvidenciaCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EvidenciaCalidadResponse.from(calidadService.registrarEvidencia(request)));
    }

    @GetMapping("/evidencias/control/{controlCalidadId}")
    public List<EvidenciaCalidadResponse> evidenciaDeControl(@PathVariable Long controlCalidadId) {
        return calidadService.evidenciaDeControl(controlCalidadId).stream().map(EvidenciaCalidadResponse::from).toList();
    }

    @GetMapping("/evidencias/incidencia/{incidenciaId}")
    public List<EvidenciaCalidadResponse> evidenciaDeIncidencia(@PathVariable Long incidenciaId) {
        return calidadService.evidenciaDeIncidencia(incidenciaId).stream().map(EvidenciaCalidadResponse::from).toList();
    }
}
