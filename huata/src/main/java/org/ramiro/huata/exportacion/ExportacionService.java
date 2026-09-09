package org.ramiro.huata.exportacion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.auditoria.RegistroAuditoria;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.liquidaciones.Liquidacion;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.liquidaciones.TarifaLeche;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.parametros.ParametroNegocio;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.quesos.Queso;
import org.ramiro.huata.quesos.QuesoRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.reportes.ReporteService;
import org.ramiro.huata.reportes.ZonaResumen;
import org.ramiro.huata.rutas.PlanRutaDia;
import org.ramiro.huata.rutas.RutaService;
import org.springframework.stereotype.Service;

/**
 * "Datos y respaldo": genera Excel reales (Apache POI) para cada hoja del prototipo
 * (Proveedores, Recepciones, HistorialCalidad, Pagos, ResumenZonas, Quesos, Configuracion,
 * Auditoria), en vez de exportarlos desde arrays en memoria del navegador.
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    public static final List<String> HOJAS_DISPONIBLES = List.of(
            "Proveedores", "Recepciones", "HistorialCalidad", "Pagos", "ResumenZonas", "Rutas", "Quesos", "Configuracion", "Auditoria");

    private final ProductorRepository productorRepository;
    private final RecojoRepository recojoRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final LiquidacionRepository liquidacionRepository;
    private final QuesoRepository quesoRepository;
    private final ParametroNegocioRepository parametroNegocioRepository;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final ReporteService reporteService;
    private final AuditoriaService auditoriaService;
    private final RutaService rutaService;

    public byte[] exportar(String hoja) {
        var filas = filas(hoja);
        byte[] archivo = generarXlsx(hoja, filas);
        auditoriaService.registrar("Exportación", "Excel", "Hoja " + hoja);
        return archivo;
    }

    private List<LinkedHashMap<String, Object>> filas(String hoja) {
        return
                switch (hoja) {
                    case "Proveedores" -> proveedores();
                    case "Recepciones" -> recepciones();
                    case "HistorialCalidad" -> historialCalidad();
                    case "Pagos" -> pagos();
                    case "ResumenZonas" -> resumenZonas();
                    case "Rutas" -> rutas();
                    case "Quesos" -> quesos();
                    case "Configuracion" -> configuracion();
                    case "Auditoria" -> auditoria();
                    default -> throw new BusinessRuleException("Hoja desconocida: " + hoja);
                };
    }

    private byte[] generarXlsx(String nombreHoja, List<LinkedHashMap<String, Object>> filas) {
        try (Workbook workbook = new XSSFWorkbook()) {
            escribirHoja(workbook, nombreHoja, filas);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel", e);
        }
    }

    private void escribirHoja(Workbook workbook, String nombreHoja, List<LinkedHashMap<String, Object>> filas) {
            Sheet sheet = workbook.createSheet(nombreHoja);
            if (!filas.isEmpty()) {
                List<String> columnas = new ArrayList<>(filas.get(0).keySet());
                Row encabezado = sheet.createRow(0);
                for (int i = 0; i < columnas.size(); i++) {
                    encabezado.createCell(i).setCellValue(columnas.get(i));
                }
                int filaIdx = 1;
                for (Map<String, Object> fila : filas) {
                    Row row = sheet.createRow(filaIdx++);
                    for (int i = 0; i < columnas.size(); i++) {
                        Object valor = fila.get(columnas.get(i));
                        Cell cell = row.createCell(i);
                        if (valor == null) {
                            cell.setBlank();
                        } else if (valor instanceof Number numero) {
                            cell.setCellValue(numero.doubleValue());
                        } else {
                            cell.setCellValue(String.valueOf(valor));
                        }
                    }
                }
            }
    }

    @org.springframework.transaction.annotation.Transactional
    public byte[] exportarLibro() {
        try (Workbook workbook = new XSSFWorkbook()) {
            for (var hoja : HOJAS_DISPONIBLES) escribirHoja(workbook, hoja, filas(hoja));
            var salida = new ByteArrayOutputStream();
            workbook.write(salida);
            auditoriaService.registrar("Exportación", "Excel", "Libro completo");
            return salida.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException("No se pudo generar el libro", e); }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, List<LinkedHashMap<String, Object>>> exportarDatos() {
        var datos = new LinkedHashMap<String, List<LinkedHashMap<String, Object>>>();
        HOJAS_DISPONIBLES.forEach(hoja -> datos.put(hoja, filas(hoja)));
        return datos;
    }

    private List<LinkedHashMap<String, Object>> proveedores() {
        return productorRepository.findAll().stream().map(this::filaProveedor).toList();
    }

    private LinkedHashMap<String, Object> filaProveedor(Productor p) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("Codigo", p.getCodigo());
        m.put("Nombres", p.getNombres());
        m.put("Apellidos", p.getApellidos());
        m.put("Ruta", p.getRuta() != null ? p.getRuta().getNombre() : "");
        m.put("Zona", p.getRuta() != null ? p.getRuta().getZona() : "");
        m.put("Estado", p.getEstado().name());
        return m;
    }

    private List<LinkedHashMap<String, Object>> recepciones() {
        return recojoRepository.findAll().stream()
                .map(r -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("Fecha", String.valueOf(r.getFechaHora()));
                    m.put("Codigo_Proveedor", r.getProductor().getCodigo());
                    m.put("Ruta", r.getRuta() != null ? r.getRuta().getNombre() : "");
                    m.put("Litros", r.getLitros());
                    m.put("Temperatura", r.getTemperatura());
                    m.put("Estado", r.getEstado().name());
                    return m;
                })
                .toList();
    }

    private List<LinkedHashMap<String, Object>> historialCalidad() {
        return controlCalidadRepository.findAll().stream().map(this::filaCalidad).toList();
    }

    private LinkedHashMap<String, Object> filaCalidad(ControlCalidad c) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("Fecha", String.valueOf(c.getCreatedAt()));
        m.put("Codigo_Proveedor", c.getRecojo().getProductor().getCodigo());
        m.put("Resultado", c.getResultado().name());
        m.put("Densidad", c.getDensidad());
        m.put("Punto_Congelacion", c.getPuntoCongelacion());
        m.put("Acidez", c.getAcidez());
        m.put("Revision_Visual", c.getRevisionVisual());
        m.put("Responsable", c.getResponsable());
        return m;
    }

    private List<LinkedHashMap<String, Object>> pagos() {
        return liquidacionRepository.findAll().stream().map(this::filaLiquidacion).toList();
    }

    private LinkedHashMap<String, Object> filaLiquidacion(Liquidacion l) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("Codigo_Proveedor", l.getProductor().getCodigo());
        m.put("Periodo_Inicio", String.valueOf(l.getPeriodoInicio()));
        m.put("Periodo_Fin", String.valueOf(l.getPeriodoFin()));
        m.put("Litros", l.getLitrosTotales());
        m.put("Monto", l.getMontoTotal());
        m.put("Estado", l.getEstado().name());
        return m;
    }

    private List<LinkedHashMap<String, Object>> resumenZonas() {
        return reporteService.zonas().stream()
                .map(z -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("Zona", z.zona());
                    m.put("Periodo_Anterior_L", z.litrosPeriodoAnterior());
                    m.put("Periodo_Actual_L", z.litrosPeriodoActual());
                    m.put("Variacion_Pct", z.variacionPorciento());
                    m.put("Participacion_Pct", z.participacionPorciento());
                    return m;
                })
                .toList();
    }

    private List<LinkedHashMap<String, Object>> rutas() {
        return rutaService.planOperativo(java.time.LocalDate.now()).stream()
                .map(p -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("Ruta", p.rutaNombre());
                    m.put("Zona", p.zona());
                    m.put("Responsable", p.acopiadorNombre());
                    m.put("Movilidad", p.movilidad());
                    m.put("Estado", p.estado().name());
                    m.put("Litros", p.litros());
                    m.put("Recepciones", p.recepciones());
                    m.put("Ultimo_Registro", p.ultimoRegistro() != null ? String.valueOf(p.ultimoRegistro()) : "");
                    m.put("Observacion", p.observacion());
                    return m;
                })
                .toList();
    }

    private List<LinkedHashMap<String, Object>> quesos() {
        return quesoRepository.findAll().stream()
                .filter(Queso::isActivo)
                .map(q -> {
                    LinkedHashMap<String, Object> m = new LinkedHashMap<>();
                    m.put("Fecha", String.valueOf(q.getFecha()));
                    m.put("Fuente", q.getFuente());
                    m.put("Comprador", q.getComprador());
                    m.put("Numero_Boleta", q.getNumeroBoleta());
                    m.put("Producidos_Kg", q.getProducidosKg());
                    m.put("Vendidos_Kg", q.getVendidosKg());
                    m.put("Diferencia_Kg", q.getProducidosKg() - q.getVendidosKg());
                    m.put("Total_Venta", q.getTotalVenta() != null ? q.getTotalVenta() : "No visible en evidencia");
                    m.put("Observacion", q.getObservaciones());
                    return m;
                })
                .toList();
    }

    private List<LinkedHashMap<String, Object>> configuracion() {
        List<LinkedHashMap<String, Object>> filas = new ArrayList<>();
        for (ParametroNegocio p : parametroNegocioRepository.findAll()) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("Campo", p.getClave());
            m.put("Valor", p.getValor());
            filas.add(m);
        }
        for (TarifaLeche t : tarifaLecheRepository.findAll()) {
            LinkedHashMap<String, Object> m = new LinkedHashMap<>();
            m.put("Campo", "Tarifa vigente desde " + t.getVigenteDesde());
            m.put("Valor", "Normal S/ " + t.getPrecioNormal() + " · Observada S/ " + t.getPrecioObservada());
            filas.add(m);
        }
        return filas;
    }

    private List<LinkedHashMap<String, Object>> auditoria() {
        return auditoriaRepository.findAllByOrderByFechaDesc(org.springframework.data.domain.PageRequest.of(0, 500))
                .getContent()
                .stream()
                .map(this::filaAuditoria)
                .toList();
    }

    private LinkedHashMap<String, Object> filaAuditoria(RegistroAuditoria a) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("Fecha_Hora", String.valueOf(a.getFecha()));
        m.put("Actor", a.getActor());
        m.put("Accion", a.getAccion());
        m.put("Entidad", a.getEntidad());
        m.put("Detalle", a.getDetalle());
        return m;
    }
}
