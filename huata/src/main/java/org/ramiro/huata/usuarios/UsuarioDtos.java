package org.ramiro.huata.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

record UsuarioRequest(
        @NotBlank String nombreCompleto,
        @NotBlank String username,
        String email,
        String telefono,
        String password,
        @NotEmpty List<String> roles) {}

record RolResponse(Long id, String nombre, String descripcion, String accesos) {
    static RolResponse from(Rol r) {
        return new RolResponse(r.getId(), r.getNombre(), r.getDescripcion(), r.getAccesos());
    }
}

record UsuarioResponse(
        Long id,
        String nombreCompleto,
        String username,
        String email,
        String telefono,
        List<String> roles,
        boolean activo) {

    static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(
                u.getId(),
                u.getNombreCompleto(),
                u.getUsername(),
                u.getEmail(),
                u.getTelefono(),
                u.getRoles().stream().map(Rol::getNombre).toList(),
                u.isActivo());
    }
}
