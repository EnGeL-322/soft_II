package org.ramiro.huata.mensajeria;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.ramiro.huata.usuarios.Usuarios;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MensajeriaService {
    private final ConversacionProveedorRepository conversaciones;
    private final MensajeProveedorRepository mensajes;
    private final ProductorRepository productores;
    private final UsuarioRepository usuarios;
    private final ApplicationEventPublisher eventos;

    /** Tope de mensajes por sincronizacion: un telefono que estuvo semanas sin senal pide por tandas. */
    static final int MAXIMO_POR_SINCRONIZACION = 500;

    /**
     * Quien esta del otro lado del canal en vivo y que puede ver: la planta (Administracion o
     * Secretaria, bandeja compartida) ve todas las conversaciones; el proveedor, solo la suya. Son las
     * mismas reglas que aplican los endpoints REST de este servicio.
     */
    public record ContextoMensajeria(Long usuarioId, String username, boolean planta, Long productorId) {
        boolean puedeVer(Long productorIdConversacion) {
            return planta || productorIdConversacion.equals(productorId);
        }
    }

    /** Un mensaje nuevo listo para difundir: como lo ve el proveedor y como lo ve la planta. */
    public record MensajePublicable(Long productorId, MensajeriaDtos.MensajeResponse vistaProveedor,
            MensajeriaDtos.MensajeResponse vistaPlanta) {}

    @Transactional(readOnly = true)
    public ContextoMensajeria contexto(String username) {
        Usuario actor = usuario(username);
        if (esPlanta(actor)) {
            return new ContextoMensajeria(actor.getId(), actor.getUsername(), true, null);
        }
        Productor productor = productorDelActor(actor);
        return new ContextoMensajeria(actor.getId(), actor.getUsername(), false, productor.getId());
    }

    @Transactional(readOnly = true)
    public List<MensajeriaDtos.ConversacionResponse> listar(String username, String q) {
        Usuario actor = usuario(username);
        exigirPersonalPlanta(actor);
        String filtro = q == null ? "" : q.trim().toLowerCase();
        Map<Long, ConversacionProveedor> porProductor = conversaciones.findAllByOrderByUpdatedAtDesc().stream()
                .collect(Collectors.toMap(c -> c.getProductor().getId(), Function.identity()));
        return productores.findByActivoTrueOrderByNombresAscApellidosAsc().stream()
                .filter(p -> coincideConFiltro(p, filtro))
                .map(p -> {
                    ConversacionProveedor conversacion = porProductor.get(p.getId());
                    return conversacion == null ? resumenSinConversacion(p) : resumen(conversacion, actor, true);
                })
                .toList();
    }

    /**
     * Lo que ve el proveedor en su pantalla de inicio: cuantos mensajes nuevos tiene y los ultimos.
     * <b>No marca nada como leido</b> (a diferencia de {@link #miConversacion}): mostrar un resumen no
     * es abrir la conversacion, y si lo marcara el contador de "nuevos" siempre diria cero.
     * Tampoco crea la conversacion si todavia no existe.
     */
    @Transactional(readOnly = true)
    public MensajeriaDtos.ConversacionDetalleResponse miBandeja(String username) {
        Usuario actor = usuario(username);
        Productor productor = productorDelActor(actor);
        return conversaciones.findByProductor_Id(productor.getId())
                .map(c -> {
                    List<MensajeProveedor> historial = mensajes.findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(c.getId());
                    List<MensajeriaDtos.MensajeResponse> recientes = historial
                            .subList(Math.max(0, historial.size() - MENSAJES_EN_BANDEJA), historial.size())
                            .stream()
                            .map(m -> mensaje(m, actor))
                            .toList();
                    return new MensajeriaDtos.ConversacionDetalleResponse(resumen(c, actor, false), recientes);
                })
                .orElseGet(() -> new MensajeriaDtos.ConversacionDetalleResponse(resumenSinConversacion(productor), List.of()));
    }

    /** Cuantos mensajes recientes trae la bandeja de la pantalla de inicio del proveedor. */
    private static final int MENSAJES_EN_BANDEJA = 3;

    @Transactional
    public MensajeriaDtos.ConversacionDetalleResponse miConversacion(String username) {
        Usuario actor = usuario(username);
        Productor productor = productorDelActor(actor);
        ConversacionProveedor conversacion = conversacionDe(productor);
        marcarComoLeido(conversacion, actor, false);
        return detalle(conversacion, actor, false);
    }

    @Transactional
    public MensajeriaDtos.ConversacionDetalleResponse conversacionDeProveedor(Long productorId, String username) {
        Usuario actor = usuario(username);
        exigirPersonalPlanta(actor);
        ConversacionProveedor conversacion = conversacionDe(productor(productorId));
        marcarComoLeido(conversacion, actor, true);
        return detalle(conversacion, actor, true);
    }

    @Transactional
    public MensajeriaDtos.MensajeResponse enviarAProveedor(Long productorId, MensajeriaDtos.EnviarMensajeRequest req, String username) {
        Usuario actor = usuario(username);
        exigirEnvioPlanta(actor);
        return enviar(conversacionDe(productor(productorId)), actor, req.cuerpo(), req.clientUuid(), true);
    }

    @Transactional
    public MensajeriaDtos.AvisoEnviadoResponse enviarAviso(MensajeriaDtos.EnviarAvisoRequest req, String username) {
        Usuario actor = usuario(username);
        exigirEnvioPlanta(actor);
        String destino = req.destino() == null ? "TODOS" : req.destino().trim().toUpperCase();
        List<Productor> activos = productores.findByActivoTrueOrderByNombresAscApellidosAsc();
        List<Productor> destinatarios = switch (destino) {
            case "TODOS" -> activos;
            case "ZONA" -> {
                if (req.zona() == null || req.zona().isBlank()) throw new BusinessRuleException("Selecciona una zona");
                yield activos.stream().filter(p -> req.zona().trim().equalsIgnoreCase(p.getZonaPrincipal())).toList();
            }
            case "SELECCION" -> {
                Set<Long> ids = req.productorIds() == null ? Set.of() : Set.copyOf(req.productorIds());
                if (ids.isEmpty()) throw new BusinessRuleException("Selecciona al menos un proveedor");
                yield activos.stream().filter(p -> ids.contains(p.getId())).toList();
            }
            default -> throw new BusinessRuleException("Destino de aviso no válido");
        };
        if (destinatarios.isEmpty()) throw new BusinessRuleException("No hay proveedores activos para este aviso");
        destinatarios.forEach(p -> enviar(conversacionDe(p), actor, req.cuerpo(), null, true));
        return new MensajeriaDtos.AvisoEnviadoResponse(destinatarios.size());
    }

    @Transactional
    public MensajeriaDtos.MensajeResponse enviarComoProveedor(MensajeriaDtos.EnviarMensajeRequest req, String username) {
        Usuario actor = usuario(username);
        return enviar(conversacionDe(productorDelActor(actor)), actor, req.cuerpo(), req.clientUuid(), false);
    }

    // ------------------------------------------------------------------ Canal en vivo (WebSocket)

    /**
     * Lo que el cliente se perdio mientras estaba desconectado: todo lo posterior al ultimo id que
     * recibio, en orden. Ordenar y cortar por id (no por fecha) es lo que evita duplicados y huecos:
     * dos mensajes del mismo milisegundo tienen ids distintos.
     */
    @Transactional(readOnly = true)
    public List<MensajeriaDtos.MensajeResponse> sincronizar(ContextoMensajeria ctx, Long desdeId) {
        Usuario actor = usuario(ctx.username());
        long desde = desdeId == null ? 0L : desdeId;
        PageRequest tope = PageRequest.of(0, MAXIMO_POR_SINCRONIZACION);
        List<MensajeProveedor> nuevos = ctx.planta()
                ? mensajes.findByIdGreaterThanAndActivoTrueOrderByIdAsc(desde, tope)
                : mensajes.findByConversacion_Productor_IdAndIdGreaterThanAndActivoTrueOrderByIdAsc(ctx.productorId(), desde, tope);
        return nuevos.stream().map(m -> mensaje(m, actor)).toList();
    }

    /**
     * El cliente confirma que los mensajes llegaron a su dispositivo (doble check gris). Solo cuentan
     * los que escribio el otro lado y los de conversaciones que esa cuenta puede ver: un id ajeno se
     * ignora sin error, porque el canal no tiene a quien responderle y no debe revelar si existe.
     */
    @Transactional
    public List<Long> marcarEntregados(ContextoMensajeria ctx, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Instant ahora = Instant.now();
        // Un acuse de la planta puede traer mensajes de varias conversaciones: un aviso por cada una,
        // porque cada aviso va solo a quien puede ver esa conversacion.
        Map<Long, List<Long>> porConversacion = new LinkedHashMap<>();
        for (MensajeProveedor m : mensajes.findAllById(ids)) {
            Long deConversacion = m.getConversacion().getProductor().getId();
            boolean delOtroLado = ctx.planta() == m.esDelProveedor();
            if (!m.isActivo() || !ctx.puedeVer(deConversacion) || !delOtroLado) continue;
            if (ctx.planta() && m.getEntregadoAPlantaAt() == null) {
                m.setEntregadoAPlantaAt(ahora);
            } else if (!ctx.planta() && m.getEntregadoAProveedorAt() == null) {
                m.setEntregadoAProveedorAt(ahora);
            } else {
                continue;
            }
            porConversacion.computeIfAbsent(deConversacion, k -> new ArrayList<>()).add(m.getId());
        }
        porConversacion.forEach((productorId, cambiados) -> eventos.publishEvent(
                new MensajeriaEventos.EstadoCambiado(productorId, List.copyOf(cambiados), EstadoMensaje.ENTREGADO, ahora)));
        return porConversacion.values().stream().flatMap(List::stream).sorted().toList();
    }

    /** Un mensaje recien guardado, con la vista de cada lado, para difundirlo por el canal. */
    @Transactional(readOnly = true)
    public MensajePublicable publicable(Long mensajeId) {
        MensajeProveedor m = mensajes.findById(mensajeId).orElseThrow(() -> NotFoundException.of("Mensaje", mensajeId));
        Long productorId = m.getConversacion().getProductor().getId();
        MensajeriaDtos.MensajeResponse vistaProveedor = vista(m, m.getLeidoPorProveedorAt());
        MensajeriaDtos.MensajeResponse vistaPlanta = vista(m, m.getLeidoPorPlantaAt());
        return new MensajePublicable(productorId, vistaProveedor, vistaPlanta);
    }

    // ------------------------------------------------------------- IVR telefonico (avisos por voz)

    /** Un aviso de la planta tal como lo escucha el proveedor por telefono. */
    public record AvisoParaProveedor(Long id, Instant fecha, String texto, boolean leido) {}

    /**
     * Lo que la planta le mando al proveedor desde {@code desde}: primero lo que no ha leido, y dentro
     * de eso lo mas reciente. Solo lectura: escucharlos no los marca hasta que el IVR lo confirme
     * (una llamada que se corta a la mitad no debe dar por leido lo que no se escucho).
     */
    @Transactional(readOnly = true)
    public List<AvisoParaProveedor> avisosRecientes(Long productorId, Instant desde, int maximo) {
        return conversaciones.findByProductor_Id(productorId)
                .map(c -> mensajes.findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(c.getId()).stream()
                        .filter(m -> !m.esDelProveedor())
                        .filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().isBefore(desde))
                        .sorted(java.util.Comparator
                                .comparing((MensajeProveedor m) -> m.getLeidoPorProveedorAt() != null)
                                .thenComparing(MensajeProveedor::getCreatedAt, java.util.Comparator.reverseOrder()))
                        .limit(maximo)
                        .map(m -> new AvisoParaProveedor(m.getId(), m.getCreatedAt(), m.getCuerpo(), m.getLeidoPorProveedorAt() != null))
                        .toList())
                .orElse(List.of());
    }

    /**
     * El proveedor escucho estos avisos por telefono: cuentan como leidos, igual que si los abriera en
     * la app, y la planta ve los checks en azul por el canal en vivo. Ids de otra conversacion o
     * escritos por el propio proveedor se ignoran sin error.
     */
    @Transactional
    public List<Long> marcarLeidosPorTelefono(Long productorId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        Instant ahora = Instant.now();
        List<Long> leidos = new ArrayList<>();
        for (MensajeProveedor m : mensajes.findAllById(ids)) {
            boolean deEstaConversacion = m.getConversacion().getProductor().getId().equals(productorId);
            if (!m.isActivo() || !deEstaConversacion || m.esDelProveedor() || m.getLeidoPorProveedorAt() != null) continue;
            m.setLeidoPorProveedorAt(ahora);
            if (m.getEntregadoAProveedorAt() == null) m.setEntregadoAProveedorAt(ahora);
            leidos.add(m.getId());
        }
        if (!leidos.isEmpty()) {
            eventos.publishEvent(new MensajeriaEventos.EstadoCambiado(productorId, List.copyOf(leidos), EstadoMensaje.LEIDO, ahora));
        }
        return leidos;
    }

    // --------------------------------------------------------------------------------- Internos

    /**
     * Guarda el mensaje. Con {@code clientUuid} es idempotente: si ese mismo mensaje ya llego (un
     * reintento de la cola offline tras un corte a medio envio), se devuelve el que ya estaba y no se
     * guarda otro ni se vuelve a difundir.
     */
    private MensajeriaDtos.MensajeResponse enviar(ConversacionProveedor conversacion, Usuario actor, String cuerpo,
            String clientUuid, boolean planta) {
        String uuid = clientUuid == null || clientUuid.isBlank() ? null : clientUuid.trim();
        if (uuid != null) {
            var existente = mensajes.findByClientUuid(uuid);
            if (existente.isPresent()) {
                MensajeProveedor m = existente.get();
                boolean mismoMensaje = m.getAutor().getId().equals(actor.getId())
                        && m.getConversacion().getId().equals(conversacion.getId());
                if (!mismoMensaje) {
                    throw new BusinessRuleException("El identificador del mensaje ya está en uso");
                }
                return mensaje(m, actor);
            }
        }
        String limpio = cuerpo == null ? "" : cuerpo.trim();
        if (limpio.isEmpty()) throw new BusinessRuleException("El mensaje no puede estar vacío");
        MensajeProveedor mensaje = new MensajeProveedor();
        mensaje.setConversacion(conversacion);
        mensaje.setAutor(actor);
        mensaje.setCuerpo(limpio);
        mensaje.setClientUuid(uuid);
        if (planta) mensaje.setLeidoPorPlantaAt(Instant.now()); else mensaje.setLeidoPorProveedorAt(Instant.now());
        MensajeProveedor guardado = mensajes.save(mensaje);
        conversacion.setUpdatedAt(Instant.now());
        conversaciones.save(conversacion);
        eventos.publishEvent(new MensajeriaEventos.MensajeGuardado(guardado.getId()));
        return mensaje(guardado, actor);
    }

    private MensajeriaDtos.ConversacionDetalleResponse detalle(ConversacionProveedor c, Usuario actor, boolean planta) {
        List<MensajeProveedor> historial = mensajes.findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(c.getId());
        return new MensajeriaDtos.ConversacionDetalleResponse(resumen(c, actor, planta), historial.stream().map(m -> mensaje(m, actor)).toList());
    }
    private MensajeriaDtos.ConversacionResponse resumen(ConversacionProveedor c, Usuario actor, boolean planta) {
        List<MensajeProveedor> historial = mensajes.findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(c.getId());
        MensajeProveedor ultimo = historial.isEmpty() ? null : historial.get(historial.size() - 1);
        long sinLeer = planta
                ? mensajes.countByConversacion_IdAndAutor_IdNotAndLeidoPorPlantaAtIsNullAndActivoTrue(c.getId(), actor.getId())
                : mensajes.countByConversacion_IdAndAutor_IdNotAndLeidoPorProveedorAtIsNullAndActivoTrue(c.getId(), actor.getId());
        Productor p = c.getProductor();
        return new MensajeriaDtos.ConversacionResponse(p.getId(), p.getCodigo(), nombre(p), p.getZonaPrincipal(), p.getTelefono(), sinLeer,
                ultimo == null ? c.getUpdatedAt() : ultimo.getCreatedAt(), ultimo == null ? null : ultimo.getCuerpo());
    }
    private MensajeriaDtos.ConversacionResponse resumenSinConversacion(Productor p) {
        return new MensajeriaDtos.ConversacionResponse(p.getId(), p.getCodigo(), nombre(p), p.getZonaPrincipal(),
                p.getTelefono(), 0L, null, null);
    }
    private boolean coincideConFiltro(Productor p, String filtro) {
        if (filtro.isBlank()) return true;
        return nombre(p).toLowerCase().contains(filtro)
                || p.getCodigo().toLowerCase().contains(filtro)
                || contiene(p.getTelefono(), filtro)
                || contiene(p.getZonaPrincipal(), filtro);
    }
    private boolean contiene(String valor, String filtro) { return valor != null && valor.toLowerCase().contains(filtro); }
    private MensajeriaDtos.MensajeResponse mensaje(MensajeProveedor m, Usuario actor) {
        Instant leido = esProveedor(actor) ? m.getLeidoPorProveedorAt() : m.getLeidoPorPlantaAt();
        return vista(m, leido).paraUsuario(actor.getId());
    }
    private static MensajeriaDtos.MensajeResponse vista(MensajeProveedor m, Instant leidoEn) {
        return new MensajeriaDtos.MensajeResponse(m.getId(), m.getAutor().getId(), m.getAutor().getNombreCompleto(), false,
                m.getCuerpo(), m.getCreatedAt(), leidoEn, m.getConversacion().getProductor().getId(), m.getClientUuid(), m.estado());
    }

    /**
     * Abrir la conversacion marca como leidos (y por lo tanto entregados) los mensajes del otro lado,
     * y avisa por el canal para que quien los escribio vea los checks de color sin recargar.
     */
    private void marcarComoLeido(ConversacionProveedor c, Usuario actor, boolean planta) {
        Instant ahora = Instant.now();
        List<Long> leidos = new ArrayList<>();
        mensajes.findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(c.getId()).stream()
                .filter(m -> !m.getAutor().getId().equals(actor.getId()))
                .forEach(m -> {
                    if (planta) {
                        if (m.getLeidoPorPlantaAt() == null) leidos.add(m.getId());
                        m.setLeidoPorPlantaAt(ahora);
                        if (m.getEntregadoAPlantaAt() == null) m.setEntregadoAPlantaAt(ahora);
                    } else {
                        if (m.getLeidoPorProveedorAt() == null) leidos.add(m.getId());
                        m.setLeidoPorProveedorAt(ahora);
                        if (m.getEntregadoAProveedorAt() == null) m.setEntregadoAProveedorAt(ahora);
                    }
                });
        if (!leidos.isEmpty()) {
            eventos.publishEvent(new MensajeriaEventos.EstadoCambiado(c.getProductor().getId(), List.copyOf(leidos), EstadoMensaje.LEIDO, ahora));
        }
    }
    private ConversacionProveedor conversacionDe(Productor p) { return conversaciones.findByProductor_Id(p.getId()).orElseGet(() -> { ConversacionProveedor c = new ConversacionProveedor(); c.setProductor(p); return conversaciones.save(c); }); }
    private Usuario usuario(String username) { return usuarios.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow(() -> new NotFoundException("Usuario no encontrado")); }
    private Productor productor(Long id) { return productores.findById(id).filter(Productor::isActivo).orElseThrow(() -> NotFoundException.of("Proveedor", id)); }
    private Productor productorDelActor(Usuario actor) { if (!esProveedor(actor)) throw new BusinessRuleException("Esta cuenta no es de proveedor"); return productores.findByUsuario_UsernameIgnoreCase(actor.getUsername()).orElseThrow(() -> new BusinessRuleException("El usuario no tiene un proveedor asociado")); }
    private boolean esProveedor(Usuario u) { return u.getRoles().stream().anyMatch(r -> Rol.PROVEEDOR.equals(r.getNombre())); }
    /**
     * Bandeja de planta = tener mensajeria.read y no ser una cuenta de proveedor (que tiene el mismo
     * permiso para SU conversacion). Antes se decidia por el nombre del rol (ADMINISTRADOR/SECRETARIA),
     * y conceder el permiso a otro rol o persona desde "Usuarios y roles" no tenia ningun efecto.
     */
    private boolean esPlanta(Usuario u) { return !esProveedor(u) && Usuarios.permisosDe(u).contains(Permiso.MENSAJERIA_READ); }
    private void exigirPersonalPlanta(Usuario u) { if (!esPlanta(u)) throw new AccessDeniedException("No tienes acceso a la bandeja de mensajes con proveedores"); }
    private void exigirEnvioPlanta(Usuario u) {
        exigirPersonalPlanta(u);
        if (!Usuarios.permisosDe(u).contains(Permiso.MENSAJERIA_WRITE)) throw new AccessDeniedException("No tienes permiso para enviar mensajes a proveedores");
    }
    private static String nombre(Productor p) { return p.getNombres() + " " + p.getApellidos(); }
}
