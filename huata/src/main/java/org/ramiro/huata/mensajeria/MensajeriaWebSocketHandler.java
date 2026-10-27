package org.ramiro.huata.mensajeria;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.tiemporeal.AutenticacionCanal;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Canal en vivo de la mensajeria ({@code /api/ws/mensajeria}).
 *
 * <p><b>WebSocket nativo y no STOMP/SockJS.</b> El proyecto ya tiene dos canales nativos con el mismo
 * apreton de manos (mensaje {@code AUTH} con el JWT), nginx ya los proxya y ni la web ni la app usan una
 * libreria STOMP. Para una sola conversacion por proveedor y una bandeja compartida no hacen falta
 * topicos ni un broker: el servidor sabe a quien le toca cada mensaje.
 *
 * <p><b>A diferencia de los otros canales, este escucha al cliente</b>, pero solo para cosas de lectura
 * y acuse — nunca para escribir mensajes. Enviar sigue siendo el POST REST de siempre (idempotente por
 * {@code clientUuid}), que es lo que reintenta la cola offline del telefono. Asi hay una sola puerta de
 * escritura, con su validacion y sus permisos.
 *
 * <p>Cliente → servidor: {@code AUTH} (token), {@code SYNC} (desdeId), {@code ENTREGADO} (ids),
 * {@code PING}. Servidor → cliente: {@code LISTO}, {@code MENSAJE}, {@code ESTADO}, {@code SYNC},
 * {@code PONG}.
 *
 * <p><b>Quien recibe que.</b> El proveedor, solo lo de su conversacion; la planta (Administracion o
 * Secretaria), todo, porque la bandeja es compartida. Es la misma regla que aplica
 * {@link MensajeriaService} a los endpoints REST, resuelta una vez al autenticar.
 */
@Slf4j
@Component
public class MensajeriaWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AutenticacionCanal autenticacion;
    private final MensajeriaService servicio;
    private final Map<String, Oyente> oyentes = new ConcurrentHashMap<>();

    public MensajeriaWebSocketHandler(ObjectMapper objectMapper, JwtService jwtService,
            SecurityUserDetailsService userDetailsService, MensajeriaService servicio) {
        this.objectMapper = objectMapper;
        this.autenticacion = new AutenticacionCanal(jwtService, userDetailsService);
        this.servicio = servicio;
    }

    private record Oyente(WebSocketSession sesion, MensajeriaService.ContextoMensajeria contexto) {}

    record Entrante(String type, String token, Long desdeId, List<Long> ids) {}

    record Listo(String type, Long usuarioId, boolean planta, Long productorId) {}
    record EventoMensaje(String type, MensajeriaDtos.MensajeResponse mensaje) {}
    record EventoEstado(String type, Long productorId, List<Long> ids, EstadoMensaje estado, Instant en) {}
    record EventoSync(String type, List<MensajeriaDtos.MensajeResponse> mensajes, boolean completo) {}
    record Pong(String type) {}

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Entrante entrante;
        try {
            entrante = objectMapper.readValue(message.getPayload(), Entrante.class);
        } catch (RuntimeException e) {
            cerrarSiNoAutenticada(session);
            return;
        }
        String tipo = entrante.type() == null ? "" : entrante.type();
        Oyente oyente = oyentes.get(session.getId());
        if (oyente == null || "AUTH".equals(tipo)) {
            autenticar(session, entrante);
            return;
        }
        try {
            switch (tipo) {
                case "PING" -> enviar(oyente.sesion(), new Pong("PONG"));
                case "SYNC" -> {
                    List<MensajeriaDtos.MensajeResponse> faltantes = servicio.sincronizar(oyente.contexto(), entrante.desdeId());
                    enviar(oyente.sesion(), new EventoSync("SYNC", faltantes,
                            faltantes.size() < MensajeriaService.MAXIMO_POR_SINCRONIZACION));
                }
                case "ENTREGADO" -> servicio.marcarEntregados(oyente.contexto(), entrante.ids());
                default -> { /* tipo desconocido: se ignora, no rompe la conexion */ }
            }
        } catch (RuntimeException e) {
            // Un SYNC o un acuse que falla no debe tumbar la conexion: el cliente reintenta al reconectar.
            log.warn("Mensajeria en vivo: no se pudo atender {} de {}: {}", tipo, oyente.contexto().username(), e.toString());
        }
    }

    /**
     * El primer mensaje debe ser AUTH. Un AUTH posterior (el cliente renovo su token) se vuelve a
     * validar; si ya no vale, la conexion se cierra.
     */
    private void autenticar(WebSocketSession session, Entrante entrante) throws IOException {
        UserDetails user = "AUTH".equals(entrante.type())
                ? autenticacion.autenticar(entrante.token(), Permiso.MENSAJERIA_READ)
                : null;
        MensajeriaService.ContextoMensajeria contexto = null;
        if (user != null) {
            try {
                contexto = servicio.contexto(user.getUsername());
            } catch (RuntimeException e) {
                // Tiene el permiso pero no es ni planta ni un proveedor con ficha: no hay bandeja que darle.
                contexto = null;
            }
        }
        if (contexto == null) {
            oyentes.remove(session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        WebSocketSession decorada = new ConcurrentWebSocketSessionDecorator(session, 5_000, 512 * 1024);
        oyentes.put(session.getId(), new Oyente(decorada, contexto));
        enviar(decorada, new Listo("LISTO", contexto.usuarioId(), contexto.planta(), contexto.productorId()));
    }

    private void cerrarSiNoAutenticada(WebSocketSession session) throws IOException {
        if (!oyentes.containsKey(session.getId())) session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        oyentes.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        oyentes.remove(session.getId());
    }

    /** Un mensaje nuevo: a la planta y al proveedor de esa conversacion, cada uno con su vista. */
    public void publicarMensaje(MensajeriaService.MensajePublicable publicable) {
        oyentes.forEach((id, oyente) -> {
            MensajeriaService.ContextoMensajeria ctx = oyente.contexto();
            MensajeriaDtos.MensajeResponse vista;
            if (ctx.planta()) vista = publicable.vistaPlanta();
            else if (publicable.productorId().equals(ctx.productorId())) vista = publicable.vistaProveedor();
            else return;
            if (!enviar(oyente.sesion(), new EventoMensaje("MENSAJE", vista.paraUsuario(ctx.usuarioId())))) {
                oyentes.remove(id);
            }
        });
    }

    /** Cambio de checks: solo a quien puede ver esa conversacion. */
    public void publicarEstado(MensajeriaEventos.EstadoCambiado cambio) {
        EventoEstado evento = new EventoEstado("ESTADO", cambio.productorId(), cambio.mensajeIds(), cambio.estado(), cambio.en());
        oyentes.forEach((id, oyente) -> {
            if (!oyente.contexto().puedeVer(cambio.productorId())) return;
            if (!enviar(oyente.sesion(), evento)) oyentes.remove(id);
        });
    }

    /** Cuantas conexiones autenticadas hay. Lo usan las pruebas y evita difundir sin oyentes. */
    public int oyentes() {
        return oyentes.size();
    }

    private boolean enviar(WebSocketSession sesion, Object mensaje) {
        if (!sesion.isOpen()) return false;
        try {
            sesion.sendMessage(new TextMessage(objectMapper.writeValueAsString(mensaje)));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
