package org.ramiro.huata.tiemporeal;

import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Canal de acopio (RF-53, RF-54): cada recojo que registra el acopiador desde la app movil llega a
 * "Conciliacion planta" apenas se guarda, sin que nadie recargue la pantalla, y lo mismo el total
 * que registra planta.
 *
 * <p>Va gateado con {@code acopio.read} y no con {@code rutas.read} (el del GPS) porque no es la
 * misma audiencia: SECRETARIA consulta la conciliacion — es la base de la liquidacion — y no tiene
 * {@code rutas.read}.
 */
@Component
public class AcopioTiempoRealHandler extends CanalTiempoReal {

    public AcopioTiempoRealHandler(
            ObjectMapper objectMapper, JwtService jwtService, SecurityUserDetailsService userDetailsService) {
        super(objectMapper, jwtService, userDetailsService);
    }

    @Override
    protected String permisoRequerido() {
        return Permiso.ACOPIO_READ;
    }
}
