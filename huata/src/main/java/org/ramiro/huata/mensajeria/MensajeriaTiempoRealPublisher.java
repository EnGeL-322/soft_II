package org.ramiro.huata.mensajeria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Lleva al canal en vivo lo que la mensajeria acaba de guardar.
 *
 * <p><b>AFTER_COMMIT</b>, igual que el canal de acopio: difundir dentro de la transaccion mostraria un
 * mensaje que todavia puede revertirse, y el destinatario no tendria forma de enterarse.
 *
 * <p><b>Un fallo aqui no rompe el envio.</b> El mensaje ya esta guardado: si el socket falla, el
 * destinatario lo recibe igual al reconectar (SYNC) o al abrir la conversacion. Propagar la excepcion
 * solo le devolveria un error a quien escribio, y su cola offline lo reintentaria sin necesidad.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MensajeriaTiempoRealPublisher {

    private final MensajeriaService servicio;
    private final MensajeriaWebSocketHandler canal;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alGuardarMensaje(MensajeriaEventos.MensajeGuardado evento) {
        if (canal.oyentes() == 0) return;
        try {
            canal.publicarMensaje(servicio.publicable(evento.mensajeId()));
        } catch (RuntimeException e) {
            log.warn("No se pudo difundir el mensaje {}: {}", evento.mensajeId(), e.toString());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alCambiarEstado(MensajeriaEventos.EstadoCambiado evento) {
        if (canal.oyentes() == 0) return;
        try {
            canal.publicarEstado(evento);
        } catch (RuntimeException e) {
            log.warn("No se pudo difundir el estado de {} mensajes: {}", evento.mensajeIds().size(), e.toString());
        }
    }
}
