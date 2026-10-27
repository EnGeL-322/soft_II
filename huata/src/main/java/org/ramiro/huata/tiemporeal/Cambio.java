package org.ramiro.huata.tiemporeal;

import java.util.Set;

/**
 * Algo se guardo en la base: que modulo, que entidad, cual y que paso. Es lo unico que viaja por el
 * canal de cambios: nunca datos de negocio (litros, montos, DNI, GPS). Quien lo recibe vuelve a pedir
 * lo que muestra por la API, que valida sus permisos.
 *
 * <p>{@code permisos}, {@code productorId}, {@code acopiadorId} y {@code usuarioId} deciden quien lo
 * recibe y no se envian: un proveedor se entera de lo suyo (sus recojos, su liquidacion) aunque no
 * tenga {@code acopio.read} ni {@code pagos.read}, y una cuenta, de los cambios en sus propios permisos.
 */
public record Cambio(
        String modulo,
        String entidad,
        Long id,
        Accion accion,
        Set<String> permisos,
        Long productorId,
        Long acopiadorId,
        Long usuarioId) {

    public enum Accion {
        CREADO,
        ACTUALIZADO,
        ELIMINADO
    }

    /** Lo que ve el cliente. */
    public record Publico(String modulo, String entidad, Long id, Accion accion) {}

    public Publico publico() {
        return new Publico(modulo, entidad, id, accion);
    }

    /** Clave para no repetir el mismo cambio dos veces en una transaccion (crear y luego actualizar). */
    String clave() {
        return entidad + "#" + id;
    }
}
