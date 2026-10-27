package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La cuenta del acopiador se edita en su modulo, que no maneja permisos: antes no habia forma de
 * concederle una accion puntual (p. ej. aprobar calidad). Ahora se cambian solo sus excepciones,
 * sin tocar su rol ni sus datos. El proveedor sigue sin poder editarse.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin.pruebas@huata.pe")
class ExcepcionesDeCuentasDeModuloTests {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario cuenta(String username) {
        return usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(username).orElseThrow();
    }

    @Test
    void aUnAcopiadorSeLePuedeConcederAprobarCalidadSinTocarSuRol() {
        Usuario acopiador = cuenta("acopiador.demo@huata.pe");

        Usuario guardado = usuarioService.actualizarExcepciones(
                acopiador.getId(), List.of(Permiso.CALIDAD_APROBAR), List.of());

        assertThat(Usuarios.permisosDe(guardado)).contains(Permiso.CALIDAD_APROBAR);
        assertThat(guardado.getRoles()).extracting(Rol::getNombre).containsExactly(Rol.ACOPIADOR);
    }

    @Test
    void losPermisosDeUnProveedorNoSeEditan() {
        Usuario proveedor = cuenta("proveedor.demo@huata.pe");

        assertThatThrownBy(() -> usuarioService.actualizarExcepciones(
                        proveedor.getId(), List.of(Permiso.CALIDAD_READ), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se editan");
    }
}
