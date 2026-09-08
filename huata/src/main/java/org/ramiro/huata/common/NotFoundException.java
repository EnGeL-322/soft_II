package org.ramiro.huata.common;

/** Se lanza cuando se busca un recurso por id y no existe (o esta inactivo). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String entidad, Object id) {
        return new NotFoundException(entidad + " no encontrado(a) con id " + id);
    }
}
