package org.ramiro.huata.productores;

import java.time.LocalDate;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Solicitud de cambio de zona del proveedor (2.5): la crea el proveedor, la decide administración. */
@Service
@RequiredArgsConstructor
public class SolicitudCambioZonaService {

    private final SolicitudCambioZonaRepository repository;
    private final ProductorRepository productorRepository;
    private final RutaRepository rutaRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final AuditoriaService auditoriaService;
    /** Solo demostraciones: al aprobar, su vivienda simulada pasa a la zona nueva. */
    private final UbicacionSimuladaService ubicacionSimulada;

    private static final String ENTIDAD = "Proveedores";

    public List<SolicitudCambioZona> listar(EstadoSolicitudZona estado) {
        return estado != null ? repository.findByEstadoOrderByFechaSolicitudDesc(estado) : repository.findAllByOrderByFechaSolicitudDesc();
    }

    public List<SolicitudCambioZona> deProductor(Long productorId) {
        return repository.findByProductor_IdOrderByFechaSolicitudDesc(productorId);
    }

    /**
     * Catálogo de zonas a las que se puede pedir pasar: las de las rutas activas, porque aprobar
     * exige una ruta en esa zona. Es la única fuente para la app, la web y el IVR, así todos ofrecen
     * las mismas zonas y con el mismo nombre.
     */
    public List<String> zonasDisponibles() {
        LinkedHashSet<String> zonas = new LinkedHashSet<>();
        rutaRepository.findByActivoTrueOrderByZonaAscNombreAsc().forEach(r -> {
            if (r.getZona() != null && !r.getZona().isBlank()) zonas.add(r.getZona().trim());
        });
        return List.copyOf(zonas);
    }

    /** Las zonas disponibles menos la que el proveedor ya tiene: es lo que la app le deja elegir. */
    public List<String> zonasSolicitables(Long productorId) {
        Productor productor = productorActivo(productorId);
        String actual = productor.getZonaPrincipal();
        return zonasDisponibles().stream()
                .filter(z -> actual == null || !normalizar(z).equals(normalizar(actual)))
                .toList();
    }

    @Transactional
    public SolicitudCambioZona crear(Long productorId, SolicitudCambioZonaRequest req) {
        Productor productor = productorActivo(productorId);
        String zonaSolicitada = req.zonaSolicitada().trim();
        if (zonasDisponibles().stream().noneMatch(z -> coincideZona(z, zonaSolicitada))) {
            throw new BusinessRuleException("La zona " + zonaSolicitada + " no está disponible: elige una de la lista.");
        }
        if (productor.getZonaPrincipal() != null
                && normalizar(productor.getZonaPrincipal()).equals(normalizar(zonaSolicitada))) {
            throw new BusinessRuleException("El proveedor ya pertenece a la zona " + zonaSolicitada + ".");
        }
        // Misma regla que el IVR: una sola solicitud esperando respuesta por proveedor.
        boolean hayPendiente = deProductor(productorId).stream()
                .anyMatch(s -> s.getEstado() == EstadoSolicitudZona.PENDIENTE);
        if (hayPendiente) {
            throw new BusinessRuleException("Ya hay una solicitud de cambio de zona esperando respuesta.");
        }

        SolicitudCambioZona solicitud = new SolicitudCambioZona();
        solicitud.setProductor(productor);
        solicitud.setZonaActual(productor.getZonaPrincipal());
        solicitud.setZonaSolicitada(zonaSolicitada);
        solicitud.setMotivo(req.motivo());
        solicitud.setEstado(EstadoSolicitudZona.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDate.now());
        SolicitudCambioZona guardada = repository.save(solicitud);

        auditoriaService.registrar(
                "Solicitud de cambio de zona",
                ENTIDAD,
                productor.getCodigo() + " · " + solicitud.getZonaActual() + " -> " + solicitud.getZonaSolicitada());
        return guardada;
    }

    /**
     * La misma solicitud que crea la app, pedida por telefono (IVR). Va por aqui para que la regla y
     * la bitacora sean una sola; el motivo deja constancia del canal para quien la revise en la web.
     */
    @Transactional
    public SolicitudCambioZona crearPorTelefono(Long productorId, String zonaSolicitada) {
        return crear(productorId, new SolicitudCambioZonaRequest(zonaSolicitada, "Pedido por teléfono (IVR)"));
    }

    /**
     * Aprobar actualiza {@code Productor.zonaPrincipal}; rechazar solo deja constancia de la
     * decision, sin tocar la zona vigente.
     */
    @Transactional
    public SolicitudCambioZona decidir(Long id, SolicitudCambioZonaDecisionRequest req) {
        SolicitudCambioZona solicitud = repository.findById(id).orElseThrow(() -> NotFoundException.of("Solicitud", id));
        if (solicitud.getEstado() != EstadoSolicitudZona.PENDIENTE) {
            throw new BusinessRuleException("La solicitud ya fue decidida: está " + solicitud.getEstado() + ".");
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        solicitud.setAprobador(auth != null ? auth.getName() : "sistema");
        solicitud.setFechaDecision(LocalDate.now());
        solicitud.setObservacionDecision(req.observacion());

        if (req.aprobar()) {
            var productor = solicitud.getProductor();
            var ruta = rutaRepository.findByActivoTrueOrderByZonaAscNombreAsc().stream()
                    .filter(r -> coincideZona(r.getZona(), solicitud.getZonaSolicitada()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "No se puede aprobar: primero crea una ruta activa para la zona "
                                    + solicitud.getZonaSolicitada() + "."));
            solicitud.setEstado(EstadoSolicitudZona.APROBADA);
            String zonaAnterior = productor.getZonaPrincipal();
            productor.setRuta(ruta);
            productor.setZonaPrincipal(ruta.getZona().trim());
            productor.setAcopiadorResponsable(acopiadorRepository
                    .findByRuta_IdAndActivoTrueOrderByNombresAsc(ruta.getId())
                    .stream()
                    .findFirst()
                    .orElse(null));
            productorRepository.save(productor);
            ubicacionSimulada.alCambiarDeZona(productor, zonaAnterior);
        } else {
            solicitud.setEstado(EstadoSolicitudZona.RECHAZADA);
        }
        SolicitudCambioZona guardada = repository.save(solicitud);

        auditoriaService.registrar(
                req.aprobar() ? "Cambio de zona aprobado" : "Cambio de zona rechazado",
                ENTIDAD,
                solicitud.getProductor().getCodigo() + " · " + solicitud.getZonaSolicitada());
        return guardada;
    }

    private Productor productorActivo(Long productorId) {
        return productorRepository
                .findById(productorId)
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", productorId));
    }

    /** Acepta tanto la zona completa como una parte declarada con "/" en una ruta compuesta. */
    private static boolean coincideZona(String zonaRuta, String zonaSolicitada) {
        if (zonaRuta == null || zonaSolicitada == null) return false;
        String solicitada = normalizar(zonaSolicitada);
        return normalizar(zonaRuta).equals(solicitada)
                || Arrays.stream(zonaRuta.split("/"))
                        .map(SolicitudCambioZonaService::normalizar)
                        .anyMatch(solicitada::equals);
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
