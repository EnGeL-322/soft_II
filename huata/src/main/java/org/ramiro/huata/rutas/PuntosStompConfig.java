package org.ramiro.huata.rutas;

import java.security.Principal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.context.ApplicationContext;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class PuntosStompConfig implements WebSocketMessageBrokerConfigurer {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PuntosStompConfig.class);
    private final Map<String, Map<String, Object>> sesiones = new java.util.concurrent.ConcurrentHashMap<>();
    private final PuntosHandshake handshake;
    private final PuntosSocketAcceso acceso;
    private final ApplicationContext contexto;
    @Value("${huata.security.cors.allowed-origins}")
    private String origenes;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/api/ws/puntos").addInterceptors(handshake)
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler handler,
                                                      Map<String, Object> attributes) {
                        return (Principal) attributes.get(PuntosHandshake.IDENTIDAD);
                    }
                })
                .setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).toArray(String[]::new));
    }

    @Bean
    public ThreadPoolTaskScheduler puntosHeartbeat() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("puntos-heartbeat-");
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/rutas/", "/queue/puntos")
                .setTaskScheduler(puntosHeartbeat()).setHeartbeatValue(new long[] {10000, 10000});
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // El broker simple no implementa receipts. Procesamiento síncrono de SUBSCRIBE permite
        // confirmar DESPUÉS de registrar la suscripción, sin una ventana de pérdida de eventos.
        registration.executor(new SyncTaskExecutor()).interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (h == null || h.getCommand() == null) return message;
                if (h.getCommand() == StompCommand.DISCONNECT) {
                    sesiones.remove(h.getSessionId());
                    return message;
                }
                if (!vigente(h) || h.getUser() == null) throw new AccessDeniedException("Conexión no autorizada");
                sesiones.put(h.getSessionId(), h.getSessionAttributes());
                if (h.getCommand() == StompCommand.SUBSCRIBE) {
                    if (!acceso.permitido(h.getUser().getName(), h.getDestination()))
                        throw new AccessDeniedException("Ruta no autorizada");
                    suscripciones(h).put(h.getSubscriptionId(), h.getDestination());
                } else if (h.getCommand() == StompCommand.UNSUBSCRIBE) {
                    suscripciones(h).remove(h.getSubscriptionId());
                } else if (h.getCommand() != StompCommand.CONNECT && h.getCommand() != StompCommand.STOMP) {
                    // Canal de solo lectura: nadie puede publicar eventos como si fuera el servidor.
                    throw new AccessDeniedException("Canal de solo lectura");
                }
                return message;
            }

            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (!sent || h == null || h.getCommand() != StompCommand.SUBSCRIBE) return;
                String receipt = h.getFirstNativeHeader("receipt");
                if (receipt == null) return;
                var ack = StompHeaderAccessor.create(StompCommand.RECEIPT);
                ack.setSessionId(h.getSessionId());
                ack.setSessionAttributes(h.getSessionAttributes());
                ack.setReceiptId(receipt);
                contexto.getBean("clientOutboundChannel", MessageChannel.class)
                        .send(MessageBuilder.createMessage(new byte[0], ack.getMessageHeaders()));
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                var h = StompHeaderAccessor.wrap(message);
                if (h.getMessageType() != SimpMessageType.MESSAGE) return message;
                // Recuperar el contexto guardado al suscribir: el broker no copia atributos de
                // sesión al publicar. Revalidar asignación, cuenta y expiración antes de entregar.
                var attrs = sesiones.get(h.getSessionId());
                if (attrs == null || !(attrs.get(PuntosHandshake.EXPIRA) instanceof Instant expira)
                        || !Instant.now().isBefore(expira)) {
                    log.debug("Evento descartado: sesión ausente o vencida");
                    return null;
                }
                Principal identidad = (Principal) attrs.get(PuntosHandshake.IDENTIDAD);
                @SuppressWarnings("unchecked")
                var destinos = (Map<String, String>) attrs.get(PuntosHandshake.SUSCRIPCIONES);
                String destino = destinos.get(h.getSubscriptionId());
                boolean permitido = identidad != null && acceso.permitido(identidad.getName(), destino);
                log.debug("Entrega de puntos: destino={}, permitido={}", destino, permitido);
                return permitido ? message : null;
            }
        });
    }

    @org.springframework.context.event.EventListener
    public void alCerrar(org.springframework.web.socket.messaging.SessionDisconnectEvent evento) {
        sesiones.remove(evento.getSessionId());
    }

    private static boolean vigente(StompHeaderAccessor h) {
        var attrs = h.getSessionAttributes();
        return attrs != null && attrs.get(PuntosHandshake.EXPIRA) instanceof Instant expira
                && Instant.now().isBefore(expira);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> suscripciones(StompHeaderAccessor h) {
        return (Map<String, String>) h.getSessionAttributes().get(PuntosHandshake.SUSCRIPCIONES);
    }
}
