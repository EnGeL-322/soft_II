package org.ramiro.huata.tiemporeal;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.mensajeria.MensajeriaWebSocketHandler;
import org.ramiro.huata.ubicaciones.UbicacionWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Canales WebSocket de la aplicacion. Cada uno tiene su propio permiso (ver {@link CanalTiempoReal})
 * y su propia ruta; el origen permitido es el mismo que el de CORS, para no abrir el socket a un
 * origen que la API rechazaria.
 *
 * <p><b>El mismo origen siempre pasa, liste lo que liste esta propiedad.</b> Spring valida el
 * handshake con {@code WebUtils.isValidOrigin}, que acepta una peticion del mismo origen antes de
 * mirar la lista. En el despliegue real el navegador llega por nginx (mismo origen que el SPA), asi
 * que el canal funciona aunque {@code HUATA_CORS_ORIGINS} no incluya el dominio publico. La lista
 * solo importa para un front servido desde otro origen, como el {@code ng serve} de desarrollo.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class CanalesTiempoRealConfig implements WebSocketConfigurer {

    private final UbicacionWebSocketHandler ubicaciones;
    private final AcopioTiempoRealHandler acopio;
    private final MensajeriaWebSocketHandler mensajeria;
    private final CambiosWebSocketHandler cambios;

    @Value("${huata.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origenes = origenesPermitidos();
        registry.addHandler(ubicaciones, "/api/ws/ubicaciones").setAllowedOrigins(origenes);
        registry.addHandler(acopio, "/api/ws/acopio").setAllowedOrigins(origenes);
        registry.addHandler(mensajeria, "/api/ws/mensajeria").setAllowedOrigins(origenes);
        // Todo lo demas: avisa que algo cambio para que web y app se actualicen solas (ver RegistroCambios).
        registry.addHandler(cambios, "/api/ws/cambios").setAllowedOrigins(origenes);
    }

    /**
     * Se descartan las entradas vacias. Una coma de sobra o un espacio en
     * {@code HUATA_CORS_ORIGINS} dejaria un origen "" en la lista, y un origen vacio no autoriza a
     * nadie pero si deja la configuracion diciendo una cosa distinta de lo que hace.
     *
     * <p>Si no queda ninguno, se registra la lista vacia a proposito: eso es "solo mismo origen",
     * que es justo el modo en que corre detras de nginx. Fallar el arranque por esto dejaria el
     * despliegue caido por una variable cosmetica.
     */
    private String[] origenesPermitidos() {
        return Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
