package org.ramiro.huata.ubicaciones;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UbicacionGpsService {

    private final UbicacionGpsRepository ubicacionGpsRepository;
    private final AcopiadorRepository acopiadorRepository;

    /** Margen para un reloj de telefono mal puesto. Mas alla de esto el punto se rechaza. */
    private static final Duration TOLERANCIA_RELOJ = Duration.ofMinutes(5);

    @Transactional
    public UbicacionGps registrar(String username, UbicacionRequest req) {
        return ubicacionGpsRepository.save(construir(acopiadorDe(username), req));
    }

    /**
     * Guarda de una vez los puntos que el movil no pudo enviar sin cobertura (RN-18).
     *
     * <p>Dos cuidados que no tiene el alta de uno en uno:
     *
     * <ul>
     *   <li><b>No duplica.</b> Si la conexion se corta a mitad del envio, el movil reintenta el
     *       lote completo; los puntos que ya estaban (mismo acopiador y misma hora de captura) se
     *       cuentan como duplicados y no se vuelven a insertar. Sin esto el recorrido acabaria con
     *       tramos repetidos.
     *   <li><b>No acepta puntos del futuro.</b> La hora la pone el dispositivo, y un reloj mal
     *       puesto dejaria una "ultima ubicacion" que nunca caduca en el panel de seguimiento.
     * </ul>
     */
    @Transactional
    public UbicacionLoteResponse registrarLote(String username, UbicacionLoteRequest req) {
        Acopiador acopiador = acopiadorDe(username);
        Instant limite = Instant.now().plus(TOLERANCIA_RELOJ);
        if (req.puntos().stream().anyMatch(p -> p.fecha() != null && p.fecha().isAfter(limite))) {
            throw new BusinessRuleException(
                    "El lote trae puntos con fecha futura; revisa la hora del dispositivo.");
        }

        Set<Instant> yaGuardadas = fechasYaGuardadas(acopiador.getId(), req.puntos());
        List<UbicacionGps> nuevas = new ArrayList<>();
        Set<Instant> enEsteLote = new HashSet<>();
        for (UbicacionRequest punto : req.puntos()) {
            // Un punto sin fecha no se puede deduplicar; se guarda igual con la hora del servidor.
            if (punto.fecha() != null && !(yaGuardadas.add(punto.fecha()) && enEsteLote.add(punto.fecha()))) {
                continue;
            }
            nuevas.add(construir(acopiador, punto));
        }
        ubicacionGpsRepository.saveAll(nuevas);
        return new UbicacionLoteResponse(nuevas.size(), req.puntos().size() - nuevas.size());
    }

    /** Las fechas que ya existen dentro de la ventana del lote, en una sola consulta. */
    private Set<Instant> fechasYaGuardadas(Long acopiadorId, List<UbicacionRequest> puntos) {
        List<Instant> fechas = puntos.stream().map(UbicacionRequest::fecha).filter(Objects::nonNull).toList();
        if (fechas.isEmpty()) {
            return new HashSet<>();
        }
        Instant desde = fechas.stream().min(Instant::compareTo).orElseThrow();
        Instant hasta = fechas.stream().max(Instant::compareTo).orElseThrow();
        return ubicacionGpsRepository
                .findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(acopiadorId, desde, hasta)
                .stream()
                .map(UbicacionGps::getFecha)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Acopiador acopiadorDe(String username) {
        return acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .orElseThrow(() -> new BusinessRuleException(
                        "Tu usuario no está vinculado a un acopiador; no se puede reportar ubicación."));
    }

    private static UbicacionGps construir(Acopiador acopiador, UbicacionRequest req) {
        UbicacionGps ubicacion = new UbicacionGps();
        ubicacion.setAcopiador(acopiador);
        ubicacion.setLatitud(req.latitud());
        ubicacion.setLongitud(req.longitud());
        ubicacion.setPrecisionMetros(req.precisionMetros());
        ubicacion.setVelocidadMs(req.velocidadMs());
        if (req.fecha() != null) {
            ubicacion.setFecha(req.fecha());
        }
        return ubicacion;
    }

    /** Punto mas reciente del acopiador autenticado; lo usa el envio por lotes para el WebSocket. */
    public Optional<UbicacionResponse> ultimaDe(String username) {
        return ubicacionGpsRepository
                .findTopByAcopiadorIdOrderByFechaDesc(acopiadorDe(username).getId())
                .map(UbicacionResponse::from);
    }

    /** Última posición reportada por cada acopiador (sin ventana de tiempo: el front muestra "hace X" para ver si está vigente). */
    public List<UbicacionResponse> ultimas() {
        // La consulta ya trae uno por acopiador; el mapa solo protege del empate de dos puntos con la
        // misma hora exacta (el lote los deduplica, pero un punto suelto y uno de lote podrian coincidir).
        Map<Long, UbicacionGps> masReciente = new LinkedHashMap<>();
        for (UbicacionGps u : ubicacionGpsRepository.ultimaDeCadaAcopiador()) {
            masReciente.putIfAbsent(u.getAcopiador().getId(), u);
        }
        return masReciente.values().stream().map(UbicacionResponse::from).toList();
    }

    public List<UbicacionResponse> historial(Long acopiadorId, Instant desde, Instant hasta) {
        Instant fin = hasta != null ? hasta : Instant.now();
        Instant inicio = desde != null ? desde : fin.minus(12, ChronoUnit.HOURS);
        return ubicacionGpsRepository.findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(acopiadorId, inicio, fin).stream()
                .map(UbicacionResponse::from)
                .toList();
    }
}
