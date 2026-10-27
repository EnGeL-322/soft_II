package org.ramiro.huata.mensajeria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class MensajeriaDtos {
    private MensajeriaDtos() {}

    /**
     * {@code clientUuid} es opcional (los clientes viejos no lo mandan). Cuando llega, es la llave de
     * idempotencia: el mismo valor dos veces guarda un solo mensaje.
     */
    public record EnviarMensajeRequest(@NotBlank @Size(max = 1000) String cuerpo, @Size(max = 64) String clientUuid) {
        public EnviarMensajeRequest(String cuerpo) {
            this(cuerpo, null);
        }
    }
    public record EnviarAvisoRequest(@NotBlank @Size(max = 1000) String cuerpo, String destino,
            String zona, List<Long> productorIds) {}
    public record AvisoEnviadoResponse(int destinatarios) {}
    public record ConversacionResponse(Long productorId, String productorCodigo, String productorNombre,
            String zona, String telefono, Long mensajesSinLeer, Instant ultimoMensajeAt, String ultimoMensaje) {}

    /**
     * {@code leidoEn}: cuando lo leyo quien mira (igual que antes). {@code estado}: como va el mensaje
     * frente a su destinatario (enviado, entregado, leido), que es lo que pintan los checks.
     * {@code productorId} dice de que conversacion es, que el canal en vivo necesita.
     */
    public record MensajeResponse(Long id, Long autorId, String autorNombre, boolean mio,
            String cuerpo, Instant creadoEn, Instant leidoEn,
            Long productorId, String clientUuid, EstadoMensaje estado) {
        /** El mismo mensaje visto por otra persona: solo cambia si es "mio". */
        public MensajeResponse paraUsuario(Long usuarioId) {
            return new MensajeResponse(id, autorId, autorNombre, autorId.equals(usuarioId), cuerpo, creadoEn,
                    leidoEn, productorId, clientUuid, estado);
        }
    }
    public record ConversacionDetalleResponse(ConversacionResponse conversacion, List<MensajeResponse> mensajes) {}
}
