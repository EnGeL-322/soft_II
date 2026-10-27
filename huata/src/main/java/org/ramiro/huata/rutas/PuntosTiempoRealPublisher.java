package org.ramiro.huata.rutas;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PuntosTiempoRealPublisher {
    private final SimpMessagingTemplate mensajes;

    record Evento(String tipo, PuntoEstablecidoResponse punto) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicar(PuntoCambiado cambio) {
        Evento evento = new Evento(cambio.tipo(), cambio.punto());
        Set<Long> rutas = new HashSet<>();
        rutas.add(cambio.rutaAnterior());
        rutas.add(cambio.punto().rutaId());
        for (Long ruta : rutas) mensajes.convertAndSend("/topic/rutas/" + ruta + "/puntos", evento);
        Set<String> usuarios = new HashSet<>();
        if (cambio.usuarioAnterior() != null) usuarios.add(cambio.usuarioAnterior());
        if (cambio.usuarioActual() != null) usuarios.add(cambio.usuarioActual());
        for (String usuario : usuarios) mensajes.convertAndSendToUser(usuario, "/queue/puntos", evento);
    }
}
