package org.ramiro.huata.usuarios;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reglas que comparten la edicion de usuarios y la de roles. */
@Component
@RequiredArgsConstructor
class ReglasDeAcceso {

    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;

    /** Convierte codigos en permisos y rechaza cualquiera que no exista en el catalogo. */
    Set<Permiso> resolver(Collection<String> codigos) {
        if (codigos == null || codigos.isEmpty()) {
            return new LinkedHashSet<>();
        }
        Set<String> pedidos = new TreeSet<>(codigos);
        List<Permiso> encontrados = permisoRepository.findByCodigoIn(pedidos);
        if (encontrados.size() != pedidos.size()) {
            Set<String> conocidos = encontrados.stream().map(Permiso::getCodigo).collect(Collectors.toSet());
            String desconocido = pedidos.stream().filter(c -> !conocidos.contains(c)).findFirst().orElse("?");
            throw new BusinessRuleException("Permiso desconocido: " + desconocido);
        }
        return new LinkedHashSet<>(encontrados);
    }

    /**
     * Ningun cambio puede dejar el sistema sin nadie que pueda administrar usuarios: no habria forma
     * de deshacerlo desde la aplicacion. Se evalua sobre el estado ya modificado (la consulta hace
     * flush), asi que hay que llamarlo despues de aplicar el cambio y dentro de la misma transaccion.
     */
    void exigirQueAlguienAdministreUsuarios() {
        boolean alguien = usuarioRepository.findByActivoTrue().stream()
                .anyMatch(u -> Usuarios.permisosDe(u).contains(Permiso.USUARIOS_WRITE));
        if (!alguien) {
            throw new BusinessRuleException(
                    "El cambio dejaria el sistema sin ningun usuario activo que pueda administrar usuarios");
        }
    }

    /** Nombre de quien hace la peticion, o null fuera de una peticion autenticada. */
    String usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth.getName() : null;
    }

    boolean esUsuarioActual(Usuario usuario) {
        String actual = usuarioActual();
        return actual != null && actual.equalsIgnoreCase(usuario.getUsername());
    }
}
