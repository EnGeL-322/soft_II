package org.ramiro.huata.liquidaciones;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LiquidacionController {

    private final LiquidacionService liquidacionService;

    @GetMapping("/liquidaciones")
    public PageResponse<LiquidacionResponse> listar(
            @RequestParam(required = false) Long productorId,
            @RequestParam(required = false) EstadoLiquidacion estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return liquidacionService
                .buscar(productorId, estado, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "periodoFin")))
                .map(l -> LiquidacionResponse.from(l, null));
    }

    @GetMapping("/liquidaciones/{id}")
    public LiquidacionResponse obtener(@PathVariable Long id) {
        Liquidacion liquidacion = liquidacionService.obtener(id);
        return LiquidacionResponse.from(liquidacion, liquidacionService.detalle(id));
    }

    @PostMapping("/liquidaciones/calcular")
    public LiquidacionResponse calcular(@Valid @RequestBody LiquidacionCalcularRequest request) {
        Liquidacion liquidacion = liquidacionService.calcular(request);
        return LiquidacionResponse.from(liquidacion, liquidacionService.detalle(liquidacion.getId()));
    }

    @PatchMapping("/liquidaciones/{id}/estado")
    public LiquidacionResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoLiquidacionRequest request) {
        Liquidacion liquidacion = liquidacionService.cambiarEstado(id, request);
        return LiquidacionResponse.from(liquidacion, liquidacionService.detalle(id));
    }

    // ---- Tarifas de leche (configuracion) ----

    @GetMapping("/tarifas-leche")
    public List<TarifaLecheResponse> tarifas() {
        return liquidacionService.listarTarifas().stream().map(TarifaLecheResponse::from).toList();
    }

    @PostMapping("/tarifas-leche")
    public ResponseEntity<TarifaLecheResponse> crearTarifa(@Valid @RequestBody TarifaLecheRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(TarifaLecheResponse.from(liquidacionService.guardarTarifa(request)));
    }
}
