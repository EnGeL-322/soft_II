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
        var resultados = controles.findAll().stream().filter(c -> ids.contains(c.getRecojo().getId()))
                .collect(Collectors.toMap(c -> c.getRecojo().getId(), ControlCalidad::getResultado));
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

    @Transactional
    public CierreAcopio cerrar(LocalDate fecha, double litrosPlanta, String observacion) {
        if (fecha.isAfter(LocalDate.now(ZONA)) || !Double.isFinite(litrosPlanta) || litrosPlanta < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fecha o litros inválidos.");
        var resumen = resumen(fecha);
        if (resumen.pendientesCalidad() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completa los controles de calidad pendientes.");
        if (Math.abs(resumen.aceptados() - litrosPlanta) > 0.01 && (observacion == null || observacion.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Explica la diferencia en la observación.");
        var cierre = cierres.findByFecha(fecha).orElseGet(CierreAcopio::new);
        if (cierre.isCerrado()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La conciliación ya está cerrada.");
        cierre.setFecha(fecha);
        cierre.setLitrosSistema(resumen.aceptados());
        cierre.setLitrosPlanta(litrosPlanta);
        cierre.setObservacion(observacion);
        cierre.setCerrado(true);
        cierres.save(cierre);
        auditoria.registrar("Cierre diario", "Acopio", fecha + " · Sistema: " + resumen.aceptados() + " L · Planta: " + litrosPlanta + " L");
        return cierre;
    }

    @Transactional
    public CierreAcopio reabrir(LocalDate fecha) {
        var cierre = cierres.findByFecha(fecha).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!cierre.isCerrado()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La conciliación ya está abierta.");
        cierre.setCerrado(false);
        auditoria.registrar("Reapertura diaria", "Acopio", fecha.toString());
        return cierres.save(cierre);
    }
}
