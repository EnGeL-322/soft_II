package org.ramiro.huata.ubicaciones;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
public class UbicacionGpsController {

    private final UbicacionGpsService ubicacionGpsService;

    @PostMapping
    public ResponseEntity<UbicacionResponse> reportar(Authentication authentication, @Valid @RequestBody UbicacionRequest request) {
        UbicacionGps guardada = ubicacionGpsService.registrar(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UbicacionResponse.from(guardada));
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
