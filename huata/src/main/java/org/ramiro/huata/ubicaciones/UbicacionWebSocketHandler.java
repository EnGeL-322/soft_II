package org.ramiro.huata.ubicaciones;

import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.tiemporeal.CanalTiempoReal;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Difunde a los administradores cada posición GPS apenas llega desde la aplicación móvil. */
@Component
public class UbicacionWebSocketHandler extends CanalTiempoReal {

    public UbicacionWebSocketHandler(
            ObjectMapper objectMapper, JwtService jwtService, SecurityUserDetailsService userDetailsService) {
        super(objectMapper, jwtService, userDetailsService);
    }

    @Override
    protected String permisoRequerido() {
        return Permiso.RUTAS_READ;
    }
}
