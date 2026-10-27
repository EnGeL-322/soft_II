package org.ramiro.huata.ivr;

/**
 * Un pedido del IVR que no se puede cumplir por el estado actual (ya tiene una solicitud pendiente,
 * ya esta en esa zona...). Responde 409 con un {@code codigo} estable: el flujo de voz elige el audio
 * por el codigo, no leyendo el mensaje.
 */
class ConflictoIvrException extends RuntimeException {
    private final String codigo;

    ConflictoIvrException(String codigo, String texto) {
        super(texto);
        this.codigo = codigo;
    }

    String codigo() {
        return codigo;
    }
}
