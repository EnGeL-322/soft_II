package org.ramiro.huata.ubicaciones;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GPS real (Geolocation API del navegador del acopiador), no una simulación. */
@RestController
@RequestMapping("/api/v1/ubicaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
public class UbicacionGpsController {

    private final UbicacionGpsService ubicacionGpsService;
    private final UbicacionWebSocketHandler ubicacionWebSocketHandler;

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public ResponseEntity<UbicacionResponse> reportar(Authentication authentication, @Valid @RequestBody UbicacionRequest request) {
        UbicacionGps guardada = ubicacionGpsService.registrar(authentication.getName(), request);
        UbicacionResponse response = UbicacionResponse.from(guardada);
        ubicacionWebSocketHandler.publicar(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Puntos que el movil guardo sin cobertura y envia al recuperarla (RN-18).
     *
     * <p>Al WebSocket solo va el punto mas reciente del lote: publicar todos haria saltar el mapa
     * de administracion por posiciones de hace horas como si fueran de ahora.
     */
    @PostMapping("/lote")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public ResponseEntity<UbicacionLoteResponse> reportarLote(
            Authentication authentication, @Valid @RequestBody UbicacionLoteRequest request) {
        UbicacionLoteResponse resumen = ubicacionGpsService.registrarLote(authentication.getName(), request);
        ubicacionGpsService.ultimaDe(authentication.getName()).ifPresent(ubicacionWebSocketHandler::publicar);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumen);
    }

    @GetMapping("/ultimas")
    public List<UbicacionResponse> ultimas() {
        return ubicacionGpsService.ultimas();
    }

    @GetMapping("/acopiador/{id}")
    public List<UbicacionResponse> historial(
            @PathVariable Long id, @RequestParam(required = false) Instant desde, @RequestParam(required = false) Instant hasta) {
        return ubicacionGpsService.historial(id, desde, hasta);
    }
}
