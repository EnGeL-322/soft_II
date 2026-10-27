package org.ramiro.huata.usuarios;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
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

    /** Filtro del listado de "Usuarios y roles". */
    public enum Estado {
        ACTIVOS,
        INACTIVOS,
        TODOS
    }

    /** Usuario renombrado de una cuenta eliminada que conserva historial (ver eliminarDesdeAdministracion). */
    static final String PREFIJO_ELIMINADO = "eliminado-";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReglasDeAcceso reglas;
    private final AuditoriaService auditoriaService;
    private final EntityManager entityManager;

    public PageResponse<Usuario> buscar(String q, Pageable pageable) {
        return buscar(q, Estado.ACTIVOS, false, pageable);
    }

    /**
     * @param soloInternos excluye las cuentas de acopiadores y proveedores, que se administran en su
     *     propio modulo. Se filtra en la base y no en el navegador para que la paginacion cuadre.
     */
    public PageResponse<Usuario> buscar(String q, Estado estado, boolean soloInternos, Pageable pageable) {
        Specification<Usuario> spec = SearchSpecifications
                .<Usuario>buscarPalabras(q, "nombreCompleto", "username", "email", "telefono")
                .and(porEstado(estado == null ? Estado.ACTIVOS : estado))
                .and((root, cq, cb) -> cb.notLike(root.get("username"), PREFIJO_ELIMINADO + "%"));
        if (soloInternos) {
            spec = spec.and(sinRolesDeOtroModulo());
        }
        return PageResponse.from(usuarioRepository.findAll(spec, pageable));
    }

    private static Specification<Usuario> porEstado(Estado estado) {
        return switch (estado) {
            case ACTIVOS -> SearchSpecifications.onlyActive();
            case INACTIVOS -> (root, cq, cb) -> cb.isFalse(root.get("activo"));
            case TODOS -> SearchSpecifications.alwaysTrue();
        };
    }

    private static Specification<Usuario> sinRolesDeOtroModulo() {
        return (root, cq, cb) -> {
            Subquery<Long> sub = cq.subquery(Long.class);
            Root<Usuario> otro = sub.from(Usuario.class);
            Join<Usuario, Rol> rol = otro.join("roles");
            sub.select(otro.get("id"))
                    .where(cb.equal(otro.get("id"), root.get("id")), rol.get("nombre").in(Rol.GESTIONADOS_EN_SU_MODULO));
            return cb.not(cb.exists(sub));
        };
    }

    public Usuario obtener(Long id) {
        return usuarioRepository
                .findById(id)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> NotFoundException.of("Usuario", id));
    }

    /** Incluye cuentas inactivas: la ficha de administracion tambien las muestra. */
    public Usuario obtenerCualquiera(Long id) {
        return usuarioRepository.findById(id).orElseThrow(() -> NotFoundException.of("Usuario", id));
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
        aplicarExcepciones(usuario, req.permisosConcedidos(), req.permisosRevocados());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario actualizar(Long id, UsuarioRequest req) {
        Usuario usuario = obtener(id);
        if (!usuario.getUsername().equalsIgnoreCase(req.username())
                && usuarioRepository.existsByUsernameIgnoreCase(req.username())) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario");
        }
        boolean administrabaUsuarios = Usuarios.permisosDe(usuario).contains(Permiso.USUARIOS_WRITE);
        aplicarDatos(usuario, req);
        if (req.password() != null && !req.password().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(req.password()));
        }
        aplicarExcepciones(usuario, req.permisosConcedidos(), req.permisosRevocados());
        Usuario guardado = usuarioRepository.save(usuario);
        if (administrabaUsuarios && !Usuarios.permisosDe(guardado).contains(Permiso.USUARIOS_WRITE)) {
            reglas.exigirQueAlguienAdministreUsuarios();
        }
        return guardado;
    }

    /**
     * Alta desde el modulo "Usuarios y roles". Es una puerta distinta de {@link #crear}, que sigue
     * siendo la que usan Acopiadores y Proveedores a traves de {@link #sincronizarCuentaDeAcceso}: la
     * restriccion es de por donde entra el alta, no del rol en si.
     */
    @Transactional
    public Usuario crearDesdeAdministracion(UsuarioRequest req) {
        rechazarRolesDeOtroModulo(req.roles());
        Usuario creado = crear(req);
        auditoriaService.registrar("Alta de usuario", "Usuarios", resumen(creado));
        return creado;
    }

    /**
     * Edicion desde "Usuarios y roles". Ademas de no poder asignar los roles de otro modulo, no
     * puede tocar una cuenta que YA pertenece a uno: quitarle el rol ACOPIADOR a un acopiador
     * dejaria su fila de {@code acopiadores} viva pero sin acceso, y nadie lo notaria hasta que esa
     * persona intentara entrar a la app movil.
     */
    @Transactional
    public Usuario actualizarDesdeAdministracion(Long id, UsuarioRequest req) {
        Usuario usuario = obtener(id);
        usuario.getRoles().stream()
                .map(Rol::getNombre)
                .filter(Rol.GESTIONADOS_EN_SU_MODULO::contains)
                .findFirst()
                .ifPresent(rol -> {
                    throw new BusinessRuleException("Esta cuenta es de un " + rol.toLowerCase()
                            + " y se edita en su propio modulo, no desde Usuarios y roles");
                });
        rechazarRolesDeOtroModulo(req.roles());
        boolean esUnoMismo = reglas.esUsuarioActual(usuario);
        Usuario actualizado = actualizar(id, req);
        // Aunque quede otro administrador, quitarse a uno mismo la administracion por accidente deja
        // a esa persona fuera de la pantalla que estaba usando, sin poder deshacerlo.
        if (esUnoMismo && !Usuarios.permisosDe(actualizado).contains(Permiso.USUARIOS_WRITE)) {
            throw new BusinessRuleException(
                    "No puedes quitarte a ti mismo el permiso de administrar usuarios; pide a otro administrador que lo haga");
        }
        auditoriaService.registrar("Edicion de usuario", "Usuarios", resumen(actualizado));
        return actualizado;
    }

    /**
     * Conceder o quitar acciones puntuales a una cuenta sin tocar sus datos ni sus roles. Existe por el
     * acopiador: su cuenta se edita en su modulo (que no maneja permisos), asi que no habia forma de
     * darle, por ejemplo, calidad.aprobar. El proveedor sigue excluido: es una cuenta externa y sus
     * permisos no se editan (Rol.PERMISOS_BLOQUEADOS).
     */
    @Transactional
    public Usuario actualizarExcepciones(Long id, List<String> conceder, List<String> revocar) {
        Usuario usuario = obtener(id);
        usuario.getRoles().stream()
                .map(Rol::getNombre)
                .filter(Rol.PERMISOS_BLOQUEADOS::contains)
                .findFirst()
                .ifPresent(rol -> {
                    throw new BusinessRuleException("Los permisos de una cuenta de " + rol.toLowerCase()
                            + " no se editan: es una cuenta externa");
                });
        boolean esUnoMismo = reglas.esUsuarioActual(usuario);
        boolean administrabaUsuarios = Usuarios.permisosDe(usuario).contains(Permiso.USUARIOS_WRITE);
        aplicarExcepciones(usuario,
                conceder == null ? List.of() : conceder,
                revocar == null ? List.of() : revocar);
        Usuario guardado = usuarioRepository.save(usuario);
        boolean administra = Usuarios.permisosDe(guardado).contains(Permiso.USUARIOS_WRITE);
        if (esUnoMismo && !administra) {
            throw new BusinessRuleException(
                    "No puedes quitarte a ti mismo el permiso de administrar usuarios; pide a otro administrador que lo haga");
        }
        if (administrabaUsuarios && !administra) {
            usuarioRepository.flush();
            reglas.exigirQueAlguienAdministreUsuarios();
        }
        auditoriaService.registrar("Edicion de permisos de usuario", "Usuarios", resumen(guardado));
        return guardado;
    }

    private static void rechazarRolesDeOtroModulo(List<String> roles) {
        if (roles == null) {
            return; // aplicarDatos ya exige al menos un rol; aqui solo se filtra cuales.
        }
        roles.stream().filter(Rol.GESTIONADOS_EN_SU_MODULO::contains).findFirst().ifPresent(rol -> {
            throw new BusinessRuleException("El rol " + rol
                    + " se asigna al crear el registro en su propio modulo (Acopiadores o Proveedores),"
                    + " no desde Usuarios y roles");
        });
    }

    /**
     * Crea o actualiza la cuenta ligada a un proveedor/acopiador en la misma transaccion. Es UNA sola
     * cuenta: el mismo usuario y contrasena entran a la web y a la app movil (ambas usan /auth/login).
     */
    public Usuario sincronizarCuentaDeAcceso(
            Usuario actual,
            String nombreCompleto,
            String username,
            String telefono,
            String password,
            String rol) {
        if (username == null || username.isBlank()) {
            throw new BusinessRuleException("El usuario (email) de la cuenta es obligatorio");
        }
        // El correo nace igual al usuario. Si la persona puso uno propio desde "Mi cuenta", editar su
        // ficha no lo pisa.
        boolean correoPropio = actual != null
                && actual.getEmail() != null
                && !actual.getEmail().equalsIgnoreCase(actual.getUsername());
        UsuarioRequest req = new UsuarioRequest(
                nombreCompleto,
                username.trim(),
                correoPropio ? actual.getEmail() : username.trim(),
                telefono,
                password,
                List.of(rol));
        return actual == null ? crear(req) : actualizar(actual.getId(), req);
    }

    @Transactional
    public void inactivar(Long id) {
        Usuario usuario = obtener(id);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        if (Usuarios.permisosDe(usuario).contains(Permiso.USUARIOS_WRITE)) {
            reglas.exigirQueAlguienAdministreUsuarios();
        }
    }

    @Transactional
    public void inactivarDesdeAdministracion(Long id) {
        Usuario usuario = obtener(id);
        if (reglas.esUsuarioActual(usuario)) {
            throw new BusinessRuleException("No puedes inactivar tu propia cuenta");
        }
        inactivar(id);
        auditoriaService.registrar("Inactivacion de usuario", "Usuarios", usuario.getUsername());
    }

    /** Resultado de {@link #eliminarDesdeAdministracion}, para que la pantalla diga lo que paso. */
    public record ResultadoEliminacion(String nombre, boolean conservaHistorial, long mensajes) {}

    /**
     * Elimina una cuenta interna desde "Usuarios y roles".
     *
     * <p>Si la persona no dejo historial, la fila se borra de verdad (con sus roles, excepciones y
     * notificaciones, que son su bandeja personal). Si escribio mensajes a proveedores, esas
     * conversaciones son historial del proveedor y exigen un autor (mensajes_proveedor.autor_usuario_id
     * NOT NULL): borrarlas no es una opcion. En ese caso la cuenta se da de baja igual —no puede
     * entrar, pierde roles y permisos, sale de los listados— y su usuario se renombra para dejar el
     * correo libre; su nombre sigue apareciendo como autor de esos mensajes.
     *
     * <p>Acopiadores y proveedores no se eliminan aqui: su cuenta va atada a su registro de negocio y
     * se gestiona en su modulo.
     */
    @Transactional
    public ResultadoEliminacion eliminarDesdeAdministracion(Long id) {
        Usuario usuario = obtenerCualquiera(id);
        if (reglas.esUsuarioActual(usuario)) {
            throw new BusinessRuleException("No puedes eliminar tu propia cuenta");
        }
        if (usuario.getRoles().stream().anyMatch(Rol::isGestionadoEnSuModulo)) {
            throw new BusinessRuleException(
                    "Esta cuenta es de un acopiador o proveedor; se elimina desde su propio modulo");
        }
        boolean administraba = usuario.isActivo() && Usuarios.permisosDe(usuario).contains(Permiso.USUARIOS_WRITE);
        String nombre = usuario.getNombreCompleto();
        String username = usuario.getUsername();
        String roles = usuario.getRoles().stream().map(Rol::getNombre).sorted().collect(Collectors.joining(","));

        long mensajes = entityManager
                .createQuery("select count(m) from MensajeProveedor m where m.autor.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        if (mensajes > 0) {
            usuario.setActivo(false);
            usuario.getRoles().clear();
            usuario.getPermisosPersonalizados().clear();
            usuario.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            String liberado = PREFIJO_ELIMINADO + id + "-" + username;
            usuario.setUsername(liberado.length() > 100 ? liberado.substring(0, 100) : liberado);
            usuarioRepository.save(usuario);
        } else {
            entityManager
                    .createQuery("delete from Notificacion n where n.usuario.id = :id")
                    .setParameter("id", id)
                    .executeUpdate();
            usuarioRepository.delete(usuario);
        }
        usuarioRepository.flush();
        if (administraba) {
            reglas.exigirQueAlguienAdministreUsuarios();
        }
        auditoriaService.registrar(
                "Eliminacion de usuario",
                "Usuarios",
                username + " roles=[" + roles + "]" + (mensajes > 0 ? " (conserva " + mensajes + " mensajes)" : ""));
        return new ResultadoEliminacion(nombre, mensajes > 0, mensajes);
    }

    @Transactional
    public Usuario reactivar(Long id) {
        Usuario usuario = obtenerCualquiera(id);
        if (usuario.isActivo()) {
            return usuario;
        }
        if (usuario.getUsername().startsWith(PREFIJO_ELIMINADO)) {
            throw new BusinessRuleException("Esta cuenta fue eliminada y no se puede reactivar; crea una nueva");
        }
        if (usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(usuario.getUsername()).isPresent()) {
            throw new BusinessRuleException("Ya hay otra cuenta activa con el usuario " + usuario.getUsername());
        }
        usuario.setActivo(true);
        Usuario guardado = usuarioRepository.save(usuario);
        auditoriaService.registrar("Reactivacion de usuario", "Usuarios", usuario.getUsername());
        return guardado;
    }

    private void aplicarDatos(Usuario usuario, UsuarioRequest req) {
        if (req.roles() == null || req.roles().isEmpty()) {
            throw new BusinessRuleException("Selecciona al menos un rol vigente");
        }
        usuario.setNombreCompleto(req.nombreCompleto());
        usuario.setUsername(req.username());
        usuario.setEmail(req.email());
        usuario.setTelefono(req.telefono());
        Set<Rol> roles = req.roles().stream()
                .distinct()
                .map(nombre -> rolRepository
                        .findByNombre(nombre)
                        .filter(Rol::isActivo)
                        .orElseThrow(() -> new BusinessRuleException("Rol invalido: " + nombre)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        usuario.setRoles(roles);
    }

    /**
     * Guarda solo las excepciones que cambian algo: conceder lo que ya dan sus roles o quitar lo que
     * no le dan no tiene efecto, y guardarlo confundiria a quien lea la ficha despues.
     *
     * <p>Se aplica como diferencia sobre las filas existentes y no "borrar todo e insertar": en un
     * mismo flush Hibernate inserta antes de borrar, y volver a poner un permiso que ya estaba
     * chocaria con la restriccion unica (usuario_id, permiso_id).
     */
    private void aplicarExcepciones(Usuario usuario, List<String> conceder, List<String> revocar) {
        if (conceder == null && revocar == null) {
            return;
        }
        Set<String> aConceder = new TreeSet<>(conceder == null ? List.of() : conceder);
        Set<String> aRevocar = new TreeSet<>(revocar == null ? List.of() : revocar);
        aConceder.stream().filter(aRevocar::contains).findFirst().ifPresent(codigo -> {
            throw new BusinessRuleException("El permiso " + codigo + " no puede concederse y quitarse a la vez");
        });
        Set<String> deRoles = Usuarios.permisosDeRoles(usuario.getRoles());
        aConceder.removeAll(deRoles);
        aRevocar.retainAll(deRoles);

        Map<String, UsuarioPermiso.Tipo> deseado = new HashMap<>();
        aConceder.forEach(c -> deseado.put(c, UsuarioPermiso.Tipo.CONCEDER));
        aRevocar.forEach(c -> deseado.put(c, UsuarioPermiso.Tipo.REVOCAR));
        Map<String, Permiso> permisos = reglas.resolver(deseado.keySet()).stream()
                .collect(Collectors.toMap(Permiso::getCodigo, p -> p));

        Iterator<UsuarioPermiso> existentes = usuario.getPermisosPersonalizados().iterator();
        while (existentes.hasNext()) {
            UsuarioPermiso actual = existentes.next();
            UsuarioPermiso.Tipo tipo = deseado.remove(actual.getPermiso().getCodigo());
            if (tipo == null) {
                existentes.remove();
            } else {
                actual.setTipo(tipo);
                actual.setActivo(true);
            }
        }
        deseado.forEach((codigo, tipo) ->
                usuario.getPermisosPersonalizados().add(new UsuarioPermiso(usuario, permisos.get(codigo), tipo)));
    }

    private static String resumen(Usuario u) {
        String roles = u.getRoles().stream().map(Rol::getNombre).sorted().collect(Collectors.joining(","));
        String concedidos = String.join(",", Usuarios.excepciones(u, UsuarioPermiso.Tipo.CONCEDER));
        String revocados = String.join(",", Usuarios.excepciones(u, UsuarioPermiso.Tipo.REVOCAR));
        String detalle = u.getUsername() + " roles=[" + roles + "]"
                + (concedidos.isEmpty() ? "" : " +[" + concedidos + "]")
                + (revocados.isEmpty() ? "" : " -[" + revocados + "]");
        return detalle.length() > 500 ? detalle.substring(0, 497) + "..." : detalle;
    }
}
