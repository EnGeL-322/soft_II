package org.ramiro.huata.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Alta/edicion de una cuenta.
 *
 * <p>{@code permisosConcedidos}/{@code permisosRevocados} son las excepciones de esta persona
 * respecto de sus roles. {@code null} = no tocarlas (es lo que mandan Acopiadores y Proveedores, que
 * no gestionan excepciones); lista vacia = quitarlas todas.
 */
record UsuarioRequest(
        @NotBlank String nombreCompleto,
        @NotBlank String username,
        String email,
        String telefono,
        String password,
        @NotEmpty List<String> roles,
        List<String> permisosConcedidos,
        List<String> permisosRevocados) {

    UsuarioRequest(
            String nombreCompleto, String username, String email, String telefono, String password, List<String> roles) {
        this(nombreCompleto, username, email, telefono, password, roles, null, null);
    }
}

/**
 * Solo las excepciones de permisos de una cuenta, sin tocar sus datos ni sus roles. Es lo que se puede
 * cambiar desde "Usuarios y roles" en la cuenta de un acopiador, cuyos datos y rol viven en su modulo.
 */
record ExcepcionesRequest(List<String> permisosConcedidos, List<String> permisosRevocados) {}

record RolRequest(
        @NotBlank @Size(max = 80) String etiqueta,
        @Size(max = 200) String descripcion,
        List<String> permisos) {}

record RolResponse(
        Long id,
        String nombre,
        String etiqueta,
        String descripcion,
        String accesos,
        boolean sistema,
        boolean gestionadoEnSuModulo,
        boolean permisosBloqueados,
        long usuarios,
        List<String> permisos) {

    static RolResponse from(Rol r, long usuarios) {
        return new RolResponse(
                r.getId(),
                r.getNombre(),
                r.getEtiqueta(),
                r.getDescripcion(),
                r.getAccesos(),
                r.isSistema(),
                r.isGestionadoEnSuModulo(),
                r.isPermisosBloqueados(),
                usuarios,
                r.getPermisos().stream().map(Permiso::getCodigo).sorted().toList());
    }
}

record UsuarioResponse(
        Long id,
        String nombreCompleto,
        String username,
        String email,
        String telefono,
        List<String> roles,
        boolean activo,
        boolean gestionadoEnSuModulo,
        List<String> permisos,
        List<String> permisosConcedidos,
        List<String> permisosRevocados,
        Instant ultimoAcceso,
        Instant creadoEn) {

    static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombreCompleto(),
                u.getUsername(),
                u.getEmail(),
                u.getTelefono(),
                u.getRoles().stream()
                        .sorted(Comparator.comparing(r -> r.getOrden() == null ? Integer.MAX_VALUE : r.getOrden()))
                        .map(Rol::getNombre)
                        .toList(),
                u.isActivo(),
                u.getRoles().stream().anyMatch(Rol::isGestionadoEnSuModulo),
                Usuarios.permisosDe(u),
                Usuarios.excepciones(u, UsuarioPermiso.Tipo.CONCEDER),
                Usuarios.excepciones(u, UsuarioPermiso.Tipo.REVOCAR),
                u.getUltimoAcceso(),
                u.getCreatedAt());
    }
}

/** Catalogo modulo -> vista -> acciones que la pantalla de roles pinta como matriz. */
record ModuloAccesoResponse(String codigo, String nombre, List<VistaAccesoResponse> vistas) {}

record VistaAccesoResponse(
        String codigo,
        String nombre,
        String descripcion,
        String ruta,
        String icono,
        String permisoAcceso,
        List<AccionAccesoResponse> acciones) {}

/** {@code compartidaCon}: otras vistas que muestran este mismo permiso (es el mismo derecho). */
record AccionAccesoResponse(String permiso, String etiqueta, String descripcion, List<String> compartidaCon) {}
