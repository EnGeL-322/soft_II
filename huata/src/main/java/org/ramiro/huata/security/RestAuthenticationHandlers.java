package org.ramiro.huata.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Distingue "no estas autenticado" (401) de "estas autenticado pero no te alcanza el rol" (403).
 *
 * <p>Sin esto Spring Security responde 403 en ambos casos, y eso rompe dos cosas concretas: el
 * interceptor del frontend solo cierra sesion ante un 401, asi que con un token vencido el usuario
 * queda atrapado viendo errores sin volver al login; y el criterio de salida de la Fase 1 exige
 * poder demostrar la diferencia entre 401 y 403.
 *
 * <p>El cuerpo replica el formato de {@code ApiExceptionHandler.ApiError} para que toda la API
 * devuelva errores con la misma forma (seccion 5.3). El ObjectMapper es el de Jackson 3
 * (paquete tools.jackson), que es el que trae Spring Boot 4; el de com.fasterxml no esta
 * registrado como bean.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** Falta el token, o es invalido/expirado. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escribir(request, response, HttpStatus.UNAUTHORIZED, "Debes iniciar sesion para acceder a este recurso");
    }

    /** Token valido, pero el rol no alcanza. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, "No tienes permisos para esta accion");
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado, String mensaje)
            throws IOException {
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", estado.value());
        cuerpo.put("error", estado.getReasonPhrase());
        cuerpo.put("message", mensaje);
        cuerpo.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
