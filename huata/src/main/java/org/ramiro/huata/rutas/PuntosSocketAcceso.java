package org.ramiro.huata.rutas;

import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PuntosSocketAcceso {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PuntosSocketAcceso.class);
    private final AcopiadorRepository acopiadores;
    private final AsignacionRutaRepository asignaciones;
    private final SecurityUserDetailsService usuarios;

    @Transactional(readOnly = true)
    public boolean permitido(String username, String destino) {
        try {
            var usuario = usuarios.loadUserByUsername(username);
            if (!usuario.isEnabled() || usuario.getAuthorities().stream()
                    .noneMatch(a -> Permiso.RUTAS_READ.equals(a.getAuthority()))) return false;
            var acopiador = acopiadores.findByUsuario_UsernameIgnoreCase(username)
                    .filter(a -> a.isActivo()).orElse(null);
            if ("/user/queue/puntos".equals(destino)) return acopiador != null;
            if (destino == null || !destino.matches("/topic/rutas/[1-9][0-9]*/puntos")) return false;
            // Incluso usuarios con otros roles deben respetar la asignación si son acopiadores.
            if (acopiador == null) return usuario.getAuthorities().stream()
                    .anyMatch(a -> Permiso.RUTAS_WRITE.equals(a.getAuthority()));
            long rutaId = Long.parseLong(destino.split("/")[3]);
            var delDia = asignaciones.findByAcopiadorIdAndFechaAndActivoTrue(acopiador.getId(),
                    LocalDate.now(ZoneId.of("America/Lima")));
            if (!delDia.isEmpty()) return delDia.stream()
                    .anyMatch(a -> a.getRuta().isActivo() && a.getRuta().getId().equals(rutaId));
            return acopiador.getRuta() != null && acopiador.getRuta().isActivo()
                    && acopiador.getRuta().getId().equals(rutaId);
        } catch (RuntimeException ex) {
            log.debug("Autorización de puntos rechazada: {}", ex.getClass().getSimpleName());
            return false;
        }
    }
}
