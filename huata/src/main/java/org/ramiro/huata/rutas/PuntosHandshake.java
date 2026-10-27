package org.ramiro.huata.rutas;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.tiemporeal.AutenticacionCanal;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PuntosHandshake implements HandshakeInterceptor {
    static final String IDENTIDAD = "puntos.identidad";
    static final String EXPIRA = "puntos.expira";
    static final String SUSCRIPCIONES = "puntos.suscripciones";
    private final JwtService jwt;
    private final SecurityUserDetailsService usuarios;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        // Flutter nativo usa Authorization. El navegador solo permite el parámetro del handshake.
        // No guardar el JWT en atributos ni registrarlo (incluidas excepciones) en logs.
        String authorization = request.getHeaders().getFirst("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");
        var user = new AutenticacionCanal(jwt, usuarios).autenticar(token, Permiso.RUTAS_READ);
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(IDENTIDAD, new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        attributes.put(EXPIRA, jwt.extractExpiration(token));
        attributes.put(SUSCRIPCIONES, new ConcurrentHashMap<String, String>());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {}
}
