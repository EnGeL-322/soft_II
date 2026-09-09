package org.ramiro.huata.reportes;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;
    private final ReporteEventosService eventosService;

    @GetMapping("/eventos")
    public List<ReporteEventosService.Evento> eventos(
            @RequestParam(required = false) java.time.LocalDate desde,
            @RequestParam(required = false) java.time.LocalDate hasta) {
        return eventosService.listar(desde, hasta);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(
            @RequestParam(required = false) Instant desde, @RequestParam(required = false) Instant hasta) {
        return reporteService.dashboard(desde, hasta);
    }

    @GetMapping("/zonas")
    public List<ZonaResumen> zonas() {
        return reporteService.zonas();
    }

    @GetMapping("/serie-diaria")
    public List<PuntoSerieDiaria> serieDiaria(
            @RequestParam(required = false) Instant desde, @RequestParam(required = false) Instant hasta) {
        return reporteService.serieDiaria(desde, hasta);
    }

    @GetMapping("/trazabilidad")
    public List<TrazabilidadItem> trazabilidad(@RequestParam(required = false) String q) {
        return reporteService.trazabilidad(q);
    }
}
