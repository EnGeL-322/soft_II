package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de usuarios y asignacion de roles (alcance de la Fase 1, tarea 4).
 *
 * <p>Lo que se comprueba no es que el CRUD guarde campos, sino lo que puede hacer dano: que la
 * contrasena nunca quede en claro, que no se pueda duplicar un usuario y que no se pueda asignar un
 * rol inventado, porque un rol inexistente deja a la persona sin ningun permiso y sin explicacion.
 */
@SpringBootTest
@Transactional
class UsuarioServiceTests {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private RolService rolService;

    /**
     * Los dos catalogos no son el mismo a proposito: el de "Usuarios y roles" no ofrece Acopiador ni
     * Proveedor, porque esas cuentas nacen en su propio modulo junto al registro de negocio que las
     * sostiene. El catalogo de roles si los lista: son roles vigentes, solo que no se asignan ahi.
     */
    @Test
    void elCatalogoDeAltaDeUsuariosExcluyeLosRolesDeOtroModuloYElDeRolesNo() {
        assertThat(new UsuarioController(usuarioService, rolService).roles())
                .contains(Rol.ADMINISTRADOR, Rol.CALIDAD, Rol.SECRETARIA, Rol.PRODUCTOR_QUESO,
                        Rol.SUPERVISOR_PLANTA, Rol.VENTAS, Rol.GERENCIA)
                .doesNotContain(Rol.ACOPIADOR, Rol.PROVEEDOR);
        assertThat(rolService.listar()).extracting(RolResponse::nombre)
                .containsAll(MatrizPermisos.ROLES.stream().map(MatrizPermisos.DefinicionRol::codigo).toList());
    }

    /**
     * Un codigo que no corresponde a un rol activo se rechaza. SUPERVISOR, OPERADOR y COMPRAS son
     * los nombres que V25 borro; los perfiles del cliente volvieron en V46 con su codigo propio
     * (SUPERVISOR_PLANTA, OPERADOR_PLANTA, COMPRAS_ALMACEN).
     */
    @Test
    void noPermiteAsignarUnRolQueNoEsteEnElCatalogo() {
        for (String rol : List.of("SUPERVISOR", "OPERADOR", "COMPRAS", "INVENTADO")) {
            assertThatThrownBy(() -> usuarioService.crear(solicitud("legacy.prueba@huata.pe", "Secreta2026*", List.of(rol))))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Rol invalido");
        }
    }

    /** Los trece predefinidos: los doce perfiles del cliente mas SECRETARIA, que ya tenia cuentas. */
    @Test
    void enLaBaseEstanTodosLosRolesPredefinidos() {
        assertThat(rolRepository.findAll()).filteredOn(Rol::isSistema).extracting(Rol::getNombre)
                .containsExactlyInAnyOrderElementsOf(
                        MatrizPermisos.ROLES.stream().map(MatrizPermisos.DefinicionRol::codigo).toList());
    }

    @Test
    void noPermiteDejarUnaCuentaSinRol() {
        assertThatThrownBy(() -> usuarioService.crear(solicitud("sin.rol@huata.pe", "Secreta2026*", List.of())))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("al menos un rol");
    }

    private static UsuarioRequest solicitud(String username, String password, List<String> roles) {
        return new UsuarioRequest("Ana Quispe", username, username, "951000111", password, roles);
    }

    @Test
    void crearGuardaLaContrasenaHasheadaYNuncaEnClaro() {
        Usuario creado = usuarioService.crear(solicitud("ana.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));

        assertThat(creado.getPassword()).isNotEqualTo("Secreta2026*");
        assertThat(creado.getPassword()).startsWith("$2");
        assertThat(passwordEncoder.matches("Secreta2026*", creado.getPassword())).isTrue();
    }

    @Test
    void crearAsignaLosRolesPedidos() {
        Usuario creado = usuarioService.crear(
                solicitud("beto.prueba@huata.pe", "Secreta2026*", List.of(Rol.ADMINISTRADOR, Rol.CALIDAD)));

        assertThat(creado.getRoles()).extracting(Rol::getNombre).containsExactlyInAnyOrder(Rol.ADMINISTRADOR, Rol.CALIDAD);
    }

    /** Los permisos son la union de los roles: dos roles suman acceso, nunca lo restan. */
    @Test
    void losPermisosDeUnUsuarioSonLaUnionDeSusRoles() {
        Usuario conDos = usuarioService.crear(
                solicitud("cesar.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD, Rol.SECRETARIA)));

        List<String> permisos = Usuarios.permisosDe(conDos);

        assertThat(permisos).contains(Permiso.CALIDAD_WRITE, Permiso.PAGOS_WRITE);
        assertThat(permisos).doesNotHaveDuplicates();
        assertThat(permisos).isSorted();
    }

    /**
     * El proveedor no entra a ningún módulo interno: su acceso son los endpoints "/me" y, desde V40,
     * la mensajería con la planta. Si un día se le colara otro permiso (por ejemplo acopio.read),
     * vería datos de otros proveedores; por eso se fija la lista exacta y no solo "incluye".
     */
    @Test
    void unProveedorSoloTienePermisosDeMensajeria() {
        Usuario proveedor =
                usuarioService.crear(solicitud("dina.prueba@huata.pe", "Secreta2026*", List.of(Rol.PROVEEDOR)));

        assertThat(Usuarios.permisosDe(proveedor))
                .containsExactlyInAnyOrder(Permiso.MENSAJERIA_READ, Permiso.MENSAJERIA_WRITE);
    }

    @Test
    void noSePuedeDuplicarUnNombreDeUsuario() {
        usuarioService.crear(solicitud("elsa.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));

        assertThatThrownBy(() ->
                        usuarioService.crear(solicitud("elsa.prueba@huata.pe", "Otra2026*", List.of(Rol.CALIDAD))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void crearSinContrasenaSeRechaza() {
        assertThatThrownBy(() -> usuarioService.crear(solicitud("sin.clave@huata.pe", "  ", List.of(Rol.CALIDAD))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("contrasena");
    }

    /**
     * Un rol que no existe en el catalogo no puede asignarse. Aceptarlo en silencio dejaria a la
     * persona autenticada pero sin un solo permiso, y el sintoma seria "la aplicacion no me deja
     * hacer nada" sin ninguna pista de por que.
     */
    @Test
    void unRolInexistenteSeRechaza() {
        assertThatThrownBy(() ->
                        usuarioService.crear(solicitud("fabio.prueba@huata.pe", "Secreta2026*", List.of("JEFE_SUPREMO"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Rol invalido");
    }

    @Test
    void actualizarSinContrasenaConservaLaAnterior() {
        Usuario creado = usuarioService.crear(solicitud("gina.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));
        String hashOriginal = creado.getPassword();

        Usuario editado = usuarioService.actualizar(
                creado.getId(), new UsuarioRequest("Gina Editada", "gina.prueba@huata.pe", "gina.prueba@huata.pe",
                        "951", null, List.of(Rol.ADMINISTRADOR)));

        assertThat(editado.getPassword()).isEqualTo(hashOriginal);
        assertThat(editado.getNombreCompleto()).isEqualTo("Gina Editada");
        assertThat(editado.getRoles()).extracting(Rol::getNombre).containsExactly(Rol.ADMINISTRADOR);
    }

    @Test
    void actualizarConContrasenaLaCambiaYLaHashea() {
        Usuario creado = usuarioService.crear(solicitud("hugo.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));

        Usuario editado = usuarioService.actualizar(
                creado.getId(), solicitud("hugo.prueba@huata.pe", "Nueva2026*", List.of(Rol.CALIDAD)));

        assertThat(passwordEncoder.matches("Nueva2026*", editado.getPassword())).isTrue();
    }

    /** Inactivar es baja logica: el usuario deja de poder entrar y deja de aparecer en el listado. */
    @Test
    void inactivarSacaAlUsuarioDeLaBusquedaYDelLogin() {
        Usuario creado = usuarioService.crear(solicitud("irma.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));

        usuarioService.inactivar(creado.getId());

        assertThat(usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue("irma.prueba@huata.pe"))
                .isEmpty();
        assertThatThrownBy(() -> usuarioService.obtener(creado.getId())).isInstanceOf(NotFoundException.class);
        assertThat(usuarioService.buscar("irma.prueba", PageRequest.of(0, 20)).content())
                .isEmpty();
    }

    @Test
    void laBusquedaPorTextoEncuentraPorNombreYPorUsuario() {
        usuarioService.crear(solicitud("julia.prueba@huata.pe", "Secreta2026*", List.of(Rol.CALIDAD)));

        assertThat(usuarioService.buscar("julia.prueba", PageRequest.of(0, 20)).content())
                .isNotEmpty();
        assertThat(usuarioService.buscar("Ana Quispe", PageRequest.of(0, 20)).content())
                .isNotEmpty();
    }

    @Test
    void obtenerUnUsuarioInexistenteFalla() {
        assertThatThrownBy(() -> usuarioService.obtener(999_999L)).isInstanceOf(NotFoundException.class);
    }
}
