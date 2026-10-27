package org.ramiro.huata.mensajeria;

import java.time.Instant;
import java.util.List;

/**
 * Lo que la mensajeria avisa al canal en vivo. Se publican como eventos de Spring y el canal los
 * difunde DESPUES del commit (ver {@link MensajeriaTiempoRealPublisher}): difundir dentro de la
 * transaccion mostraria un mensaje que todavia puede revertirse.
 */
public final class MensajeriaEventos {
    private MensajeriaEventos() {}

    /** Se guardo un mensaje nuevo (de la planta o del proveedor). */
    public record MensajeGuardado(Long mensajeId) {}

    /** Unos mensajes de una conversacion pasaron a entregado o leido. */
    public record EstadoCambiado(Long productorId, List<Long> mensajeIds, EstadoMensaje estado, Instant en) {}
}
