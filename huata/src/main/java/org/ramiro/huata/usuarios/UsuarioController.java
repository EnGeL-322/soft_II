package org.ramiro.huata.usuarios;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.USUARIOS_READ + "')")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final RolService rolService;

    /**
     * @param estado ACTIVOS (por defecto), INACTIVOS o TODOS.
     * @param internos true = sin las cuentas de acopiadores y proveedores (se gestionan en su modulo).
     */
    @GetMapping
    public PageResponse<UsuarioResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ACTIVOS") UsuarioService.Estado estado,
            @RequestParam(defaultValue = "false") boolean internos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return usuarioService
                .buscar(q, estado, internos, Paginas.de(page, size, Sort.by("nombreCompleto")))
                .map(UsuarioResponse::from);
    }

    /**
     * Codigos asignables desde esta pantalla. Acopiador y Proveedor no salen: se asignan al dar de
     * alta el registro en su propio modulo. El backend tambien los rechaza (UsuarioService). La app
     * movil del administrador sigue usando este endpoint.
     */
    @GetMapping("/roles")
    public List<String> roles() {
        return rolService.asignablesEnUsuarios();
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return UsuarioResponse.from(usuarioService.obtenerCualquiera(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest request) {
        Usuario creado = usuarioService.crearDesdeAdministracion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
        return UsuarioResponse.from(usuarioService.actualizarDesdeAdministracion(id, request));
    }

    /** Solo excepciones de permisos (ver UsuarioService#actualizarExcepciones). */
    @PutMapping("/{id}/permisos")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public UsuarioResponse actualizarPermisos(@PathVariable Long id, @RequestBody ExcepcionesRequest request) {
        return UsuarioResponse.from(usuarioService.actualizarExcepciones(
                id, request.permisosConcedidos(), request.permisosRevocados()));
    }

    /** Elimina la cuenta (ver UsuarioService#eliminarDesdeAdministracion). */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public UsuarioService.ResultadoEliminacion eliminar(@PathVariable Long id) {
        return usuarioService.eliminarDesdeAdministracion(id);
    }

    @PostMapping("/{id}/reactivar")
    @PreAuthorize("hasAuthority('" + Permiso.USUARIOS_WRITE + "')")
    public UsuarioResponse reactivar(@PathVariable Long id) {
        return UsuarioResponse.from(usuarioService.reactivar(id));
    }
}
