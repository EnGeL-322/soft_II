package org.ramiro.huata.ingesta;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica al equipo de domotica en /api/v1/ingesta/** mediante una clave de API en la cabecera
 * {@code X-Api-Key}.
 *
 * <p>POR QUE NO JWT: el token de sesion pertenece a una persona, caduca en horas y obligaria a
 * guardar la contrasena de alguien en el aparato. Un equipo no es un usuario.
 *
 * <p>CIERRA POR OMISION: si {@code HUATA_INGESTA_API_KEY} no esta configurada, el filtro no
 * autentica a nadie y la ruta responde 401. Una clave ausente nunca abre la puerta, que es el error
 * clasico de este patron.
 *
 * <p>La comparacion es en tiempo constante ({@link MessageDigest#isEqual}) para no filtrar la clave
 * caracter a caracter midiendo cuanto tarda la respuesta.
 */
@Component
public class IngestaApiKeyFilter extends OncePerRequestFilter {

    /** Rol propio del equipo: no se le presta el de un administrador. */
    public static final String ROL = "ROLE_INGESTA";

    private static final String CABECERA = "X-Api-Key";

    private final byte[] claveEsperada;

    public IngestaApiKeyFilter(@Value("${huata.security.ingesta.api-key:}") String apiKey) {
        this.claveEsperada = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Solo cubre lo que envia el aparato. GET /api/v1/ingesta/lectura queda fuera: la dispara una
     * persona desde el navegador con su JWT, en sentido contrario (el sistema pregunta al equipo).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/v1/ingesta/") || uri.equals("/api/v1/ingesta/lectura");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(CABECERA);
        if (claveEsperada != null && recibida != null
                && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    "equipo-domotica", null, List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        }
        // Si no coincide no se lanza nada aqui: se deja el contexto vacio y es la cadena de seguridad
        // la que responde 401 con el mismo formato que el resto de la API.
        chain.doFilter(request, response);
    }
}
