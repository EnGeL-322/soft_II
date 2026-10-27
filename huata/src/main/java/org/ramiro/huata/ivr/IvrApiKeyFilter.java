package org.ramiro.huata.ivr;

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
 * Autentica al IVR telefonico (Asterisk hoy, RapidPro mas adelante) en /api/v1/ivr/** con la cabecera
 * {@code X-Api-Key}. Mismo patron que {@code IngestaApiKeyFilter}, pero con <b>otra clave</b>
 * ({@code HUATA_IVR_API_KEY}): si una se filtra, se revoca sin tocar la otra.
 *
 * <p>CIERRA POR OMISION: sin clave configurada nadie entra. Comparacion en tiempo constante.
 *
 * <p>El IVR recibe un rol propio y nada mas: no tiene ningun permiso de modulo, asi que con esta
 * clave no se puede leer ni escribir nada fuera de /api/v1/ivr/**.
 */
@Component
public class IvrApiKeyFilter extends OncePerRequestFilter {

    public static final String ROL = "ROLE_IVR";
    /** Actor que queda en la bitacora cuando el IVR escribe (solicitud de zona, avisos leidos). */
    public static final String ACTOR = "ivr-telefonico";

    private static final String CABECERA = "X-Api-Key";

    private final byte[] claveEsperada;

    public IvrApiKeyFilter(@Value("${huata.security.ivr.api-key:}") String apiKey) {
        this.claveEsperada = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/ivr/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibida = request.getHeader(CABECERA);
        if (claveEsperada != null && recibida != null
                && MessageDigest.isEqual(claveEsperada, recibida.getBytes(StandardCharsets.UTF_8))) {
            var autenticacion = new UsernamePasswordAuthenticationToken(
                    ACTOR, null, List.of(new SimpleGrantedAuthority(ROL)));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        }
        // Sin clave valida no se lanza nada aqui: la cadena de seguridad responde 401 como el resto.
        chain.doFilter(request, response);
    }
}
