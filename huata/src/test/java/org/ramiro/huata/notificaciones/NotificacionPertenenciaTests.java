package org.ramiro.huata.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nadie puede marcar como leida la notificacion de otra persona.
 *
 * <p>El id viaja en la URL y es correlativo, asi que sin comprobacion de pertenencia bastaba con
 * probar numeros para tocar el estado de cualquier usuario. La regla vive en el service, no en el
 * controlador, porque depende del dato y no del rol: ningun @PreAuthorize puede expresarla.
 */
@SpringBootTest
@Transactional
class NotificacionPertenenciaTests {

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Usuario ana;
    private Usuario beto;

    @BeforeEach
    void crearDosUsuarios() {
        ana = nuevoUsuario("ana.prueba@huata.pe");
        beto = nuevoUsuario("beto.prueba@huata.pe");
    }

    @Test
    void elDuenoPuedeMarcarSuPropiaNotificacion() {
        // Arrange
        Notificacion suya = notificacionDe(ana);

        // Act
        Notificacion resultado = notificacionService.marcarLeida(suya.getId(), ana.getUsername());

        // Assert
        assertThat(resultado.isLeida()).isTrue();
    }

    @Test
    void nadiePuedeMarcarLaNotificacionDeOtro() {
        // Arrange
        Notificacion deAna = notificacionDe(ana);

        // Act / Assert: 404 y no 403, para no confirmar que ese id existe.
        assertThatThrownBy(() -> notificacionService.marcarLeida(deAna.getId(), beto.getUsername()))
                .isInstanceOf(NotFoundException.class);

        // Y sobre todo: el estado ajeno no cambio.
        assertThat(notificacionRepository.findById(deAna.getId()).orElseThrow().isLeida())
                .isFalse();
    }

    @Test
    void unIdInexistenteYUnaNotificacionAjenaSonIndistinguibles() {
        // Arrange
        Notificacion deAna = notificacionDe(ana);
        long idInexistente = deAna.getId() + 10_000;

        // Act
        Class<?> ajena = capturar(() -> notificacionService.marcarLeida(deAna.getId(), beto.getUsername()));
        Class<?> inexistente = capturar(() -> notificacionService.marcarLeida(idInexistente, beto.getUsername()));

        // Assert
        assertThat(ajena).isEqualTo(inexistente);
    }

    @Test
    void unaNotificacionGeneralLaPuedeMarcarCualquierDestinatario() {
        // Arrange: usuario null = aviso para todo el equipo.
        Notificacion general = notificacionDe(null);

        // Act / Assert
        assertThat(notificacionService
                        .marcarLeida(general.getId(), beto.getUsername())
                        .isLeida())
                .isTrue();
    }

    private static Class<?> capturar(Runnable accion) {
        try {
            accion.run();
            return null;
        } catch (RuntimeException ex) {
            return ex.getClass();
        }
    }

    private Usuario nuevoUsuario(String username) {
        Usuario usuario = new Usuario();
        usuario.setNombreCompleto(username);
        usuario.setUsername(username);
        usuario.setEmail(username);
        usuario.setPassword(passwordEncoder.encode("Huata2026*"));
        return usuarioRepository.save(usuario);
    }

    private Notificacion notificacionDe(Usuario destinatario) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTitulo("Prueba");
        notificacion.setMensaje("Contenido de prueba");
        return notificacionRepository.save(notificacion);
    }
}
