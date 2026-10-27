package org.ramiro.huata.common;

/**
 * El cliente hizo mas peticiones de las permitidas en la ventana. Se traduce a 429 en
 * {@link ApiExceptionHandler}.
 *
 * <p>Va aparte de {@link BusinessRuleException} porque no es una regla del negocio: la peticion es
 * legitima, lo que sobra es el ritmo. Mezclarlas daria 422 y el cliente no sabria que basta con
 * esperar.
 */
public class DemasiadasSolicitudesException extends RuntimeException {

    public DemasiadasSolicitudesException(String mensaje) {
        super(mensaje);
    }
}
