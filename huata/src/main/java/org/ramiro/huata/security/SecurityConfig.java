package org.ramiro.huata.security;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad stateless con JWT: sin sesiones, login por /api/v1/auth/login, y el resto de la API
 * exige un token valido (los permisos finos por rol se anotan con @PreAuthorize en cada modulo).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final org.ramiro.huata.ingesta.IngestaApiKeyFilter ingestaApiKeyFilter;
    private final org.ramiro.huata.ivr.IvrApiKeyFilter ivrApiKeyFilter;
    private final RestAuthenticationHandlers restAuthenticationHandlers;

    @Value("${huata.security.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * No se define un DaoAuthenticationProvider manual a proposito: al existir un unico bean
     * UserDetailsService (SecurityUserDetailsService) y un unico PasswordEncoder en el contexto,
     * Spring Security arma automaticamente el AuthenticationManager con ambos.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * La consola H2 y Swagger UI solo se abren en el perfil dev. En prod ademas estan
     * deshabilitados por configuracion (application-prod.properties), de modo que quedan cerrados
     * por dos vias distintas y no dependen de que nadie recuerde apagarlos.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean desarrollo = environment.matchesProfiles("dev");

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> {
                    if (desarrollo) {
                        // Necesario solo para que la consola H2 pueda dibujarse en un iframe.
                        frame.sameOrigin();
                    } else {
                        frame.deny();
                    }
                }))
                .authorizeHttpRequests(auth -> {
                    // Solo el login es publico, NO todo /auth/**. Con el comodin, "/auth/me" tambien
                    // quedaba abierto: una peticion sin sesion llegaba al controlador con
                    // Authentication nulo y reventaba con un 500 en vez de responder 401. Pasaba de
                    // verdad cada vez que un token expiraba, y ensuciaba el log con errores de
                    // servidor por algo que es culpa de la peticion (mismo criterio que la seccion
                    // 36.4). "/auth/me" cae ahora en anyRequest().authenticated().
                    auth.requestMatchers("/api/v1/auth/login").permitAll()
                            // El upgrade WebSocket no permite cabeceras Authorization desde el
                            // navegador. El interceptor del socket valida el JWT antes de aceptar.
                            .requestMatchers("/api/ws/**").permitAll()
                            .requestMatchers("/api/v1/public/**").permitAll()
                            // Sonda de vida del contenedor (healthcheck del docker-compose). El
                            // detalle del estado solo se muestra a usuarios autenticados
                            // (management.endpoint.health.show-details=when-authorized).
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                    if (desarrollo) {
                        auth.requestMatchers("/h2-console/**").permitAll()
                                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationHandlers)
                        .accessDeniedHandler(restAuthenticationHandlers))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Ingesta del equipo de domotica: se autentica con X-Api-Key, no con el JWT de una
                // persona. La ruta NO se declara permitAll: sigue exigiendo autenticacion, y quien
                // la concede es este filtro. Si la clave no esta configurada, nadie entra.
                .addFilterBefore(ingestaApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
                // IVR telefonico: mismo esquema con su propia clave (HUATA_IVR_API_KEY).
                .addFilterBefore(ivrApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
