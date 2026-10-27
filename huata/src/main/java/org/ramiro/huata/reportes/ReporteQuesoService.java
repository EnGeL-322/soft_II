package org.ramiro.huata.reportes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.compras.Compra;
import org.ramiro.huata.compras.CompraItem;
import org.ramiro.huata.compras.CompraRepository;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.liquidaciones.EstadoLiquidacion;
import org.ramiro.huata.liquidaciones.Liquidacion;
import org.ramiro.huata.liquidaciones.LiquidacionRepository;
import org.ramiro.huata.liquidaciones.LiquidacionService;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRepository;
import org.ramiro.huata.quesos.VentaQueso;
import org.ramiro.huata.quesos.VentaQuesoRepository;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.stereotype.Service;

/**
 * Reporte semanal de queso (RF-42, RF-63, RF-73), calculado sobre el modelo vigente ({@link
 * LoteQueso}/{@link VentaQueso}, no la tabla historica {@code Queso}), en el mismo formato que el
 * cuaderno fisico de la Unidad de la Cadena Productiva de Lacteos - Huata: venta de quesos por dia,
 * rendimiento de acopio por zona y dia, y registro de ingresos/egresos del ciclo.
 *
 * <p>"Despachado" no existe como movimiento de inventario propio (no hay un paso intermedio entre
 * produccion y venta en este sistema): por decision de negocio se reporta igual al numero de
 * "vendido", solo para que la hoja tenga las mismas columnas que el formato fisico.
 *
 * <p>"TOTAL DE QUESOS EN DESCUENTO" y "TOTAL EN ALMACEN" son dos numeros distintos a proposito:
 * el primero es la suma neta de la columna Diferencia (producidos - vendidos) del ciclo; el segundo
 * es el stock real vigente segun el kardex ({@link InventarioService}). En el documento fisico
 * tambien difieren entre si (no son el mismo numero repetido), asi que no se fuerza a que coincidan
 * aqui.
 */
@Service
@RequiredArgsConstructor
public class ReporteQuesoService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final LoteQuesoRepository loteQuesoRepository;
    private final VentaQuesoRepository ventaQuesoRepository;
    private final RecojoRepository recojoRepository;
    private final CompraRepository compraRepository;
    private final LiquidacionRepository liquidacionRepository;
    private final InventarioService inventarioService;

    public ReporteQuesoSemanalResponse semanal(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            LocalDate[] ciclo = LiquidacionService.cicloActual();
            desde = ciclo[0];
            hasta = ciclo[1];
        }
        if (hasta.isBefore(desde)) {
            throw new BusinessRuleException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (hasta.isAfter(desde.plusDays(31))) {
            throw new BusinessRuleException("El rango del reporte semanal de queso no puede superar 31 dias.");
        }

        List<LocalDate> dias = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            dias.add(d);
        }

        List<LoteQueso> lotes = loteQuesoRepository.findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(desde, hasta);
        List<VentaQueso> ventas = ventaQuesoRepository.findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(desde, hasta);

        Map<LocalDate, Integer> producidosPorDia =
                lotes.stream().collect(Collectors.groupingBy(LoteQueso::getFecha, Collectors.summingInt(LoteQueso::getUnidadesProducidas)));
        Map<LocalDate, Integer> vendidosPorDia =
                ventas.stream().collect(Collectors.groupingBy(VentaQueso::getFecha, Collectors.summingInt(VentaQueso::getUnidades)));
        Map<LocalDate, Double> ingresosPorDia =
                ventas.stream().collect(Collectors.groupingBy(VentaQueso::getFecha, Collectors.summingDouble(VentaQueso::total)));

        List<VentaQuesoDiaRow> ventaPorDia = new ArrayList<>();
        int totalProducidos = 0;
        int totalVendidos = 0;
        int totalDiferencia = 0;
        for (LocalDate d : dias) {
            int producidos = producidosPorDia.getOrDefault(d, 0);
            int vendidos = vendidosPorDia.getOrDefault(d, 0);
            int diferencia = producidos - vendidos;
            ventaPorDia.add(new VentaQuesoDiaRow(d, nombreDia(d), producidos, vendidos, vendidos, diferencia));
            totalProducidos += producidos;
            totalVendidos += vendidos;
            totalDiferencia += diferencia;
        }

        List<Long> idsLotesActivos =
                loteQuesoRepository.findByActivoTrueOrderByFechaDescIdDesc().stream().map(LoteQueso::getId).toList();
        double stockAlmacen = inventarioService.saldosLotesQueso(idsLotesActivos).values().stream()
                .mapToDouble(BigDecimal::doubleValue)
                .sum();

        // ---- Rendimiento: litros acopiados por zona y dia ----
        Instant instanteDesde = desde.atStartOfDay(ZONA).toInstant();
        Instant instanteHasta = hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
        List<Recojo> recojos = recojoRepository
                .findByFechaHoraBetweenAndEstadoNot(instanteDesde, instanteHasta, EstadoRecojo.CANCELLED)
                .stream()
                .filter(r -> r.getRuta() != null)
                .toList();

        TreeSet<String> zonasOrdenadas = recojos.stream().map(this::zonaDe).collect(Collectors.toCollection(TreeSet::new));
        List<String> zonas = new ArrayList<>(zonasOrdenadas);

        Map<LocalDate, Map<String, Double>> litrosPorDiaYZona = recojos.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getFechaHora().atZone(ZONA).toLocalDate(),
                        TreeMap::new,
                        Collectors.groupingBy(this::zonaDe, Collectors.summingDouble(Recojo::getLitros))));

        List<RendimientoDiaRow> rendimientoPorDia = new ArrayList<>();
        double totalAcopioCiclo = 0;
        for (LocalDate d : dias) {
            Map<String, Double> litrosDelDia = litrosPorDiaYZona.getOrDefault(d, Map.of());
            List<Double> litrosPorZona = zonas.stream().map(z -> litrosDelDia.getOrDefault(z, 0.0)).toList();
            double totalAcopioDia = litrosDelDia.values().stream().mapToDouble(Double::doubleValue).sum();
            int produccionDia = producidosPorDia.getOrDefault(d, 0);
            double rendimientoDia = produccionDia == 0 ? 0.0 : redondear(totalAcopioDia / produccionDia);
            rendimientoPorDia.add(new RendimientoDiaRow(d, nombreDia(d), litrosPorZona, totalAcopioDia, produccionDia, rendimientoDia));
            totalAcopioCiclo += totalAcopioDia;
        }

        // ---- Registro de ventas: ingresos / egresos ----
        List<RegistroDiaRow> registroPorDia =
                dias.stream().map(d -> new RegistroDiaRow(d, nombreDia(d), ingresosPorDia.getOrDefault(d, 0.0))).toList();
        double totalIngresos = registroPorDia.stream().mapToDouble(RegistroDiaRow::ingresos).sum();

        double totalPagoProveedoresLeche = liquidacionRepository
                .findByActivoTrueAndEstadoAndPeriodoInicioAndPeriodoFin(EstadoLiquidacion.CLOSED, desde, hasta)
                .stream()
                .mapToDouble(Liquidacion::getMontoNeto)
                .sum();

        // Una compra anulada (V59) no fue un egreso.
        List<Compra> compras = compraRepository.findByFechaBetweenOrderByFechaDescIdDesc(desde, hasta).stream()
                .filter(Compra::isActivo)
                .toList();
        List<EgresoRow> egresos = compras.stream()
                .flatMap(c -> c.getItems().stream().map(item -> filaEgreso(c, item)))
                .toList();
        double totalEgresos = egresos.stream().mapToDouble(EgresoRow::monto).sum();

        double totalSaldo = totalIngresos - totalPagoProveedoresLeche - totalEgresos;

        return new ReporteQuesoSemanalResponse(
                desde,
                hasta,
                ventaPorDia,
                totalProducidos,
                totalVendidos,
                totalVendidos,
                totalDiferencia,
                totalDiferencia,
                stockAlmacen,
                zonas,
                rendimientoPorDia,
                redondear(totalAcopioCiclo),
                registroPorDia,
                totalIngresos,
                totalPagoProveedoresLeche,
                egresos,
                totalEgresos,
                totalSaldo);
    }

    private EgresoRow filaEgreso(Compra compra, CompraItem item) {
        String descripcion = item.getMateriaPrima().getNombre() + " x " + item.getCantidad();
        return new EgresoRow(compra.getFecha(), compra.getDocumento(), descripcion, item.total().doubleValue());
    }

    /** Mismo criterio que {@code ReporteService.zonaDe}: zona de la ruta, o su nombre si no tiene zona configurada. */
    private String zonaDe(Recojo r) {
        String zona = r.getRuta().getZona();
        return zona != null && !zona.isBlank() ? zona : r.getRuta().getNombre();
    }

    private String nombreDia(LocalDate d) {
        return switch (d.getDayOfWeek()) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIÉRCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SÁBADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}
