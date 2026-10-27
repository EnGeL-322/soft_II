package org.ramiro.huata.notificaciones;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final UsuarioRepository usuarioRepository;

    public List<Notificacion> paraUsuarioActual(String username) {
        Usuario usuario = usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + username));
        return notificacionRepository.buscarParaUsuario(usuario.getId());
    }

    @Transactional
    public void crear(Long usuarioId, String titulo, String mensaje, String entidadTipo, Long entidadId) {
        Notificacion notificacion = new Notificacion();
        if (usuarioId != null) {
            notificacion.setUsuario(usuarioRepository.findById(usuarioId).orElse(null));
        }
        notificacion.setTitulo(titulo);
        notificacion.setMensaje(mensaje);
        notificacion.setEntidadTipo(entidadTipo);
        notificacion.setEntidadId(entidadId);
        notificacionRepository.save(notificacion);
    }

    /**
     * Marca leida una notificacion del usuario que la pide.
     *
     * <p>La comprobacion de pertenencia va aqui y no en el controlador porque es una regla de
     * dominio (a quien pertenece una notificacion), no de transporte, y porque asi cualquier otro
     * llamador futuro la hereda. @PreAuthorize no puede cubrirlo: el permiso no depende del rol
     * sino del dato concreto.
     *
     * <p>Responde 404 y no 403 cuando la notificacion es de otra persona: un 403 confirmaria que
     * ese id existe, y los ids son correlativos. Para quien pregunta, una notificacion ajena y una
     * inexistente deben ser indistinguibles.
     */
    @Transactional
    public Notificacion marcarLeida(Long id, String username) {
        Usuario usuario = usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado: " + username));
        Notificacion notificacion =
                notificacionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Notificacion", id));

        // usuario == null es una notificacion general del equipo: cualquiera de sus destinatarios
        // puede marcarla. Ver la deuda anotada debajo sobre el estado "leida" compartido.
        Usuario destinatario = notificacion.getUsuario();
        if (destinatario != null && !destinatario.getId().equals(usuario.getId())) {
            throw NotFoundException.of("Notificacion", id);
        }

        notificacion.setLeida(true);
        return notificacionRepository.save(notificacion);
    }
}
