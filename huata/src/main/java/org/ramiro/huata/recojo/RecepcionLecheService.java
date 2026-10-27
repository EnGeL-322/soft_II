package org.ramiro.huata.recojo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.inventario.DocumentoOrigenTipo;
import org.ramiro.huata.inventario.LecheAcopioService;
import org.ramiro.huata.parametros.ReglasLecheService;
import org.ramiro.huata.parametros.ReglasLecheService.ReglasLeche;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuanta leche APTA entra al almacen (V31, V33).
 *
 * <p><b>Por ruta:</b> lo que planta anoto como recibido, menos la leche no apta de esa ruta ese dia.
 * La RECHAZADA nunca entra (adulterada o devuelta); la OBSERVADA entra o no segun "Reglas de la
 * leche". Una recepcion todavia sin control de calidad se considera apta hasta que se analice: si
 * luego sale rechazada, el almacen se corrige solo.
 *
 * <p><b>Entrega directa en planta:</b> sus litros, si es apta y no esta anulada.
 *
 * <p>Se recalcula el objetivo completo cada vez (al registrar planta, al anular una entrega, al
 * cargar o corregir un control) y {@link LecheAcopioService} mueve solo la diferencia. Asi el orden
 * en que llegan los datos no cambia el resultado.
 */
@Service
@RequiredArgsConstructor
public class RecepcionLecheService {

    private static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");

    private final ConciliacionRutaRepository conciliaciones;
    private final RecojoRepository recojos;
    private final ControlCalidadRepository controles;
    private final ReglasLecheService reglasLeche;
    private final LecheAcopioService lecheAcopio;

    /** Si la leche con este resultado entra al almacen. Sin control todavia: apta hasta que se analice. */
    public static boolean esApta(ResultadoCalidad resultado, ReglasLeche reglas) {
        if (resultado == null || resultado == ResultadoCalidad.ACEPTADA) return true;
        if (resultado == ResultadoCalidad.RECHAZADA) return false;
        return reglas.observadaEntraAInventario();
    }

    /** Litros no aptos entre las recepciones EN RUTA dadas (las directas en planta van aparte). */
    public static double litrosNoAptos(
            List<Recojo> deLaRuta, Map<Long, ResultadoCalidad> calidad, ReglasLeche reglas) {
        return deLaRuta.stream()
                .filter(r -> r.getFuente() != FuenteRecojo.PLANTA)
                .filter(r -> r.getLitros() != null)
                .filter(r -> !esApta(calidad.get(r.getId()), reglas))
                .mapToDouble(Recojo::getLitros)
                .sum();
    }

    /** Resultado de calidad de cada recepcion del dia, en una sola consulta. */
    public Map<Long, ResultadoCalidad> calidadDelDia(LocalDate fecha) {
        return controles.findByRecojo_FechaHoraBetween(inicio(fecha), inicio(fecha.plusDays(1))).stream()
                .collect(Collectors.toMap(c -> c.getRecojo().getId(), ControlCalidad::getResultado, (a, b) -> b));
    }

    public ReglasLeche reglas() {
        return reglasLeche.vigentes();
    }

    /** Lleva al almacen lo apto de lo recibido en planta de una ruta y dia (si ya se registro). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sincronizarRuta(LocalDate fecha, Long rutaId) {
        conciliaciones.findByFechaAndRuta_Id(fecha, rutaId).ifPresent(c -> {
            if (c.getLitrosRecibidosPlanta() == null) return;
            List<Recojo> deLaRuta = recojos.delDiaConDetalle(inicio(fecha), inicio(fecha.plusDays(1)), EstadoRecojo.CANCELLED)
                    .stream()
                    .filter(r -> r.getRuta() != null && r.getRuta().getId().equals(rutaId))
                    .toList();
            double noAptos = litrosNoAptos(deLaRuta, calidadDelDia(fecha), reglas());
            lecheAcopio.ajustarHasta(
                    BigDecimal.valueOf(c.getLitrosRecibidosPlanta() - noAptos),
                    fecha,
                    DocumentoOrigenTipo.CONCILIACION,
                    c.getId(),
                    c.getRuta().getNombre() + " · recibido en planta el " + fecha);
        });
    }

    /** La leche que el proveedor llevo el mismo a planta: sus litros si es apta y no esta anulada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sincronizarEntregaDirecta(Recojo recojo) {
        boolean cuenta = recojo.getFuente() == FuenteRecojo.PLANTA
                && recojo.getEstado() != EstadoRecojo.CANCELLED
                && recojo.getEstadoVisita() != EstadoVisita.NO_ENTREGADO
                && recojo.getLitros() != null
                && esApta(controles.findByRecojoId(recojo.getId()).map(ControlCalidad::getResultado).orElse(null), reglas());
        lecheAcopio.ajustarHasta(
                BigDecimal.valueOf(cuenta ? recojo.getLitros() : 0),
                fecha(recojo),
                DocumentoOrigenTipo.RECOJO,
                recojo.getId(),
                "Entrega en planta de " + recojo.getProductor().getCodigo());
    }

    /** Tras registrar o corregir su calidad, o anularla: se recalcula lo que le corresponde. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void alCambiar(Recojo recojo) {
        if (recojo.getFuente() == FuenteRecojo.PLANTA) {
            sincronizarEntregaDirecta(recojo);
        } else if (recojo.getRuta() != null) {
            sincronizarRuta(fecha(recojo), recojo.getRuta().getId());
        }
    }

    private static LocalDate fecha(Recojo recojo) {
        return recojo.getFechaHora().atZone(ZONA_HUATA).toLocalDate();
    }

    private static java.time.Instant inicio(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA_HUATA).toInstant();
    }
}
