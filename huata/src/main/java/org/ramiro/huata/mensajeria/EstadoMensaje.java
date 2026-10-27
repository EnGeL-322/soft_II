package org.ramiro.huata.mensajeria;

/** Lo que el servidor sabe de un mensaje. "Pendiente" y "error" viven solo en la cola del cliente. */
public enum EstadoMensaje {
    ENVIADO,
    ENTREGADO,
    LEIDO
}
