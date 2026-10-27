package org.ramiro.huata.rutas;

/**
 * Para que sirve un trazado pintado por el acopiador.
 *
 * <p>{@link #DIA} es el recorrido de una fecha concreta: lo pinta el acopiador en la app antes o
 * durante su jornada, y es un hecho de campo. {@link #PREDETERMINADO} es su recorrido habitual, que
 * administracion crea copiando uno del dia. No hay un tercer estado "propuesto": un trazado del dia
 * vale por si mismo aunque nunca llegue a ser predeterminado.
 */
public enum TipoTrazado {
    DIA,
    PREDETERMINADO
}
