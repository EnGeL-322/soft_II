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

    @Transactional
    public Notificacion marcarLeida(Long id) {
        Notificacion notificacion = notificacionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Notificacion", id));
        notificacion.setLeida(true);
        return notificacionRepository.save(notificacion);
    }
}
