package org.ramiro.huata.reportes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.PANEL_READ + "')")
public class ReporteController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReporteService reporteService;
    private final ReporteEventosService eventosService;
    private final ReporteQuesoService reporteQuesoService;
    private final ReporteQuesoExcelService reporteQuesoExcelService;

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
    @PreAuthorize("hasAuthority('" + Permiso.ZONAS_READ + "')")
    public List<ZonaResumen> zonas() {
        return reporteService.zonas();
    }

    @GetMapping("/serie-diaria")
    public List<PuntoSerieDiaria> serieDiaria(
            @RequestParam(required = false) Instant desde, @RequestParam(required = false) Instant hasta) {
        return reporteService.serieDiaria(desde, hasta);
    }

    @GetMapping("/trazabilidad")
    @PreAuthorize("hasAuthority('" + Permiso.TRAZABILIDAD_READ + "')")
    public List<TrazabilidadItem> trazabilidad(@RequestParam(required = false) String q) {
        return reporteService.trazabilidad(q);
    }

    /** Reporte semanal de queso (venta por dia, rendimiento por zona, ingresos/egresos). Sin fechas, usa el ciclo jueves-miercoles vigente. */
    @GetMapping("/quesos/semanal")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_READ + "')")
    public ReporteQuesoSemanalResponse quesosSemanal(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return reporteQuesoService.semanal(desde, hasta);
    }

    /** Mismo reporte que {@link #quesosSemanal}, en el formato Excel del cuaderno fisico (3 hojas). */
    @GetMapping("/quesos/semanal.xlsx")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_READ + "')")
    public ResponseEntity<byte[]> quesosSemanalExcel(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        ReporteQuesoSemanalResponse reporte = reporteQuesoService.semanal(desde, hasta);
        byte[] archivo = reporteQuesoExcelService.generar(reporte);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=Huata_Reporte_Queso_" + reporte.desde() + "_" + reporte.hasta() + ".xlsx")
                .contentType(XLSX)
                .body(archivo);
    }
}
