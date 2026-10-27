package org.ramiro.huata.tiemporeal;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Canal WebSocket de solo salida: el navegador se presenta con su JWT y a partir de ahi solo recibe.
 *
 * <p><b>Por que es una clase base y no se copia en cada canal.</b> El handshake es un control de
 * acceso: valida el token, resuelve el usuario y exige un permiso. Un filtro de seguridad duplicado
 * en dos sitios es un filtro que un dia se arregla en uno solo — la ruta {@code /api/ws/**} esta
 * declarada {@code permitAll} en {@code SecurityConfig} justamente porque el handshake de HTTP no
 * lleva cabecera Authorization, asi que este mensaje AUTH es la unica puerta que tiene el canal.
 *
 * <p>Cada canal declara su propio permiso en {@link #permisoRequerido()} y por eso son canales
 * separados y no uno con mensajes mezclados: publicar acopio en el canal del GPS lo entregaria a
 * quien tiene {@code rutas.read} pero no {@code acopio.read}, y al contrario.
 */
public abstract class CanalTiempoReal extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final AutenticacionCanal autenticacion;
    private final Set<WebSocketSession> sesiones = ConcurrentHashMap.newKeySet();

    protected CanalTiempoReal(
            ObjectMapper objectMapper, JwtService jwtService, SecurityUserDetailsService userDetailsService) {
        this.objectMapper = objectMapper;
        this.autenticacion = new AutenticacionCanal(jwtService, userDetailsService);
    }

    /** Permiso que debe tener la cuenta para escuchar este canal. */
    protected abstract String permisoRequerido();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        try {
            MensajeAutenticacion auth = objectMapper.readValue(message.getPayload(), MensajeAutenticacion.class);
            boolean autorizado = "AUTH".equals(auth.type())
                    && autenticacion.autenticar(auth.token(), permisoRequerido()) != null;
            if (!autorizado) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            sesiones.removeIf(s -> s.getId().equals(session.getId()));
            sesiones.add(new ConcurrentWebSocketSessionDecorator(session, 5_000, 128 * 1024));
        } catch (Exception e) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sesiones.removeIf(s -> s.getId().equals(session.getId()));
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sesiones.removeIf(s -> s.getId().equals(session.getId()));
    }

    /** Difunde el mensaje a las sesiones autenticadas y descarta las que ya no responden. */
    public void publicar(Object mensaje) {
        String json = objectMapper.writeValueAsString(mensaje);
        sesiones.removeIf(sesion -> !enviar(sesion, json));
    }

    /** Cuantos navegadores estan escuchando. Lo usan las pruebas y evita exponer el Set. */
    public int oyentes() {
        return sesiones.size();
    }

    private boolean enviar(WebSocketSession sesion, String json) {
        if (!sesion.isOpen()) return false;
        try {
            sesion.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private record MensajeAutenticacion(String type, String token) {}
}
