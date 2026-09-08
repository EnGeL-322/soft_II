package org.ramiro.huata.usuarios;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<Usuario> buscar(String q, Pageable pageable) {
        Specification<Usuario> spec = SearchSpecifications
                .<Usuario>textSearch(q, "nombreCompleto", "username", "email")
                .and(SearchSpecifications.onlyActive());
        return PageResponse.from(usuarioRepository.findAll(spec, pageable));
    }

    public Usuario obtener(Long id) {
        return usuarioRepository
                .findById(id)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> NotFoundException.of("Usuario", id));
    }

    @Transactional
    public Usuario crear(UsuarioRequest req) {
        if (usuarioRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new BusinessRuleException("La contrasena es obligatoria al crear un usuario");
        }
        Usuario usuario = new Usuario();
        aplicarDatos(usuario, req);
        usuario.setPassword(passwordEncoder.encode(req.password()));
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Long id, UsuarioRequest req) {
        Usuario usuario = obtener(id);
        aplicarDatos(usuario, req);
        if (req.password() != null && !req.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(req.password()));
        }
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void inactivar(Long id) {
        Usuario usuario = obtener(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    private void aplicarDatos(Usuario usuario, UsuarioRequest req) {
        usuario.setNombreCompleto(req.nombreCompleto());
        usuario.setUsername(req.username());
        usuario.setEmail(req.email());
        usuario.setTelefono(req.telefono());
        Set<Rol> roles = req.roles().stream()
                .map(nombre -> rolRepository
                        .findByNombre(nombre)
                        .orElseThrow(() -> new BusinessRuleException("Rol invalido: " + nombre)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        usuario.setRoles(roles);
    }
}
