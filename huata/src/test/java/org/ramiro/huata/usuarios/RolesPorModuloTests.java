package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Usuarios y roles" crea todos los roles MENOS Acopiador y Proveedor; esos dos nacen en su propio
 * modulo, junto con el registro de negocio que les da sentido.
 *
 * <p>La regla vive en el servicio y no solo en el selector de la web: ocultar una opcion no es
 * control (CLAUDE.md seccion 0, RNF-15). Lo que se prueba aqui es que la puerta de administracion
 * rechace esos roles y que la puerta de los modulos los siga aceptando.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class RolesPorModuloTests {

    @Autowired
    private UsuarioService usuarioService;

    private UsuarioRequest solicitud(String username, List<String> roles) {
        return new UsuarioRequest("Persona de prueba", username, username, "951000111", "Clave2026*", roles);
    }

    @Autowired
    private RolService rolService;

    @Test
    void elCatalogoDeUsuariosNoOfreceAcopiadorNiProveedor() {
        assertThat(rolService.asignablesEnUsuarios())
                .doesNotContain(Rol.ACOPIADOR, Rol.PROVEEDOR)
                .containsExactlyInAnyOrderElementsOf(rolService.listar().stream()
                        .map(RolResponse::nombre)
                        .filter(rol -> !Rol.GESTIONADOS_EN_SU_MODULO.contains(rol))
                        .toList());
    }

    @Test
    void crearUnUsuarioConRolAcopiadorDesdeAdministracionFalla() {
        var req = solicitud("acopiador.colado" + System.nanoTime() + "@huata.pe", List.of(Rol.ACOPIADOR));

        assertThatThrownBy(() -> usuarioService.crearDesdeAdministracion(req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("su propio modulo");
    }

    @Test
    void crearUnUsuarioConRolProveedorDesdeAdministracionFalla() {
        var req = solicitud("proveedor.colado" + System.nanoTime() + "@huata.pe", List.of(Rol.PROVEEDOR));

        assertThatThrownBy(() -> usuarioService.crearDesdeAdministracion(req))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void colarElRolProhibidoJuntoAOtroPermitidoTampocoFunciona() {
        var req = solicitud(
                "mixto" + System.nanoTime() + "@huata.pe", List.of(Rol.CALIDAD, Rol.ACOPIADOR));

        assertThatThrownBy(() -> usuarioService.crearDesdeAdministracion(req))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void losDemasRolesSiSeCreanDesdeAdministracion() {
        for (String rol : rolService.asignablesEnUsuarios()) {
            var req = solicitud(rol.toLowerCase() + System.nanoTime() + "@huata.pe", List.of(rol));
            assertThatCode(() -> usuarioService.crearDesdeAdministracion(req)).doesNotThrowAnyException();
        }
    }

    /**
     * La restriccion es de por donde entra el alta, no del rol: Acopiadores y Proveedores siguen
     * creando su cuenta por {@link UsuarioService#sincronizarCuentaDeAcceso}.
     */
    @Test
    void elModuloDeAcopiadoresSiPuedeCrearLaCuentaConRolAcopiador() {
        String username = "acopiador.legitimo" + System.nanoTime() + "@huata.pe";

        Usuario creado = usuarioService.sincronizarCuentaDeAcceso(
                null, "Acopiador Legitimo", username, "951000111", "Clave2026*", Rol.ACOPIADOR);

        assertThat(creado.getRoles()).extracting(Rol::getNombre).containsExactly(Rol.ACOPIADOR);
    }

    @Test
    void administracionNoPuedeEditarLaCuentaDeUnAcopiadorYQuitarleSuRol() {
        String username = "acopiador.editable" + System.nanoTime() + "@huata.pe";
        Usuario acopiador = usuarioService.sincronizarCuentaDeAcceso(
                null, "Acopiador Editable", username, "951000111", "Clave2026*", Rol.ACOPIADOR);
        var req = solicitud(username, List.of(Rol.CALIDAD));

        assertThatThrownBy(() -> usuarioService.actualizarDesdeAdministracion(acopiador.getId(), req))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("su propio modulo");
    }

    @Test
    void administracionSiEditaUnaCuentaQueNoEsDeOtroModulo() {
        String username = "calidad" + System.nanoTime() + "@huata.pe";
        Usuario creado = usuarioService.crearDesdeAdministracion(solicitud(username, List.of(Rol.CALIDAD)));

        Usuario actualizado = usuarioService.actualizarDesdeAdministracion(
                creado.getId(), solicitud(username, List.of(Rol.SECRETARIA)));

        assertThat(actualizado.getRoles()).extracting(Rol::getNombre).containsExactly(Rol.SECRETARIA);
    }
}
