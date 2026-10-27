package org.ramiro.huata.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.ramiro.huata.usuarios.Usuarios;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Login stateless: valida credenciales y entrega un JWT que el frontend guarda en memoria/localStorage. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /**
     * "permisos" es lo que el frontend usa para armar el menu y habilitar botones. No es una
     * concesion: el backend ya autoriza cada endpoint con esas mismas autoridades, asi que
     * manipular esta lista en el cliente no abre ningun acceso, solo desordena su propia interfaz.
     */
    public record LoginResponse(
            String token,
            String nombreCompleto,
            String username,
            List<String> roles,
            List<String> permisos) {}

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        Usuario usuario = usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(request.username())
                .orElseThrow(() -> new BadCredentialsException("Usuario o contrasena incorrectos"));

        List<String> roles = usuario.getRoles().stream().map(Rol::getNombre).toList();
        // Los permisos NO viajan dentro del JWT: se resuelven contra la base en cada peticion
        // (SecurityUserDetailsService). Si fueran un claim, revocarle un permiso a alguien no
        // surtiria efecto hasta que expirara su token.
        String token = jwtService.generateToken(
                usuario.getUsername(), Map.of("roles", roles, "nombre", usuario.getNombreCompleto()));
        auditoriaService.registrar("Inicio de sesión", "Usuarios", usuario.getUsername());
        usuarioRepository.marcarUltimoAcceso(usuario.getId(), Instant.now());
        return new LoginResponse(
                token,
                usuario.getNombreCompleto(),
                usuario.getUsername(),
                roles,
                Usuarios.permisosDe(usuario));
    }

    @GetMapping("/me")
    public LoginResponse me(Authentication authentication) {
        Usuario usuario = usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(authentication.getName())
                .orElseThrow(() -> new BadCredentialsException("Sesion invalida"));
        List<String> roles = usuario.getRoles().stream().map(Rol::getNombre).toList();
        return new LoginResponse(
                null,
                usuario.getNombreCompleto(),
                usuario.getUsername(),
                roles,
                Usuarios.permisosDe(usuario));
    }
}
