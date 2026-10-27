package org.ramiro.huata.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra el limite de consultas solo sobre la ruta enumerable del portal publico.
 *
 * <p>Se acota a ese patron y no a todo {@code /api/v1/public/**} para no estorbar a la app del
 * proveedor, que consulta el resumen del dia cada vez que se abre (RF-49) y no revela nada al
 * repetirlo.
 */
@Configuration
@RequiredArgsConstructor
public class PortalWebConfig implements WebMvcConfigurer {

    private final LimiteConsultaPublicaInterceptor limiteConsultaPublica;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(limiteConsultaPublica).addPathPatterns("/api/v1/public/productores/*/calidad");
    }
}
