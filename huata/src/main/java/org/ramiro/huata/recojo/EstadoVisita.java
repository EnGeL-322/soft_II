package org.ramiro.huata.recojo;

/**
 * Blanco/pendiente NO es un valor de este enum: es la ausencia de un {@link Recojo} del dia para un
 * proveedor, calculada por el cliente (comparar la lista de proveedores de la ruta contra los
 * recojos ya registrados). Verde/acopiado se infiere de un Recojo con litros &gt; 0. Lo unico que
 * hace falta persistir es el caso rojo: una visita real que no resulto en leche.
 */
public enum EstadoVisita {
    ACOPIADO,
    NO_ENTREGADO
}
