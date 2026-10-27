package org.ramiro.huata.recojo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compara litros recolectados (calculado desde {@link Recojo}) contra litros recibidos en planta,
 * por ruta y fecha (2.2.4, RF-54). No acusa perdida ni responsable por si sola: solo deja el numero
 * y quien lo registro, la investigacion es un paso humano posterior.
 */
@Service
@RequiredArgsConstructor
public class ConciliacionRutaService {

    /**
     * La jornada se corta a medianoche de Lima, igual que en {@link RecojoService} y en los reportes
     * del acopiador. Antes se cortaba en UTC: un acopio de las 19:30 (00:30 UTC del dia siguiente)
     * caia en la conciliacion del dia siguiente y no aparecia nunca en la del dia en que ocurrio.
     */
    private static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");

    private final ConciliacionRutaRepository repository;
    private final RutaRepository rutaRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final RecojoRepository recojoRepository;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher eventos;
    private final RecepcionLecheService recepcionLeche;
    private final UsuarioRepository usuarioRepository;

    private static final String ENTIDAD = "Acopio";

    @Transactional(readOnly = true)
    public List<ConciliacionRutaResponse> deFecha(LocalDate fecha) {
        List<Recojo> recojosDelDia = recojosDe(fecha);
        // Los totales de planta del dia se traen de una vez: una consulta por ruta serian N mas.
        List<ConciliacionRuta> registradas = repository.findByFechaOrderByRuta_Nombre(fecha);
        Calidad calidad = calidadDe(fecha);
        // Las rutas de baja se descartan en SQL y no en memoria: con un catalogo que crece, traerlas
        // todas para tirar la mitad es trabajo que la base ya sabe no hacer.
        return rutaRepository.findByActivoTrueOrderByZonaAscNombreAsc().stream()
                .sorted(Comparator.comparing(Ruta::getNombre, Comparator.nullsLast(String::compareTo)))
                .filter(ruta -> seRecibe(deLaRuta(recojosDelDia, ruta), registradaDe(registradas, ruta)))
                .map(ruta -> aRespuesta(fecha, ruta, deLaRuta(recojosDelDia, ruta), registradaDe(registradas, ruta), calidad))
                .toList();
    }

    /**
     * Si la ruta tiene algo que recibir en planta: visitas del acopiador ese dia (aunque sean rojas, 0 L:
     * explican una recoleccion baja) o un total ya anotado.
     *
     * <p><b>Las entregas directas no cuentan</b> (correccion del 2026-10-26). La leche que el proveedor
     * lleva el mismo a planta ya esta en planta: entra sola al almacen ({@link
     * RecepcionLecheService#sincronizarEntregaDirecta}) y el cierre del dia ya la da por recibida. Antes
     * una ruta con solo entregas directas aparecia aqui "por recibir", pidiendo anotar una llegada que
     * no existe: ningun camion la trajo.
     */
    private static boolean seRecibe(List<Recojo> deLaRuta, ConciliacionRuta registrada) {
        return registrada != null || !delCamion(deLaRuta).isEmpty();
    }

    /** Lo que viajo en la movilidad del acopiador (sin las entregas directas en planta). */
    private static List<Recojo> delCamion(List<Recojo> deLaRuta) {
        return deLaRuta.stream().filter(r -> r.getFuente() != FuenteRecojo.PLANTA).toList();
    }

    /**
     * Una sola fila, recalculada. Es lo que viaja por el WebSocket tras cada escritura, aunque la ruta
     * ya no tenga nada que recibir (se anulo su ultimo recojo, o solo hubo una entrega directa): con
     * {@code requiereRecepcion = false} la pantalla la quita en vez de mostrarla "por recibir".
     */
    @Transactional(readOnly = true)
    public Optional<ConciliacionRutaResponse> deFechaYRuta(LocalDate fecha, Long rutaId) {
        return rutaRepository
                .findById(rutaId)
                .filter(Ruta::isActivo)
                .map(ruta -> fila(fecha, ruta, recojosDe(fecha)));
    }

    @Transactional
    public ConciliacionRutaResponse registrar(ConciliacionRutaRequest req) {
        Ruta ruta = rutaRepository
                .findById(req.rutaId())
                .filter(Ruta::isActivo)
                .orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
        Acopiador acopiador = req.acopiadorId() != null
                ? acopiadorRepository
                        .findById(req.acopiadorId())
                        .orElseThrow(() -> NotFoundException.of("Acopiador", req.acopiadorId()))
                : null;

        ConciliacionRuta conciliacion =
                repository.findByFechaAndRuta_Id(req.fecha(), req.rutaId()).orElseGet(ConciliacionRuta::new);
        conciliacion.setFecha(req.fecha());
        conciliacion.setRuta(ruta);
        conciliacion.setAcopiador(acopiador);
        conciliacion.setLitrosRecibidosPlanta(req.litrosRecibidosPlanta());
        conciliacion.setResponsable(quienRecibe());
        conciliacion.setObservacion(req.observacion() == null || req.observacion().isBlank() ? null : req.observacion().trim());
        repository.save(conciliacion);

        // V31/V33: entra al almacen lo APTO de lo que planta recibio (decision del cliente). La
        // diferencia con lo recogido queda como merma de transporte en esta misma conciliacion.
        recepcionLeche.sincronizarRuta(req.fecha(), ruta.getId());

        ConciliacionRutaResponse fila = fila(req.fecha(), ruta, recojosDe(req.fecha()));
        auditoriaService.registrar(
                "Conciliación de ruta",
                ENTIDAD,
                ruta.getNombre() + " · " + req.fecha() + " · recolectado " + fila.totalRecolectado()
                        + " L · planta " + req.litrosRecibidosPlanta() + " L · diferencia " + fila.diferencia()
                        + " L");

        // Despues del commit: el total de planta recien registrado tiene que llegar a las pantallas
        // ya abiertas sin que nadie recargue (ver AcopioTiempoRealPublisher).
        eventos.publishEvent(new AcopioCambioEvent(req.fecha(), ruta.getId(), EventoAcopioTiempoReal.PLANTA));
        return fila;
    }

    private List<Recojo> recojosDe(LocalDate fecha) {
        return recojoRepository.delDiaConDetalle(
                fecha.atStartOfDay(ZONA_HUATA).toInstant(),
                fecha.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant(),
                EstadoRecojo.CANCELLED);
    }

    private ConciliacionRutaResponse fila(LocalDate fecha, Ruta ruta, List<Recojo> recojosDelDia) {
        return aRespuesta(
                fecha,
                ruta,
                deLaRuta(recojosDelDia, ruta),
                repository.findByFechaAndRuta_Id(fecha, ruta.getId()).orElse(null),
                calidadDe(fecha));
    }

    /** Resultado de calidad de las recepciones del dia y reglas vigentes, leidos una vez por pedido. */
    private record Calidad(
            java.util.Map<Long, org.ramiro.huata.calidad.ResultadoCalidad> porRecojo,
            org.ramiro.huata.parametros.ReglasLecheService.ReglasLeche reglas) {}

    private Calidad calidadDe(LocalDate fecha) {
        return new Calidad(recepcionLeche.calidadDelDia(fecha), recepcionLeche.reglas());
    }

    private static List<Recojo> deLaRuta(List<Recojo> recojos, Ruta ruta) {
        return recojos.stream()
                .filter(r -> r.getRuta() != null && r.getRuta().getId().equals(ruta.getId()))
                .toList();
    }

    private static ConciliacionRuta registradaDe(List<ConciliacionRuta> registradas, Ruta ruta) {
        return registradas.stream()
                .filter(c -> c.getRuta().getId().equals(ruta.getId()))
                .findFirst()
                .orElse(null);
    }

    private static ConciliacionRutaResponse aRespuesta(
            LocalDate fecha, Ruta ruta, List<Recojo> deLaRuta, ConciliacionRuta registrada, Calidad calidad) {
        double enRuta = litrosDe(deLaRuta, FuenteRecojo.RUTA);
        double enPlanta = litrosDe(deLaRuta, FuenteRecojo.PLANTA);
        Double litrosPlanta = registrada != null ? registrada.getLitrosRecibidosPlanta() : null;
        // La diferencia se mide contra lo que viajo en la movilidad: una entrega directa en planta no
        // paso por el camion, contarla como recolectada en ruta inventaria un faltante.
        Double diferencia = litrosPlanta != null ? enRuta - litrosPlanta : null;
        // El detalle y los conteos son de lo que trajo el camion: las entregas directas tienen su propia
        // pantalla ("Entregas directas") y aqui solo se informa su total.
        List<Recojo> camion = delCamion(deLaRuta);
        Acopiador acopiador = acopiadorDe(camion, registrada);
        // Misma cuenta que usa el almacen (RecepcionLecheService): la pantalla no puede decir una cosa
        // y el inventario otra.
        double noAptos = RecepcionLecheService.litrosNoAptos(deLaRuta, calidad.porRecojo(), calidad.reglas());
        Double alInventario = litrosPlanta != null ? Math.max(0, litrosPlanta - noAptos) : null;

        return new ConciliacionRutaResponse(
                registrada != null ? registrada.getId() : null,
                registrada != null ? registrada.getFecha() : fecha,
                ruta.getId(),
                ruta.getNombre(),
                ruta.getZona(),
                acopiador != null ? acopiador.getId() : null,
                acopiador != null ? nombreDe(acopiador) : null,
                enRuta + enPlanta,
                enRuta,
                enPlanta,
                (int) camion.stream().filter(r -> r.getEstadoVisita() != EstadoVisita.NO_ENTREGADO).count(),
                (int) camion.stream().filter(r -> r.getEstadoVisita() == EstadoVisita.NO_ENTREGADO).count(),
                camion.stream().map(Recojo::getFechaHora).max(Instant::compareTo).orElse(null),
                litrosPlanta,
                diferencia,
                noAptos,
                alInventario,
                registrada != null ? registrada.getResponsable() : null,
                registrada != null ? registrada.getObservacion() : null,
                camion.stream().map(r -> aVisita(r, calidad.porRecojo().get(r.getId()))).toList(),
                seRecibe(deLaRuta, registrada));
    }

    private static double litrosDe(List<Recojo> recojos, FuenteRecojo fuente) {
        return recojos.stream()
                // Un recojo anterior a que existiera la columna no declara fuente; cuenta como ruta,
                // que es de donde venia toda la leche registrada por este canal.
                .filter(r -> fuente == FuenteRecojo.RUTA
                        ? r.getFuente() != FuenteRecojo.PLANTA
                        : r.getFuente() == FuenteRecojo.PLANTA)
                .mapToDouble(Recojo::getLitros)
                .sum();
    }

    /**
     * Quien recorrio la ruta. Se prefiere el acopiador de los recojos del dia sobre el que anoto
     * planta: el primero es un hecho del campo, el segundo un dato que se escribe a mano y puede
     * quedar vacio.
     */
    private static Acopiador acopiadorDe(List<Recojo> deLaRuta, ConciliacionRuta registrada) {
        return deLaRuta.stream()
                .map(Recojo::getAcopiador)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> registrada != null ? registrada.getAcopiador() : null);
    }

    private static ConciliacionVisitaResponse aVisita(Recojo r, org.ramiro.huata.calidad.ResultadoCalidad calidad) {
        Productor productor = r.getProductor();
        Acopiador acopiador = r.getAcopiador();
        return new ConciliacionVisitaResponse(
                r.getId(),
                productor != null ? productor.getId() : null,
                productor != null ? productor.getCodigo() : null,
                productor != null ? productor.getNombres() + " " + productor.getApellidos() : null,
                r.getFechaHora(),
                r.getLitros(),
                r.getFuente(),
                r.getEstadoVisita(),
                r.getMotivoNoEntrega(),
                acopiador != null ? nombreDe(acopiador) : null,
                calidad);
    }

    private static String nombreDe(Acopiador a) {
        return a.getNombres() + " " + a.getApellidos();
    }

    /**
     * Quien recibe en planta es la cuenta que registra, no un texto escrito a mano: asi la bitacora
     * dice quien opero de verdad y nadie puede anotar la recepcion a nombre de otro.
     */
    private String quienRecibe() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        return usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(auth.getName())
                .map(Usuario::getNombreCompleto)
                .orElse(auth.getName());
    }
}
