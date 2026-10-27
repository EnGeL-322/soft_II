package org.ramiro.huata.recojo;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recojos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.ACOPIO_READ + "')")
public class RecojoController {

    private final RecojoService recojoService;

    /** "Mis entregas" (7.1) para la app del proveedor. Solo exige estar autenticado, no acopio.read. */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<RecojoResponse> misRecojos(
            Authentication authentication,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return recojoService.misRecojos(
                authentication.getName(), desde, hasta, Paginas.de(page, size, Sort.by(Sort.Direction.DESC, "fechaHora")));
    }

    /** Resumen diario o semanal exclusivo del acopiador autenticado en la app movil. */
    @GetMapping("/me/acopiador/reporte")
    @PreAuthorize("isAuthenticated()")
    public ReporteAcopiadorResponse miReporte(
            Authentication authentication,
            @RequestParam(defaultValue = "DIA") PeriodoReporteAcopiador periodo,
            @RequestParam(required = false) LocalDate fecha) {
        return recojoService.reportePropioAcopiador(authentication.getName(), periodo, fecha);
    }

    /** Reporte administrativo por rango; admite dia, semana, mes o un rango personalizado. */
    @GetMapping("/reportes/acopiadores")
    @PreAuthorize("hasAuthority('" + Permiso.REPORTES_READ + "')")
    public List<ReporteAcopiadorResponse> reporteAcopiadores(
            @RequestParam LocalDate desde,
            @RequestParam LocalDate hasta,
            @RequestParam(required = false) Long acopiadorId) {
        return recojoService.reporteAcopiadores(desde, hasta, acopiadorId);
    }

    @GetMapping
    public PageResponse<RecojoResponse> listar(
            @RequestParam(required = false) Long productorId,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) Long acopiadorId,
            @RequestParam(required = false) EstadoRecojo estado,
            @RequestParam(required = false) Instant desde,
            @RequestParam(required = false) Instant hasta,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) FuenteRecojo fuente,
            @RequestParam(required = false) String resultado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return recojoService.buscar(
                productorId,
                rutaId,
                acopiadorId,
                estado,
                desde,
                hasta,
                q,
                fuente,
                resultado,
                Paginas.de(page, size, Sort.by(Sort.Direction.DESC, "fechaHora")));
    }

    @GetMapping("/{id}")
    public RecojoResponse obtener(@PathVariable Long id) {
        return recojoService.obtenerRespuesta(id);
    }

    /**
     * El recojo en ruta lo registra el acopiador (acopio.write). La entrega directa la recibe planta
     * (planta.recepcion): el proveedor trae la leche y planta la analiza y la registra (2026-10-14).
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permiso.ACOPIO_WRITE + "', '" + Permiso.PLANTA_RECEPCION + "')")
    public ResponseEntity<RecojoResponse> registrar(
            @Valid @RequestBody RecojoRequest request, Authentication authentication) {
        boolean esAcopiador = authentication.getAuthorities().stream()
                .anyMatch(a -> Permiso.ACOPIO_WRITE.equals(a.getAuthority()));
        if (request.fuente() != FuenteRecojo.PLANTA && !esAcopiador) {
            throw new AccessDeniedException("Desde planta solo se registran entregas directas; el recojo en ruta lo registra el acopiador.");
        }
        Recojo creado = recojoService.registrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(recojoService.obtenerRespuesta(creado.getId()));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public RecojoResponse cancelar(@PathVariable Long id, @RequestBody RecojoCancelRequest request) {
        recojoService.cancelar(id, request);
        return recojoService.obtenerRespuesta(id);
    }

    /**
     * Corrige un recojo ya registrado (pedido del cliente, 2026-09-24): un proveedor entrega leche
     * una sola vez al dia, asi que esta es la via para arreglar un litros mal tecleado sin chocar
     * contra esa regla creando un segundo recojo. Exige motivo y el recojo queda marcado
     * {@code observado} para revision.
     */
    @PatchMapping("/{id}/corregir")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public RecojoResponse corregir(@PathVariable Long id, @Valid @RequestBody RecojoCorreccionRequest request) {
        recojoService.corregir(id, request);
        return recojoService.obtenerRespuesta(id);
    }
}
