package org.ramiro.huata.usuarios;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Utilidades de lectura sobre {@link Usuario}. Existe para que el calculo "permisos efectivos de un
 * usuario" tenga un solo sitio: lo necesitan SecurityUserDetailsService (para las autoridades) y
 * AuthController (para que el frontend derive su menu de la misma lista). Si se duplicara en ambos,
 * el menu y la autorizacion podrian divergir, que es justo lo que este modelo viene a evitar.
 */
public final class Usuarios {

    private Usuarios() {}

    /**
     * (Union de los permisos de sus roles activos) + CONCEDER - REVOCAR, sin repetidos y en orden
     * estable.
     *
     * <p>Los roles suman acceso, nunca lo restan: dar un segundo rol jamas quita lo que ya se podia
     * hacer. Lo unico que resta es un REVOCAR explicito sobre esa persona, y gana siempre, para que
     * quitarle algo a alguien no se deshaga en silencio al darle otro rol que lo incluya.
     */
    public static List<String> permisosDe(Usuario usuario) {
        Set<String> efectivos = new TreeSet<>(permisosDeRoles(usuario.getRoles()));
        for (UsuarioPermiso excepcion : usuario.getPermisosPersonalizados()) {
            if (!excepcion.isActivo()) {
                continue;
            }
            String codigo = excepcion.getPermiso().getCodigo();
            if (excepcion.getTipo() == UsuarioPermiso.Tipo.CONCEDER) {
                efectivos.add(codigo);
            }
        }
        for (UsuarioPermiso excepcion : usuario.getPermisosPersonalizados()) {
            if (excepcion.isActivo() && excepcion.getTipo() == UsuarioPermiso.Tipo.REVOCAR) {
                efectivos.remove(excepcion.getPermiso().getCodigo());
            }
        }
        return List.copyOf(efectivos);
    }

    /** Lo que conceden los roles por si solos. Un rol dado de baja deja de conceder. */
    public static Set<String> permisosDeRoles(Collection<Rol> roles) {
        return roles.stream()
                .filter(Rol::isActivo)
                .flatMap(rol -> rol.getPermisos().stream())
                .map(Permiso::getCodigo)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Codigos con la excepcion indicada, para mostrarlos en la ficha del usuario. */
    public static List<String> excepciones(Usuario usuario, UsuarioPermiso.Tipo tipo) {
        return usuario.getPermisosPersonalizados().stream()
                .filter(UsuarioPermiso::isActivo)
                .filter(e -> e.getTipo() == tipo)
                .map(e -> e.getPermiso().getCodigo())
                .sorted()
                .toList();
    }
}
