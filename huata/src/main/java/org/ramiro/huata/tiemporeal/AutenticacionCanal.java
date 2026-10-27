package org.ramiro.huata.tiemporeal;

import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * La unica puerta de los canales WebSocket: {@code /api/ws/**} esta {@code permitAll} en
 * {@code SecurityConfig} porque el handshake HTTP no lleva cabecera Authorization, asi que el primer
 * mensaje del cliente ({@code AUTH} con su JWT) es lo que decide si se queda.
 *
 * <p>Vive aparte para que el canal de solo salida ({@link CanalTiempoReal}) y el de mensajeria, que es
 * bidireccional, validen exactamente igual: un filtro de seguridad copiado en dos sitios es un filtro
 * que un dia se arregla en uno solo.
 */
public final class AutenticacionCanal {
    private final JwtService jwtService;
    private final SecurityUserDetailsService userDetailsService;

    public AutenticacionCanal(JwtService jwtService, SecurityUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * La cuenta del token si es valido, esta activa y tiene el permiso; si no, {@code null}. Nunca
     * lanza: un token mal formado es una peticion invalida, no un error del servidor.
     */
    /** Como {@link #autenticar(String, String)} sin exigir un permiso: el canal de cambios filtra por mensaje. */
    public UserDetails autenticar(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            String username = jwtService.extractUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            return user.isEnabled() && jwtService.isValid(token, username) ? user : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public UserDetails autenticar(String token, String permisoRequerido) {
        if (token == null || token.isBlank()) return null;
        try {
            String username = jwtService.extractUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            boolean autorizado = user.isEnabled()
                    && jwtService.isValid(token, username)
                    && user.getAuthorities().stream().anyMatch(a -> permisoRequerido.equals(a.getAuthority()));
            return autorizado ? user : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
