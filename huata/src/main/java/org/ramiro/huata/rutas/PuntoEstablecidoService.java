package org.ramiro.huata.rutas;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorUbicacionService;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Puntos establecidos": puntos de referencia que administracion crea a mano en el mapa (nombre +
 * latitud/longitud), por ruta, con acopiador y proveedor asociados opcionales. Se leen desde la app
 * del acopiador (por su ruta del dia o por asignacion directa) igual que ya lee
 * {@code RutaPunto}/{@code ProductorUbicacion}; ver {@link PuntoEstablecido}.
 *
 * <p>Los cambios se publican después del commit. Desactivar permite reactivar; eliminar es una
 * baja lógica definitiva que conserva la trazabilidad.
 */
@Service
@RequiredArgsConstructor
public class PuntoEstablecidoService {

    private static final String ENTIDAD = "Punto establecido";
    private static final java.time.ZoneId ZONA_HUATA = java.time.ZoneId.of("America/Lima");

    private final PuntoEstablecidoRepository repository;
    private final RutaRepository rutaRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final ProductorRepository productorRepository;
    private final ProductorUbicacionService ubicacionService;
    private final AsignacionRutaRepository asignacionRutaRepository;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher eventos;

    @Transactional(readOnly = true)
    public List<PuntoEstablecidoResponse> listar(Long rutaId) {
        List<PuntoEstablecido> puntos = rutaId != null
                ? repository.findByRuta_IdAndActivoTrueOrderByNombreAsc(rutaId)
                : repository.findByActivoTrueOrderByNombreAsc();
        return puntos.stream().map(PuntoEstablecidoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PuntoEstablecidoResponse> listarParaGestion(Long rutaId) {
        return (rutaId == null ? repository.findByEliminadoFalseOrderByNombreAsc()
                : repository.findByRuta_IdAndEliminadoFalseOrderByNombreAsc(rutaId))
                .stream().map(PuntoEstablecidoResponse::from).toList();
    }

    public PuntoEstablecido obtener(Long id) {
        return repository.findById(id).filter(PuntoEstablecido::isActivo).orElseThrow(() -> NotFoundException.of(ENTIDAD, id));
    }

    @Transactional
    public PuntoEstablecido crear(PuntoEstablecidoRequest req) {
        PuntoEstablecido punto = new PuntoEstablecido();
        aplicarDatos(punto, req);
        PuntoEstablecido guardado = repository.saveAndFlush(punto);
        auditoriaService.registrar("Punto establecido creado", ENTIDAD, describir(guardado));
        publicar("PUNTO_ACTIVADO", guardado, guardado.getRuta().getId(), usuarioDirecto(guardado));
        return guardado;
    }

    @Transactional
    public PuntoEstablecido actualizar(Long id, PuntoEstablecidoRequest req) {
        PuntoEstablecido punto = obtener(id);
        Long rutaAnterior = punto.getRuta().getId();
        String usuarioAnterior = usuarioDirecto(punto);
        aplicarDatos(punto, req);
        PuntoEstablecido guardado = repository.saveAndFlush(punto);
        auditoriaService.registrar("Punto establecido editado", ENTIDAD, describir(guardado));
        publicar("PUNTO_ACTUALIZADO", guardado, rutaAnterior, usuarioAnterior);
        return guardado;
    }

    @Transactional
    public void desactivar(Long id) {
        PuntoEstablecido punto = obtener(id);
        punto.setActivo(false);
        repository.saveAndFlush(punto);
        auditoriaService.registrar("Punto establecido desactivado", ENTIDAD, describir(punto));
        publicar("PUNTO_DESACTIVADO", punto, punto.getRuta().getId(), usuarioDirecto(punto));
    }

    private static void exigirZona(Ruta ruta) {
        if (ruta.getZona() == null || ruta.getZona().isBlank()) {
            throw new BusinessRuleException("Asigna una zona a la ruta antes de agregar un proveedor.");
        }
    }

    @Transactional
    public PuntoEstablecido cambiarEstado(Long id, boolean activo) {
        PuntoEstablecido punto = repository.findById(id).filter(p -> !p.isEliminado())
                .orElseThrow(() -> NotFoundException.of(ENTIDAD, id));
        if (punto.isActivo() == activo) return punto;
        punto.setActivo(activo);
        repository.saveAndFlush(punto);
        auditoriaService.registrar(activo ? "Punto establecido activado" : "Punto establecido desactivado",
                ENTIDAD, describir(punto));
        publicar(activo ? "PUNTO_ACTIVADO" : "PUNTO_DESACTIVADO", punto,
                punto.getRuta().getId(), usuarioDirecto(punto));
        return punto;
    }

    @Transactional
    public void eliminar(Long id) {
        PuntoEstablecido punto = repository.findById(id).filter(p -> !p.isEliminado())
                .orElseThrow(() -> NotFoundException.of(ENTIDAD, id));
        punto.setActivo(false);
        punto.setEliminado(true);
        repository.saveAndFlush(punto);
        auditoriaService.registrar("Punto establecido eliminado", ENTIDAD, describir(punto));
        publicar("PUNTO_ELIMINADO", punto, punto.getRuta().getId(), usuarioDirecto(punto));
    }

    private static String usuarioDirecto(PuntoEstablecido punto) {
        return punto.getAcopiador() == null || punto.getAcopiador().getUsuario() == null
                ? null : punto.getAcopiador().getUsuario().getUsername();
    }

    private void publicar(String tipo, PuntoEstablecido punto, Long rutaAnterior, String usuarioAnterior) {
        // Snapshot inmutable dentro de la transacción; no acceder a entidades lazy después del commit.
        eventos.publishEvent(new PuntoCambiado(tipo, PuntoEstablecidoResponse.from(punto),
                rutaAnterior, usuarioAnterior, usuarioDirecto(punto)));
    }

    private void aplicarDatos(PuntoEstablecido punto, PuntoEstablecidoRequest req) {
        Ruta ruta = rutaRepository.findById(req.rutaId()).filter(Ruta::isActivo)
                .orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
        punto.setNombre(req.nombre().trim());
        // Doble validacion (sección 35): la anotacion @DecimalMin/@DecimalMax del DTO ya lo rechaza en
        // el controlador, pero el servicio no confia solo en eso -- igual que el resto del proyecto.
        if (req.latitud() == null || req.longitud() == null || !Double.isFinite(req.latitud())
                || !Double.isFinite(req.longitud()) || Math.abs(req.latitud()) > 90 || Math.abs(req.longitud()) > 180) {
            throw new BusinessRuleException("Coordenada fuera de rango (latitud -90 a 90, longitud -180 a 180).");
        }
        punto.setLatitud(req.latitud());
        punto.setLongitud(req.longitud());
        punto.setRuta(ruta);
        punto.setObservacion(req.observacion());

        if (req.acopiadorId() != null) {
            Acopiador acopiador = acopiadorRepository.findById(req.acopiadorId()).filter(Acopiador::isActivo)
                    .orElseThrow(() -> NotFoundException.of("Acopiador", req.acopiadorId()));
            punto.setAcopiador(acopiador);
        } else {
            punto.setAcopiador(null);
        }

        if (req.proveedorId() != null) {
            Productor proveedor = productorRepository.findById(req.proveedorId()).filter(Productor::isActivo)
                    .orElseThrow(() -> NotFoundException.of("Productor", req.proveedorId()));
            exigirZona(ruta);
            if (proveedor.getRuta() == null || !proveedor.getRuta().getId().equals(ruta.getId())) {
                throw new BusinessRuleException("El proveedor debe pertenecer a la ruta del punto. Asígnalo a esa ruta primero.");
            }
            punto.setProveedor(proveedor);
            ubicacionService.registrarPuntoDeRecojo(proveedor.getId(), req.latitud(), req.longitud());
        } else {
            punto.setProveedor(null);
        }
    }

    // ---------------------------------------------------------------------------- App movil (el acopiador)

    /**
     * Puntos que le tocan a este acopiador: los de su ruta del dia (asignacion de la fecha, o su ruta
     * habitual si no hay asignacion -- mismo orden que {@code TrazadoAcopiadorService.rutaDelDia}) mas
     * los que se le asignaron directamente por {@code acopiadorId}, sin duplicar si coinciden.
     */
    @Transactional(readOnly = true)
    public List<PuntoEstablecidoResponse> deMiAcopiador(String username) {
        return puntosDeAcopiador(username, false);
    }

    @Transactional(readOnly = true)
    public SnapshotPuntosResponse snapshotDeMiAcopiador(String username) {
        var todos = puntosDeAcopiador(username, true);
        return new SnapshotPuntosResponse(todos.stream().filter(PuntoEstablecidoResponse::activo).toList(),
                todos.stream().filter(p -> !p.activo()).toList());
    }

    private List<PuntoEstablecidoResponse> puntosDeAcopiador(String username, boolean incluirBajas) {
        Acopiador acopiador = acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> new BusinessRuleException(
                        "Tu usuario no esta vinculado a un acopiador activo; no se pueden consultar sus puntos."));

        java.util.LinkedHashMap<Long, PuntoEstablecido> resultado = new java.util.LinkedHashMap<>();

        Ruta rutaDelDia = asignacionRutaRepository
                .findByAcopiadorIdAndFechaAndActivoTrue(acopiador.getId(), LocalDate.now(ZONA_HUATA))
                .stream()
                .map(AsignacionRuta::getRuta)
                .findFirst()
                .orElse(acopiador.getRuta());
        if (rutaDelDia != null) {
            (incluirBajas ? repository.findByRuta_IdOrderByNombreAsc(rutaDelDia.getId())
                    : repository.findByRuta_IdAndActivoTrueOrderByNombreAsc(rutaDelDia.getId()))
                    .forEach(p -> resultado.put(p.getId(), p));
        }
        (incluirBajas ? repository.findByAcopiador_IdOrderByNombreAsc(acopiador.getId())
                : repository.findByAcopiador_IdAndActivoTrueOrderByNombreAsc(acopiador.getId()))
                .forEach(p -> resultado.put(p.getId(), p));

        return resultado.values().stream().map(PuntoEstablecidoResponse::from).toList();
    }

    private static String describir(PuntoEstablecido p) {
        return p.getNombre() + " · ruta " + p.getRuta().getNombre()
                + (p.getAcopiador() != null ? " · acopiador " + p.getAcopiador().getNombres() + " " + p.getAcopiador().getApellidos() : "")
                + (p.getProveedor() != null ? " · proveedor " + p.getProveedor().getCodigo() : "");
    }
}
