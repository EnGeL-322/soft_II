package org.ramiro.huata.rutas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Pintar ruta del dia" (app movil) y "Asignar como ruta predeterminada" (web).
 *
 * <p>El acopiador dibuja en la app el recorrido que va a hacer hoy —vaya o no por la ruta designada—
 * y administracion puede convertir ese trazado en el recorrido <b>predeterminado de ese acopiador</b>.
 * No toca {@link RutaPunto}: esa geometria es de la RUTA y la comparten todos sus acopiadores; el
 * predeterminado es de una persona. Quien no tenga uno propio sigue teniendo el de la ruta como
 * referencia (ver {@code deLaRuta} en {@link MiTrazadoResponse}).
 *
 * <p>Va en un servicio aparte y no dentro de {@link RutaService} porque ese ya carga el catalogo de
 * rutas, las asignaciones, el plan operativo y los poligonos de zona.
 */
@Service
@RequiredArgsConstructor
public class TrazadoAcopiadorService {

    /** Misma zona que el resto de la operacion: la jornada del acopiador se corta en Lima. */
    private static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");

    private static final String ENTIDAD = "Ruta";

    private final TrazadoAcopiadorRepository repository;
    private final TrazadoAcopiadorPuntoRepository puntoRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final AsignacionRutaRepository asignacionRutaRepository;
    private final RutaPuntoRepository rutaPuntoRepository;
    private final AuditoriaService auditoriaService;

    // ------------------------------------------------------------------ App movil (el acopiador)

    /** Lo que necesita la pantalla "Pintar ruta del dia" para abrirse: ver {@link MiTrazadoResponse}. */
    @Transactional(readOnly = true)
    public MiTrazadoResponse miTrazado(String username, LocalDate fecha) {
        Acopiador acopiador = acopiadorDe(username);
        LocalDate dia = fecha != null ? fecha : LocalDate.now(ZONA_HUATA);
        Ruta ruta = rutaDelDia(acopiador, dia);

        TrazadoAcopiador delDia = repository
                .findByAcopiador_IdAndTipoAndFecha(acopiador.getId(), TipoTrazado.DIA, dia)
                .orElse(null);
        TrazadoAcopiador predeterminado = repository
                .findByAcopiador_IdAndTipo(acopiador.getId(), TipoTrazado.PREDETERMINADO)
                .orElse(null);

        return new MiTrazadoResponse(
                acopiador.getId(),
                nombreDe(acopiador),
                ruta.getId(),
                ruta.getNombre(),
                ruta.getZona(),
                dia,
                conPuntos(delDia),
                conPuntos(predeterminado),
                rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(ruta.getId()).stream()
                        .map(p -> new TrazadoPuntoResponse(p.getSecuencia(), p.getLatitud(), p.getLongitud()))
                        .toList());
    }

    /**
     * Guarda el recorrido que pinto el acopiador para un dia. Volver a guardar <b>reemplaza</b> el
     * trazo completo, que es como se corrige un dibujo en un mapa y el mismo criterio de
     * {@code reemplazarPuntos}; no se acumulan versiones del mismo dia.
     *
     * <p>Es idempotente por (acopiador, fecha), asi que un reintento de la cola offline tras un corte
     * a medio envio no crea un segundo trazado.
     */
    @Transactional
    public TrazadoAcopiadorResponse guardarDelDia(String username, TrazadoDiaRequest req) {
        Acopiador acopiador = acopiadorDe(username);
        LocalDate dia = req.fecha() != null ? req.fecha() : LocalDate.now(ZONA_HUATA);
        // Un trazado con fecha futura seria una intencion, no un recorrido; y con el reloj del
        // telefono mal puesto ensuciaria la pantalla de administracion de un dia que no ha llegado.
        if (dia.isAfter(LocalDate.now(ZONA_HUATA).plusDays(1))) {
            throw new BusinessRuleException("No se puede pintar el recorrido de una fecha futura; revisa la hora del dispositivo.");
        }
        Ruta ruta = rutaDelDia(acopiador, dia);

        TrazadoAcopiador trazado = repository
                .findByAcopiador_IdAndTipoAndFecha(acopiador.getId(), TipoTrazado.DIA, dia)
                .orElseGet(TrazadoAcopiador::new);
        boolean esNuevo = trazado.getId() == null;
        trazado.setAcopiador(acopiador);
        trazado.setRuta(ruta);
        trazado.setTipo(TipoTrazado.DIA);
        trazado.setFecha(dia);
        trazado.setSigueRutaDesignada(req.sigueRutaDesignada());
        trazado.setObservacion(req.observacion());
        TrazadoAcopiador guardado = repository.save(trazado);

        List<TrazadoAcopiadorPunto> puntos = reemplazarPuntos(guardado, req.puntos());
        auditoriaService.registrar(
                esNuevo ? "Recorrido del dia pintado" : "Recorrido del dia corregido",
                ENTIDAD,
                nombreDe(acopiador) + " · " + ruta.getNombre() + " · " + dia + " · "
                        + puntos.size() + " puntos · "
                        + (req.sigueRutaDesignada() ? "sigue la ruta designada" : "recorrido distinto al designado"));
        return TrazadoAcopiadorResponse.from(guardado, puntos);
    }

    /** Descarta el recorrido que el acopiador pinto ese dia; su predeterminado no se toca. */
    @Transactional
    public void borrarDelDia(String username, LocalDate fecha) {
        Acopiador acopiador = acopiadorDe(username);
        LocalDate dia = fecha != null ? fecha : LocalDate.now(ZONA_HUATA);
        TrazadoAcopiador trazado = repository
                .findByAcopiador_IdAndTipoAndFecha(acopiador.getId(), TipoTrazado.DIA, dia)
                .orElseThrow(() -> NotFoundException.of("Recorrido pintado del dia", dia));
        eliminar(trazado);
        auditoriaService.registrar(
                "Recorrido del dia descartado", ENTIDAD, nombreDe(acopiador) + " · " + dia);
    }

    // ----------------------------------------------------------------------- Web (administracion)

    /**
     * Recorridos que se pintaron en una fecha. Los puntos de todos vienen en una sola consulta.
     *
     * <p>La fecha es obligatoria (ver {@link TrazadoAcopiadorController}): sin filtro, la respuesta
     * crece una fila por acopiador y por jornada, indefinidamente.
     */
    @Transactional(readOnly = true)
    public List<TrazadoAcopiadorResponse> pintadosDelDia(LocalDate fecha) {
        return conPuntosEnLote(repository.deTipoEnFecha(TipoTrazado.DIA, fecha));
    }

    /** Uno por acopiador como maximo, asi que no hace falta acotarlo por fecha. */
    @Transactional(readOnly = true)
    public List<TrazadoAcopiadorResponse> predeterminados() {
        return conPuntosEnLote(repository.deTipo(TipoTrazado.PREDETERMINADO));
    }

    /**
     * Convierte un recorrido del dia en el predeterminado de ese acopiador.
     *
     * <p><b>Copia los puntos, no referencia la fila del dia.</b> Si el predeterminado apuntara al
     * trazado del dia, el acopiador corrigiendo su dibujo de hoy cambiaria en silencio su recorrido
     * habitual, y nadie en administracion se enteraria. Queda {@code origenTrazadoId} para saber de
     * donde salio.
     *
     * <p>Reemplaza el predeterminado anterior si habia: es "el" recorrido habitual, no una lista.
     */
    @Transactional
    public TrazadoAcopiadorResponse predeterminar(Long trazadoId, String usuario) {
        TrazadoAcopiador origen = repository.findById(trazadoId).orElseThrow(() -> NotFoundException.of("Trazado", trazadoId));
        if (origen.getTipo() != TipoTrazado.DIA) {
            throw new BusinessRuleException("Ese trazado ya es el predeterminado del acopiador.");
        }
        List<TrazadoAcopiadorPunto> puntosOrigen = puntoRepository.findByTrazado_IdOrderBySecuenciaAsc(trazadoId);
        if (puntosOrigen.size() < 2) {
            throw new BusinessRuleException("El recorrido pintado no tiene puntos suficientes para ser el predeterminado.");
        }

        Acopiador acopiador = origen.getAcopiador();
        TrazadoAcopiador predeterminado = repository
                .findByAcopiador_IdAndTipo(acopiador.getId(), TipoTrazado.PREDETERMINADO)
                .orElseGet(TrazadoAcopiador::new);
        predeterminado.setAcopiador(acopiador);
        // La ruta del predeterminado es la del trazado que se promueve: si el acopiador cambio de
        // ruta, su recorrido habitual pertenece a la ruta donde lo pinto, no a la que tenga manana.
        predeterminado.setRuta(origen.getRuta());
        predeterminado.setTipo(TipoTrazado.PREDETERMINADO);
        predeterminado.setFecha(null);
        predeterminado.setSigueRutaDesignada(origen.isSigueRutaDesignada());
        predeterminado.setObservacion(origen.getObservacion());
        predeterminado.setOrigenTrazadoId(origen.getId());
        predeterminado.setPredeterminadoEn(Instant.now());
        predeterminado.setPredeterminadoPor(usuario);
        TrazadoAcopiador guardado = repository.save(predeterminado);

        List<TrazadoAcopiadorPunto> copiados = reemplazarPuntos(
                guardado,
                puntosOrigen.stream()
                        .map(p -> new TrazadoPuntoRequest(p.getSecuencia(), p.getLatitud(), p.getLongitud()))
                        .toList());

        auditoriaService.registrar(
                "Recorrido predeterminado del acopiador",
                ENTIDAD,
                nombreDe(acopiador) + " · " + origen.getRuta().getNombre() + " · copiado del "
                        + origen.getFecha() + " · " + copiados.size() + " puntos");
        return TrazadoAcopiadorResponse.from(guardado, copiados);
    }

    /** Quita el predeterminado: el acopiador vuelve a tener la geometria de la ruta como referencia. */
    @Transactional
    public void quitarPredeterminado(Long acopiadorId) {
        TrazadoAcopiador predeterminado = repository
                .findByAcopiador_IdAndTipo(acopiadorId, TipoTrazado.PREDETERMINADO)
                .orElseThrow(() -> NotFoundException.of("Recorrido predeterminado del acopiador", acopiadorId));
        String nombre = nombreDe(predeterminado.getAcopiador());
        eliminar(predeterminado);
        auditoriaService.registrar(
                "Recorrido predeterminado retirado",
                ENTIDAD,
                nombre + " · vuelve a usar la geometria de la ruta");
    }

    // --------------------------------------------------------------------------------- Auxiliares

    private Acopiador acopiadorDe(String username) {
        return acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> new BusinessRuleException(
                        "Tu usuario no esta vinculado a un acopiador activo; no se puede pintar un recorrido."));
    }

    /**
     * La ruta del acopiador ese dia: primero la asignacion de la fecha y, si no hay, su ruta habitual.
     * Es el mismo orden que ya usa la app en "Ruta del dia", asi que el trazado se cuelga de la misma
     * ruta que la pantalla le esta mostrando.
     */
    private Ruta rutaDelDia(Acopiador acopiador, LocalDate dia) {
        Optional<Ruta> asignada = asignacionRutaRepository
                .findByAcopiadorIdAndFechaAndActivoTrue(acopiador.getId(), dia)
                .stream()
                .map(AsignacionRuta::getRuta)
                .findFirst();
        Ruta ruta = asignada.orElseGet(acopiador::getRuta);
        if (ruta == null) {
            throw new BusinessRuleException(
                    "No tienes ruta asignada para el " + dia + " ni ruta habitual; pide a administracion que te asigne una.");
        }
        return ruta;
    }

    private List<TrazadoAcopiadorPunto> reemplazarPuntos(TrazadoAcopiador trazado, List<TrazadoPuntoRequest> puntos) {
        puntoRepository.deleteByTrazado_Id(trazado.getId());
        // Se renumera por posicion en vez de confiar en la secuencia que manda el cliente: el orden de
        // los vertices ES el recorrido, y dos puntos con la misma secuencia lo volverian ambiguo.
        List<TrazadoAcopiadorPunto> nuevos = new java.util.ArrayList<>();
        List<TrazadoPuntoRequest> ordenados = puntos.stream()
                .sorted(Comparator.comparing(TrazadoPuntoRequest::secuencia))
                .toList();
        for (int i = 0; i < ordenados.size(); i++) {
            TrazadoAcopiadorPunto punto = new TrazadoAcopiadorPunto();
            punto.setTrazado(trazado);
            punto.setSecuencia(i);
            punto.setLatitud(ordenados.get(i).latitud());
            punto.setLongitud(ordenados.get(i).longitud());
            nuevos.add(punto);
        }
        return puntoRepository.saveAll(nuevos);
    }

    private void eliminar(TrazadoAcopiador trazado) {
        puntoRepository.deleteByTrazado_Id(trazado.getId());
        repository.delete(trazado);
    }

    private TrazadoAcopiadorResponse conPuntos(TrazadoAcopiador trazado) {
        return trazado == null
                ? null
                : TrazadoAcopiadorResponse.from(
                        trazado, puntoRepository.findByTrazado_IdOrderBySecuenciaAsc(trazado.getId()));
    }

    private List<TrazadoAcopiadorResponse> conPuntosEnLote(List<TrazadoAcopiador> trazados) {
        if (trazados.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TrazadoAcopiadorPunto>> porTrazado = puntoRepository
                .findByTrazado_IdInOrderByTrazado_IdAscSecuenciaAsc(
                        trazados.stream().map(TrazadoAcopiador::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(p -> p.getTrazado().getId()));
        return trazados.stream()
                .map(t -> TrazadoAcopiadorResponse.from(t, porTrazado.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    private static String nombreDe(Acopiador a) {
        return a.getNombres() + " " + a.getApellidos();
    }
}
