package org.ramiro.huata.security;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.ramiro.huata.usuarios.Usuarios;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecurityUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
        // Dos familias de autoridades, y no son intercambiables:
        //   ROLE_<nombre>  identidad del usuario; solo para lo que depende del rol como tal.
        //   <recurso>.<accion>  lo que realmente autoriza cada endpoint (@PreAuthorize hasAuthority).
        // Un rol sin permisos asociados (PROVEEDOR) queda autenticado pero no accede a ningun
        // modulo interno, que es exactamente lo que se quiere: su superficie es el portal publico.
        List<GrantedAuthority> authorities = new ArrayList<>();
        usuario.getRoles().forEach(rol -> authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.getNombre())));
        Usuarios.permisosDe(usuario).forEach(codigo -> authorities.add(new SimpleGrantedAuthority(codigo)));
        return new org.springframework.security.core.userdetails.User(
                usuario.getUsername(), usuario.getPassword(), usuario.isActivo(), true, true, true, authorities);
    }
}
