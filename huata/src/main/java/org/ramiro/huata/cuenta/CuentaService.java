package org.ramiro.huata.cuenta;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.auth.AuthController.LoginResponse;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.common.DemasiadasSolicitudesException;
import org.ramiro.huata.common.LimitadorDeConsultas;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.ramiro.huata.usuarios.Usuarios;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Mi cuenta": lo que cada persona cambia de su propia cuenta sin pedírselo al administrador.
 *
 * <p>Siempre opera sobre la cuenta del token, nunca sobre un id recibido: no hay forma de que alguien
 * toque la cuenta de otro desde aquí.
 *
 * <p><b>Reglas (decididas por el cliente, 2026-10-25).</b>
 * <ul>
 *   <li>Todos pueden cambiar su contraseña y su usuario, confirmando la contraseña actual.</li>
 *   <li>Proveedores y acopiadores cambian su teléfono y correo, pero <b>no su nombre</b>: identifica
 *       sus pagos y liquidaciones y lo corrige la planta en su ficha. El teléfono se copia a la ficha,
 *       para que la ficha y la cuenta no digan cosas distintas.</li>
 *   <li>El personal interno edita nombre, teléfono y correo.</li>
 * </ul>
 *
 * <p><b>Cambiar el usuario</b> invalida el token vigente (el usuario es su {@code subject}), por eso
 * la respuesta trae uno nuevo. Lo ya registrado conserva el usuario anterior en su autoría: es
 * historia, y la auditoría anota el cambio para poder seguirla.
 */
@Service
public class CuentaService {

    static final int CLAVE_MINIMA = 8;
    private static final Pattern USUARIO = Pattern.compile("^[\\p{L}0-9._@+\\-]{3,100}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository usuarios;
    private final ProductorRepository productores;
    private final AcopiadorRepository acopiadores;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditoriaService auditoria;
    /** Intentos con la contraseña actual por cuenta y minuto: adivinarla a fuerza de pruebas no sale a cuenta. */
    private final LimitadorDeConsultas intentos;

    public CuentaService(
            UsuarioRepository usuarios,
            ProductorRepository productores,
            AcopiadorRepository acopiadores,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditoriaService auditoria,
            @Value("${huata.cuenta.intentos-por-minuto:5}") int intentosPorMinuto) {
        this.usuarios = usuarios;
        this.productores = productores;
        this.acopiadores = acopiadores;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditoria = auditoria;
        this.intentos = new LimitadorDeConsultas(intentosPorMinuto, Clock.systemUTC());
    }

    public record CuentaResponse(
            String nombreCompleto,
            String username,
            String email,
            String telefono,
            List<String> roles,
            java.time.Instant ultimoAcceso,
            boolean puedeEditarNombre) {}

    public record DatosRequest(String nombreCompleto, String email, String telefono) {}

    @Transactional(readOnly = true)
    public CuentaResponse obtener(String username) {
        Usuario u = cuenta(username);
        return new CuentaResponse(
                u.getNombreCompleto(),
                u.getUsername(),
                correoVisible(u),
                u.getTelefono(),
                u.getRoles().stream().map(Rol::getEtiqueta).toList(),
                u.getUltimoAcceso(),
                puedeEditarNombre(u));
    }

    @Transactional
    public void cambiarClave(String username, String actual, String nueva) {
        Usuario u = cuenta(username);
        verificarClave(u, actual);
        if (nueva == null || nueva.length() < CLAVE_MINIMA) {
            throw new BusinessRuleException("La nueva contraseña debe tener al menos " + CLAVE_MINIMA + " caracteres.");
        }
        if (passwordEncoder.matches(nueva, u.getPassword())) {
            throw new BusinessRuleException("La nueva contraseña debe ser distinta de la actual.");
        }
        u.setPassword(passwordEncoder.encode(nueva));
        auditoria.registrar("Cambio de contraseña", "Mi cuenta", u.getUsername());
    }

    @Transactional
    public LoginResponse cambiarUsuario(String username, String clave, String nuevo) {
        Usuario u = cuenta(username);
        verificarClave(u, clave);
        String limpio = nuevo == null ? "" : nuevo.trim();
        if (!USUARIO.matcher(limpio).matches()) {
            throw new BusinessRuleException(
                    "El usuario debe tener entre 3 y 100 caracteres, sin espacios (letras, números, . _ - @ +).");
        }
        if (limpio.equalsIgnoreCase(u.getUsername())) {
            throw new BusinessRuleException("Ese ya es tu usuario.");
        }
        if (usuarios.existsByUsernameIgnoreCase(limpio)) {
            throw new BusinessRuleException("Ese usuario ya lo usa otra persona.");
        }
        String anterior = u.getUsername();
        // Proveedores y acopiadores nacen con correo = usuario: si nunca lo cambiaron, sigue al usuario.
        if (anterior.equalsIgnoreCase(Objects.toString(u.getEmail(), ""))) {
            u.setEmail(limpio);
        }
        u.setUsername(limpio);
        usuarios.saveAndFlush(u);
        auditoria.registrar("Cambio de usuario", "Mi cuenta", anterior + " → " + limpio);
        return sesion(u);
    }

    @Transactional
    public CuentaResponse actualizarDatos(String username, DatosRequest req) {
        Usuario u = cuenta(username);
        List<String> cambios = new ArrayList<>();

        String nombre = recortar(req.nombreCompleto());
        if (nombre != null && !nombre.equals(u.getNombreCompleto())) {
            if (!puedeEditarNombre(u)) {
                throw new BusinessRuleException(
                        "Tu nombre lo corrige la planta en tu ficha, porque identifica tus pagos.");
            }
            if (!nombre.matches(DatosPersonales.NOMBRE_REGEX)) {
                throw new BusinessRuleException(DatosPersonales.NOMBRE_MENSAJE);
            }
            cambios.add("nombre: " + u.getNombreCompleto() + " → " + nombre);
            u.setNombreCompleto(nombre);
        }

        String correo = recortar(req.email());
        if (!Objects.equals(correo, correoVisible(u))) {
            if (correo != null && !CORREO.matcher(correo).matches()) {
                throw new BusinessRuleException("Correo inválido.");
            }
            cambios.add("correo: " + Objects.toString(u.getEmail(), "—") + " → " + Objects.toString(correo, "—"));
            u.setEmail(correo);
        }

        String telefono = recortar(req.telefono());
        if (!Objects.equals(telefono, recortar(u.getTelefono()))) {
            if (telefono != null && !telefono.matches(DatosPersonales.TELEFONO_REGEX)) {
                throw new BusinessRuleException(DatosPersonales.TELEFONO_MENSAJE);
            }
            cambios.add("teléfono: " + Objects.toString(u.getTelefono(), "—") + " → " + Objects.toString(telefono, "—"));
            u.setTelefono(telefono);
            // La ficha guarda el mismo teléfono: si no se copia, la próxima edición de la ficha lo pisaría.
            productores.findByUsuario_UsernameIgnoreCase(u.getUsername()).ifPresent(p -> p.setTelefono(telefono));
            acopiadores.findByUsuario_UsernameIgnoreCase(u.getUsername()).ifPresent(a -> a.setTelefono(telefono));
        }

        if (!cambios.isEmpty()) {
            auditoria.registrar("Edición de datos personales", "Mi cuenta", u.getUsername() + " · " + String.join("; ", cambios));
        }
        return obtener(u.getUsername());
    }

    /**
     * Proveedores y acopiadores nacen con correo = usuario ("alexander"), que no es un correo: se
     * muestra como vacío para no presentar el usuario como si fuera su dirección.
     */
    private static String correoVisible(Usuario u) {
        String correo = recortar(u.getEmail());
        return correo != null && CORREO.matcher(correo).matches() ? correo : null;
    }

    /** Proveedor y acopiador: su nombre vive en su ficha y lo corrige la planta. */
    static boolean puedeEditarNombre(Usuario u) {
        return u.getRoles().stream()
                .map(Rol::getNombre)
                .noneMatch(r -> r.equals(Rol.PROVEEDOR) || r.equals(Rol.ACOPIADOR));
    }

    private void verificarClave(Usuario u, String clave) {
        if (!intentos.permitir(u.getUsername().toLowerCase())) {
            throw new DemasiadasSolicitudesException(
                    "Demasiados intentos. Espera " + intentos.segundosParaReintentar(u.getUsername().toLowerCase())
                            + " segundos.");
        }
        if (clave == null || !passwordEncoder.matches(clave, u.getPassword())) {
            throw new BusinessRuleException("La contraseña actual no es correcta.");
        }
    }

    private Usuario cuenta(String username) {
        return usuarios.findByUsernameIgnoreCaseAndActivoTrue(username)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada"));
    }

    private LoginResponse sesion(Usuario u) {
        List<String> roles = u.getRoles().stream().map(Rol::getNombre).toList();
        String token = jwtService.generateToken(u.getUsername(), Map.of("roles", roles, "nombre", u.getNombreCompleto()));
        return new LoginResponse(token, u.getNombreCompleto(), u.getUsername(), roles, Usuarios.permisosDe(u));
    }

    private static String recortar(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
