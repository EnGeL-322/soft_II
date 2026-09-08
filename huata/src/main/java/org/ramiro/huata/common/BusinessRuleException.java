package org.ramiro.huata.common;

/** Se lanza cuando una operacion viola una regla de negocio (no un error de validacion de campos). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
