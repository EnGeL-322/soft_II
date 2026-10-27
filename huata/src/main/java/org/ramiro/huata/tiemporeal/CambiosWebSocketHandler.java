package org.ramiro.huata.tiemporeal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Canal de cambios ({@code /api/ws/cambios}): avisa a la web y a la app movil que algo cambio en un
 * modulo, para que la pantalla abierta se actualice sola y todos vean lo mismo al momento.
 *
 * <p><b>Solo avisa; no lleva datos.</b> Cada mensaje dice {@code modulo/entidad/id/accion}
 * ({@link Cambio.Publico}). La pantalla vuelve a pedir lo que muestra por la API REST, que aplica los
 * permisos de siempre. Asi este canal no puede filtrar DNI, montos ni GPS aunque se equivoque de
 * destinatario.
 *
 * <p><b>Quien recibe que.</b> Una sesion recibe un cambio si tiene alguno de los permisos de lectura del
 * modulo ({@link ModulosDeCambio}), o si el cambio es suyo: el proveedor se entera de sus recojos y su
 * liquidacion, el acopiador de su ruta, y cada cuenta de los cambios en sus propios permisos.
 *
 * <p><b>Permisos al dia.</b> Se leen al autenticar y se vuelven a leer cuando cambian los roles o los
 * permisos de la cuenta: un permiso quitado en la web deja de recibir avisos sin esperar a reconectar.
 *
 * <p>Cliente → servidor: {@code AUTH} (token), {@code PING}. Servidor → cliente: {@code LISTO},
 * {@code CAMBIOS}, {@code PONG}.
 */
@Slf4j
@Component
public class CambiosWebSocketHandler extends TextWebSocketHandler {

    /** Cambios de seguridad que obligan a releer los permisos de las sesiones. */
    private static final Set<String> ENTIDADES_DE_PERMISOS = Set.of("Usuario", "UsuarioPermiso");
    private static final Set<String> ENTIDADES_DE_ROLES = Set.of("Rol", "Vista", "Modulo");

    private final ObjectMapper objectMapper;
    private final AutenticacionCanal autenticacion;
    private final SecurityUserDetailsService usuarios;
    private final UsuarioRepository usuarioRepository;
    private final ProductorRepository productorRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final Map<String, Oyente> oyentes = new ConcurrentHashMap<>();

    public CambiosWebSocketHandler(ObjectMapper objectMapper, JwtService jwtService,
            SecurityUserDetailsService usuarios, UsuarioRepository usuarioRepository,
            ProductorRepository productorRepository, AcopiadorRepository acopiadorRepository) {
        this.objectMapper = objectMapper;
        this.autenticacion = new AutenticacionCanal(jwtService, usuarios);
        this.usuarios = usuarios;
        this.usuarioRepository = usuarioRepository;
        this.productorRepository = productorRepository;
        this.acopiadorRepository = acopiadorRepository;
    }

    /** Una sesion autenticada y lo que decide que le llega. */
    record Oyente(WebSocketSession sesion, String username, Set<String> permisos, Long usuarioId,
            Long productorId, Long acopiadorId) {

        boolean recibe(Cambio c) {
            return c.permisos().stream().anyMatch(permisos::contains)
                    || (productorId != null && productorId.equals(c.productorId()))
                    || (acopiadorId != null && acopiadorId.equals(c.acopiadorId()))
                    || (usuarioId != null && usuarioId.equals(c.usuarioId()));
        }

        Oyente conPermisos(Set<String> nuevos) {
            return new Oyente(sesion, username, nuevos, usuarioId, productorId, acopiadorId);
        }
    }

    record Entrante(String type, String token) {}
    record Listo(String type) {}
    record Cambios(String type, List<Cambio.Publico> cambios) {}
    record Pong(String type) {}

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Entrante entrante;
        try {
            entrante = objectMapper.readValue(message.getPayload(), Entrante.class);
        } catch (RuntimeException e) {
            if (!oyentes.containsKey(session.getId())) session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String tipo = entrante.type() == null ? "" : entrante.type();
        if ("AUTH".equals(tipo)) {
            autenticar(session, entrante.token());
        } else if (!oyentes.containsKey(session.getId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
        } else if ("PING".equals(tipo)) {
            enviar(oyentes.get(session.getId()), objectMapper.writeValueAsString(new Pong("PONG")));
        }
    }

    private void autenticar(WebSocketSession session, String token) throws IOException {
        UserDetails user = autenticacion.autenticar(token);
        if (user == null) {
            oyentes.remove(session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String username = user.getUsername();
        Oyente oyente = new Oyente(
                new ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024),
                username,
                permisosDe(user),
                usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(username).map(BaseEntity::getId).orElse(null),
                productorRepository.findByUsuario_UsernameIgnoreCase(username).map(BaseEntity::getId).orElse(null),
                acopiadorRepository.findByUsuario_UsernameIgnoreCase(username).map(BaseEntity::getId).orElse(null));
        oyentes.put(session.getId(), oyente);
        enviar(oyente, objectMapper.writeValueAsString(new Listo("LISTO")));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        oyentes.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        oyentes.remove(session.getId());
    }

    /** Cuantas sesiones escuchan. Sin ninguna, {@link RegistroCambios} ni siquiera junta los cambios. */
    public int oyentes() {
        return oyentes.size();
    }

    /** Envia a cada sesion solo los cambios que le corresponden, en un unico mensaje. */
    public void publicar(List<Cambio> cambios) {
        if (cambios.isEmpty() || oyentes.isEmpty()) return;
        actualizarPermisos(cambios);
        oyentes.values().forEach(oyente -> {
            List<Cambio.Publico> suyos = cambios.stream().filter(oyente::recibe).map(Cambio::publico).toList();
            if (suyos.isEmpty()) return;
            if (!enviar(oyente, objectMapper.writeValueAsString(new Cambios("CAMBIOS", suyos)))) {
                oyentes.remove(oyente.sesion().getId());
            }
        });
    }

    /**
     * Si cambiaron los permisos de una cuenta (o un rol, que afecta a varias), se releen antes de
     * repartir: asi el mismo lote ya respeta los permisos nuevos. Una cuenta desactivada se desconecta.
     */
    private void actualizarPermisos(List<Cambio> cambios) {
        boolean todos = cambios.stream().anyMatch(c -> ENTIDADES_DE_ROLES.contains(c.entidad()));
        Set<Long> cuentas = cambios.stream()
                .filter(c -> ENTIDADES_DE_PERMISOS.contains(c.entidad()) && c.usuarioId() != null)
                .map(Cambio::usuarioId)
                .collect(Collectors.toSet());
        if (!todos && cuentas.isEmpty()) return;
        oyentes.replaceAll((id, oyente) -> {
            if (!todos && !cuentas.contains(oyente.usuarioId())) return oyente;
            try {
                UserDetails user = usuarios.loadUserByUsername(oyente.username());
                if (user.isEnabled()) return oyente.conPermisos(permisosDe(user));
            } catch (RuntimeException e) {
                log.debug("No se pudieron releer los permisos de {}: {}", oyente.username(), e.toString());
            }
            cerrar(oyente);
            return oyente.conPermisos(Set.of());
        });
    }

    private static Set<String> permisosDe(UserDetails user) {
        return user.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toUnmodifiableSet());
    }

    private void cerrar(Oyente oyente) {
        try {
            oyente.sesion().close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException | RuntimeException e) {
            // ya estaba cerrada
        }
    }

    private boolean enviar(Oyente oyente, String json) {
        WebSocketSession sesion = oyente.sesion();
        if (!sesion.isOpen()) return false;
        try {
            sesion.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
