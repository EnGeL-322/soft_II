package org.ramiro.huata.reportes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.EstadoIncidencia;
import org.ramiro.huata.calidad.IncidenciaCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.liquidaciones.EstadoLiquidacion;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.stereotype.Service;

/**
 * Agregaciones de solo lectura sobre los demas modulos. A proposito no persiste nada propio: los
 * reportes se calculan en el momento para no duplicar la fuente de verdad de cada modulo.
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    private final RecojoRepository recojoRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final IncidenciaCalidadRepository incidenciaCalidadRepository;
    private final LiquidacionRepository liquidacionRepository;
    private final org.ramiro.huata.liquidaciones.LiquidacionDetalleRepository liquidacionDetalles;

    public DashboardResponse dashboard(Instant desde, Instant hasta) {
        Instant fin = hasta != null ? hasta : Instant.now();
        Instant inicio = desde != null ? desde : fin.minus(30, ChronoUnit.DAYS);

        List<Recojo> recojos = recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);

        double litrosTotales = recojos.stream().mapToDouble(Recojo::getLitros).sum();

        List<ProduccionPorClaveResponse> porRuta = agrupar(recojos, r -> r.getRuta() != null ? r.getRuta().getNombre() : "Sin ruta");
        List<ProduccionPorClaveResponse> porProductor = agrupar(
                recojos, r -> r.getProductor().getNombres() + " " + r.getProductor().getApellidos());

        List<ControlCalidad> controles = controlCalidadRepository.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && !c.getCreatedAt().isBefore(inicio) && !c.getCreatedAt().isAfter(fin))
                .toList();
        Map<ResultadoCalidad, Long> tendenciaMap =
                controles.stream().collect(Collectors.groupingBy(ControlCalidad::getResultado, Collectors.counting()));
        List<TendenciaCalidadResponse> tendencia = Arrays.stream(ResultadoCalidad.values())
                .map(r -> new TendenciaCalidadResponse(r, tendenciaMap.getOrDefault(r, 0L)))
                .toList();

        long incidenciasAbiertas = incidenciaCalidadRepository.findAll().stream()
                .filter(i -> i.getEstado() != EstadoIncidencia.CERRADA)
                .count();

        long liquidacionesPendientes = liquidacionRepository.findAll().stream()
                .filter(l -> l.getEstado() != EstadoLiquidacion.CLOSED)
                .count();

        return new DashboardResponse(
                litrosTotales, recojos.size(), porRuta, porProductor.stream().limit(15).toList(), tendencia,
                incidenciasAbiertas, liquidacionesPendientes);
    }

    /**
     * Serie diaria de litros aceptados y observados: es la base de los gráficos de tendencia
     * (Reportes y Centro de operación).
     */
    public List<PuntoSerieDiaria> serieDiaria(Instant desde, Instant hasta) {
        Instant fin = hasta != null ? hasta : Instant.now();
        Instant inicio = desde != null ? desde : fin.minus(14, ChronoUnit.DAYS);

        List<Recojo> recojos = recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);
        Map<Long, ResultadoCalidad> resultados = controlCalidadRepository.findAll().stream()
                .filter(c -> c.getRecojo() != null)
                .collect(Collectors.toMap(c -> c.getRecojo().getId(), ControlCalidad::getResultado, (a, b) -> a));

        ZoneId zona = ZoneId.of("America/Lima");
        Map<LocalDate, List<Recojo>> porDia = recojos.stream()
                .collect(Collectors.groupingBy(r -> r.getFechaHora().atZone(zona).toLocalDate(), TreeMap::new, Collectors.toList()));

        return porDia.entrySet().stream()
                .map(e -> {
                    double litros = e.getValue().stream().mapToDouble(Recojo::getLitros).sum();
                    double observados = e.getValue().stream()
                            .filter(r -> resultados.get(r.getId()) == ResultadoCalidad.OBSERVADA)
                            .mapToDouble(Recojo::getLitros)
                            .sum();
                    return new PuntoSerieDiaria(e.getKey(), litros, e.getValue().size(), observados);
                })
                .toList();
    }

    /** "Zonas y rendimiento": semana actual vs semana anterior, igual criterio que ResumenZonas del prototipo. */
    public List<ZonaResumen> zonas() {
        Instant ahora = Instant.now();
        Instant inicioActual = ahora.minus(7, ChronoUnit.DAYS);
        Instant inicioAnterior = ahora.minus(14, ChronoUnit.DAYS);

        Map<String, Double> anterior = agruparPorZona(
                recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicioAnterior, inicioActual, EstadoRecojo.CANCELLED));
        Map<String, Double> actual = agruparPorZona(
                recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicioActual, ahora, EstadoRecojo.CANCELLED));

        double totalActual = actual.values().stream().mapToDouble(Double::doubleValue).sum();

        TreeSet<String> zonas = new TreeSet<>();
        zonas.addAll(anterior.keySet());
        zonas.addAll(actual.keySet());

        return zonas.stream()
                .map(zona -> {
                    double litrosAnterior = anterior.getOrDefault(zona, 0.0);
                    double litrosActual = actual.getOrDefault(zona, 0.0);
                    double variacion = litrosAnterior == 0
                            ? (litrosActual > 0 ? 100.0 : 0.0)
                            : ((litrosActual - litrosAnterior) / litrosAnterior) * 100.0;
                    double participacion = totalActual == 0 ? 0.0 : (litrosActual / totalActual) * 100.0;
                    return new ZonaResumen(zona, litrosAnterior, litrosActual, variacion, participacion);
                })
                .sorted((a, b) -> Double.compare(b.litrosPeriodoActual(), a.litrosPeriodoActual()))
                .toList();
    }

    /**
     * "Seguimiento de producción": busqueda libre por codigo de proveedor, nombre, zona, fecha o
     * id de recepción, igual al buscador global del prototipo ("DEMO-001 · fecha · zona · código").
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TrazabilidadItem> trazabilidad(String q) {
        List<Recojo> recojos = recojoRepository.findAll();
        String needle = q == null ? "" : q.trim().toLowerCase();
        var calidad = controlCalidadRepository.findAll().stream().filter(ControlCalidad::isActivo)
                .collect(Collectors.toMap(c -> c.getRecojo().getId(), c -> c));
        var pagos = liquidacionDetalles.findAll().stream().filter(d -> d.getLiquidacion().isActivo())
                .collect(Collectors.groupingBy(d -> d.getRecojo().getId()));

        return recojos.stream()
                .filter(Recojo::isActivo)
                .filter(r -> needle.isBlank() || coincide(r, needle))
                .sorted(Comparator.comparing(Recojo::getFechaHora).reversed())
                .map(r -> aTrazabilidad(r, calidad.get(r.getId()), pagos.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    private boolean coincide(Recojo r, String needle) {
        String zona = zonaDe(r);
        return r.getProductor().getCodigo().toLowerCase().contains(needle)
                || (r.getProductor().getNombres() + " " + r.getProductor().getApellidos()).toLowerCase().contains(needle)
                || zona.toLowerCase().contains(needle)
                || r.getFechaHora().toString().toLowerCase().contains(needle)
                || String.valueOf(r.getId()).equals(needle)
                || r.getClientUuid().toLowerCase().contains(needle);
    }

    private TrazabilidadItem aTrazabilidad(Recojo r, ControlCalidad c,
            List<org.ramiro.huata.liquidaciones.LiquidacionDetalle> pagos) {
        return new TrazabilidadItem(
                r.getId(),
                r.getClientUuid(),
                r.getProductor().getCodigo(),
                r.getProductor().getNombres() + " " + r.getProductor().getApellidos(),
                zonaDe(r),
                r.getRuta() != null ? r.getRuta().getNombre() : null,
                r.getFechaHora(),
                r.getLitros(),
                r.getEstado().name(),
                r.getAcopiador() == null ? null : r.getAcopiador().getNombres() + " " + r.getAcopiador().getApellidos(),
                c == null ? null : c.getResultado().name(),
                c == null ? null : c.getDensidad(),
                c == null ? null : c.getResponsable(),
                c == null ? null : (c.getMotivoRechazo() != null ? c.getMotivoRechazo() : c.getRevisionVisual()),
                pagos.stream().map(d -> new TraceLiquidacion(d.getLiquidacion().getId(),
                        d.getLiquidacion().getPeriodoInicio(), d.getLiquidacion().getPeriodoFin(),
                        d.getLiquidacion().getEstado().name(), d.getSubtotal())).toList());
    }

    private String zonaDe(Recojo r) {
        if (r.getRuta() == null) {
            return "Sin ruta";
        }
        return r.getRuta().getZona() != null && !r.getRuta().getZona().isBlank() ? r.getRuta().getZona() : r.getRuta().getNombre();
    }

    private Map<String, Double> agruparPorZona(List<Recojo> recojos) {
        return recojos.stream()
                .filter(r -> r.getRuta() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getRuta().getZona() != null && !r.getRuta().getZona().isBlank()
                                ? r.getRuta().getZona()
                                : r.getRuta().getNombre(),
                        Collectors.summingDouble(Recojo::getLitros)));
    }

    private List<ProduccionPorClaveResponse> agrupar(List<Recojo> recojos, java.util.function.Function<Recojo, String> clave) {
        Map<String, List<Recojo>> agrupados = recojos.stream().collect(Collectors.groupingBy(clave));
        return agrupados.entrySet().stream()
                .map(e -> new ProduccionPorClaveResponse(
                        e.getKey(), e.getValue().stream().mapToDouble(Recojo::getLitros).sum(), e.getValue().size()))
                .sorted((a, b) -> Double.compare(b.litros(), a.litros()))
                .toList();
    }
}
