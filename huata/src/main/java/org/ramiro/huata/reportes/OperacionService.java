package org.ramiro.huata.reportes;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.calidad.*;
import org.ramiro.huata.liquidaciones.*;
import org.ramiro.huata.recojo.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OperacionService {
    private final RecojoRepository recojos;
    private final ControlCalidadRepository controles;
    private final LiquidacionRepository liquidaciones;
    private final TarifaLecheRepository tarifas;
    private final CierreAcopioRepository cierres;
    private final ConciliacionRutaRepository conciliaciones;
    private final AuditoriaService auditoria;
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    public record RutaDia(String nombre, double litros, long recepciones) {}
    public record ResumenDia(LocalDate fecha, double aceptados, double observados, double rechazados,
            long recepcionesAceptadas, long controlesObservados, long entregasRechazadas,
            long proveedores, long pendientesCalidad, long liquidacionesPendientes,
            Double compraEstimada, List<RutaDia> rutas, CierreAcopio cierre) {}

    @Transactional(readOnly = true)
    public ResumenDia resumen(LocalDate fecha) {
        var inicio = fecha.atStartOfDay(ZONA).toInstant();
        var fin = fecha.plusDays(1).atStartOfDay(ZONA).toInstant().minusNanos(1);
        var filas = recojos.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);
        var ids = filas.stream().map(Recojo::getId).collect(Collectors.toSet());
        // Solo los controles de las recepciones del dia. Antes era controles.findAll(): toda la
        // historia de calidad en memoria para quedarse con las de un dia.
        var resultados = controles.findByRecojo_FechaHoraBetween(inicio, fin).stream()
                .filter(c -> ids.contains(c.getRecojo().getId()))
                .collect(Collectors.toMap(c -> c.getRecojo().getId(), ControlCalidad::getResultado, (a, b) -> b));
        double aceptados = 0, observados = 0, rechazados = 0;
        long aceptadas = 0, observadas = 0, rechazadas = 0, pendientes = 0;
        for (var r : filas) {
            var calidad = resultados.get(r.getId());
            if (calidad == null) { pendientes++; continue; }
            if (calidad == ResultadoCalidad.RECHAZADA) { rechazados += r.getLitros(); rechazadas++; }
            else {
                aceptados += r.getLitros(); aceptadas++;
                if (calidad == ResultadoCalidad.OBSERVADA) { observados += r.getLitros(); observadas++; }
            }
        }
        var tarifa = tarifas.findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fecha)
                .filter(t -> t.isActivo() && (t.getVigenteHasta() == null || !t.getVigenteHasta().isBefore(fecha)));
        Double compra = tarifa.isEmpty() ? null : (aceptados - observados) * tarifa.get().getPrecioNormal()
                + observados * tarifa.get().getPrecioObservada();
        var rutas = filas.stream().collect(Collectors.groupingBy(r -> r.getRuta() == null ? "Sin ruta" : r.getRuta().getNombre()))
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> new RutaDia(e.getKey(), e.getValue().stream().mapToDouble(Recojo::getLitros).sum(), e.getValue().size())).toList();
        return new ResumenDia(fecha, aceptados, observados, rechazados, aceptadas, observadas, rechazadas,
                filas.stream().map(r -> r.getProductor().getId()).distinct().count(), pendientes,
                liquidaciones.findAll().stream().filter(l -> l.getEstado() != EstadoLiquidacion.CLOSED).count(),
                compra, rutas, cierres.findByFecha(fecha).orElse(null));
    }

    /** Lo que el cierre del dia compara, calculado (no escrito a mano). */
    public record TotalesDia(double recogido, double recibidoPlanta, List<String> rutasSinRecibir) {}

    /**
     * Totales del cierre a partir de las rutas (decision del cliente, 2026-09-27). Antes el cierre
     * pedia escribir a mano un total de planta que podia contradecir a la conciliacion por ruta, y
     * solo la conciliacion movia el inventario. Ahora hay un solo dato: lo recibido en cada ruta.
     *
     * <p>Recogido = toda la leche del dia (camion y entregas directas). Recibido en planta = lo que
     * planta anoto de cada ruta mas las entregas directas, que ya estan en planta.
     */
    @Transactional(readOnly = true)
    public TotalesDia totales(LocalDate fecha) {
        var inicio = fecha.atStartOfDay(ZONA).toInstant();
        var fin = fecha.plusDays(1).atStartOfDay(ZONA).toInstant().minusNanos(1);
        var filas = recojos.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);
        double recogido = filas.stream().mapToDouble(Recojo::getLitros).sum();
        double directo = filas.stream()
                .filter(r -> r.getFuente() == FuenteRecojo.PLANTA)
                .mapToDouble(Recojo::getLitros)
                .sum();
        var registradas = conciliaciones.findByFechaOrderByRuta_Nombre(fecha).stream()
                .filter(c -> c.getLitrosRecibidosPlanta() != null)
                .toList();
        double recibidoRutas = registradas.stream().mapToDouble(ConciliacionRuta::getLitrosRecibidosPlanta).sum();
        Set<Long> conTotal = registradas.stream().map(c -> c.getRuta().getId()).collect(Collectors.toSet());
        List<String> sinRecibir = filas.stream()
                .filter(r -> r.getFuente() != FuenteRecojo.PLANTA && r.getRuta() != null)
                .map(Recojo::getRuta)
                .filter(ruta -> !conTotal.contains(ruta.getId()))
                .map(ruta -> ruta.getNombre())
                .distinct()
                .sorted()
                .toList();
        return new TotalesDia(recogido, recibidoRutas + directo, sinRecibir);
    }

    @Transactional
    public CierreAcopio cerrar(LocalDate fecha, String observacion) {
        if (fecha.isAfter(LocalDate.now(ZONA)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cerrar un día que todavía no ocurrió.");
        var resumen = resumen(fecha);
        if (resumen.pendientesCalidad() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Faltan " + resumen.pendientesCalidad()
                    + " control(es) de calidad de este día. Complétalos antes de cerrar.");
        var totales = totales(fecha);
        if (!totales.rutasSinRecibir().isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Falta anotar lo recibido en planta de: " + String.join(", ", totales.rutasSinRecibir()) + ".");
        if (Math.abs(totales.recogido() - totales.recibidoPlanta()) > 0.01
                && (observacion == null || observacion.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lo recogido y lo recibido en planta no coinciden: explica la diferencia en la observación.");
        var cierre = cierres.findByFecha(fecha).orElseGet(CierreAcopio::new);
        if (cierre.isCerrado()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El día ya está cerrado.");
        double litrosPlanta = totales.recibidoPlanta();
        cierre.setFecha(fecha);
        cierre.setLitrosSistema(totales.recogido());
        cierre.setLitrosPlanta(litrosPlanta);
        cierre.setObservacion(observacion);
        cierre.setCerrado(true);
        cierres.save(cierre);
        auditoria.registrar("Cierre diario", "Acopio", fecha + " · Recogido: " + totales.recogido() + " L · Planta: " + litrosPlanta + " L");
        return cierre;
    }

    @Transactional
    public CierreAcopio reabrir(LocalDate fecha) {
        var cierre = cierres.findByFecha(fecha).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!cierre.isCerrado()) throw new ResponseStatusException(HttpStatus.CONFLICT, "El día ya está abierto.");
        cierre.setCerrado(false);
        auditoria.registrar("Reapertura diaria", "Acopio", fecha.toString());
        return cierres.save(cierre);
    }
}
