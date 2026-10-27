package org.ramiro.huata.ivr;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del IVR telefonico (ivr/README.md, seccion 5). Solo los usa el IVR con su clave
 * ({@link IvrApiKeyFilter}): ni siquiera un administrador con sesion web puede llamarlos, porque
 * consultan los datos de cualquier proveedor a partir de un numero de telefono.
 */
@RestController
@RequestMapping("/api/v1/ivr")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + IvrApiKeyFilter.ROL + "')")
public class IvrController {

    private final IvrService ivr;

    @PostMapping("/identificar")
    public IvrDtos.IdentificarResponse identificar(@Valid @RequestBody IvrDtos.IdentificarRequest request) {
        return ivr.identificar(request.telefono());
    }

    @GetMapping("/proveedores/{id}/litros-hoy")
    public IvrDtos.LitrosHoyResponse litrosHoy(@PathVariable Long id) {
        return ivr.litrosHoy(id);
    }

    @GetMapping("/proveedores/{id}/cobro")
    public IvrDtos.CobroResponse cobro(@PathVariable Long id) {
        return ivr.cobro(id);
    }

    @GetMapping("/proveedores/{id}/comunicados")
    public IvrDtos.ComunicadosResponse comunicados(@PathVariable Long id, @RequestParam(defaultValue = "7") int dias) {
        return ivr.comunicados(id, dias);
    }

    @PostMapping("/proveedores/{id}/comunicados/leidos")
    public ResponseEntity<Void> marcarLeidos(@PathVariable Long id, @Valid @RequestBody IvrDtos.LeidosRequest request) {
        ivr.marcarLeidos(id, request.ids());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/zonas")
    public IvrDtos.ZonasResponse zonas() {
        return ivr.zonas();
    }

    /** 201 la primera vez; 200 si el mismo pedido ya existia (reintento tras un corte). */
    @PostMapping("/proveedores/{id}/solicitud-zona")
    public ResponseEntity<IvrDtos.SolicitudZonaResponse> solicitarZona(
            @PathVariable Long id, @Valid @RequestBody IvrDtos.SolicitudZonaRequest request) {
        IvrService.Resultado<IvrDtos.SolicitudZonaResponse> resultado = ivr.solicitarZona(id, request.zona());
        return ResponseEntity.status(resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK).body(resultado.cuerpo());
    }

    @PostMapping("/proveedores/{id}/pedido-llamada")
    public ResponseEntity<IvrDtos.TextoResponse> pedidoLlamada(
            @PathVariable Long id, @Valid @RequestBody(required = false) IvrDtos.PedidoLlamadaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ivr.pedidoLlamada(id));
    }

    @GetMapping("/salud")
    public IvrDtos.SaludResponse salud() {
        return ivr.salud();
    }

    @ExceptionHandler(ConflictoIvrException.class)
    ResponseEntity<IvrDtos.ConflictoResponse> conflicto(ConflictoIvrException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new IvrDtos.ConflictoResponse(e.codigo(), e.getMessage()));
    }
}
