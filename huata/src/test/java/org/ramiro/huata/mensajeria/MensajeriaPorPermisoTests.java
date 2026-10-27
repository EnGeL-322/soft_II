package org.ramiro.huata.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.usuarios.PermisoRepository;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioPermiso;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bandeja de planta la abre quien tiene mensajeria.read, y enviar exige mensajeria.write. Antes
 * se decidia por el nombre del rol (Administrador o Secretaria): conceder el permiso a otra persona
 * desde "Usuarios y roles" no tenia ningun efecto.
 */
@SpringBootTest
@Transactional
class MensajeriaPorPermisoTests {

    /** Rol CALIDAD: no trae ningun permiso de mensajeria. */
    private static final String ANALISTA = "calidad.demo@huata.pe";

    @Autowired
    private MensajeriaService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PermisoRepository permisoRepository;

    @Autowired
    private ProductorRepository productorRepository;

    private void conceder(String... codigos) {
        Usuario analista = usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(ANALISTA).orElseThrow();
        for (String codigo : codigos) {
            analista.getPermisosPersonalizados().add(new UsuarioPermiso(
                    analista, permisoRepository.findByCodigo(codigo).orElseThrow(), UsuarioPermiso.Tipo.CONCEDER));
        }
        usuarioRepository.saveAndFlush(analista);
    }

    private Long unProveedor() {
        return productorRepository.findByUsuario_UsernameIgnoreCase("proveedor.demo@huata.pe").orElseThrow().getId();
    }

    @Test
    void sinElPermisoNoSeAbreLaBandeja() {
        assertThatThrownBy(() -> service.listar(ANALISTA, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void concederLecturaAbreLaBandejaAunqueElRolNoSeaAdministracionNiSecretaria() {
        conceder(Permiso.MENSAJERIA_READ);

        assertThat(service.listar(ANALISTA, null)).isNotEmpty();
    }

    @Test
    void conSoloLecturaNoSePuedeEnviar() {
        conceder(Permiso.MENSAJERIA_READ);

        assertThatThrownBy(() -> service.enviarAProveedor(
                        unProveedor(), new MensajeriaDtos.EnviarMensajeRequest("Hola"), ANALISTA))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void conLecturaYEscrituraSeEnvia() {
        conceder(Permiso.MENSAJERIA_READ, Permiso.MENSAJERIA_WRITE);

        assertThat(service.enviarAProveedor(
                        unProveedor(), new MensajeriaDtos.EnviarMensajeRequest("Hola"), ANALISTA).cuerpo())
                .isEqualTo("Hola");
    }
}
