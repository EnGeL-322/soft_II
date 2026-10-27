package org.ramiro.huata.cuenta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auth.AuthController.LoginResponse;
import org.ramiro.huata.cuenta.CuentaService.CuentaResponse;
import org.ramiro.huata.cuenta.CuentaService.DatosRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta de quien hace la petición (web y app). Basta con estar autenticado: no exige ningún
 * permiso porque solo toca la propia cuenta, que sale del token y nunca de la URL.
 */
@RestController
@RequestMapping("/api/v1/cuenta")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    public record ClaveRequest(@NotBlank String actual, @NotBlank String nueva) {}

    public record UsuarioRequest(@NotBlank String clave, @NotBlank String nuevoUsuario) {}

    @GetMapping
    public CuentaResponse obtener(Authentication auth) {
        return cuentaService.obtener(auth.getName());
    }

    @PutMapping("/datos")
    public CuentaResponse actualizarDatos(Authentication auth, @RequestBody DatosRequest req) {
        return cuentaService.actualizarDatos(auth.getName(), req);
    }

    @PutMapping("/clave")
    public ResponseEntity<Void> cambiarClave(Authentication auth, @Valid @RequestBody ClaveRequest req) {
        cuentaService.cambiarClave(auth.getName(), req.actual(), req.nueva());
        return ResponseEntity.noContent().build();
    }

    /** Devuelve la sesión nueva: el token anterior deja de valer porque su usuario ya no existe. */
    @PutMapping("/usuario")
    public LoginResponse cambiarUsuario(Authentication auth, @Valid @RequestBody UsuarioRequest req) {
        return cuentaService.cambiarUsuario(auth.getName(), req.clave(), req.nuevoUsuario());
    }
}
