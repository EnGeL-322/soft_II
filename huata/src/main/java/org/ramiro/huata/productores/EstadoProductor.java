package org.ramiro.huata.productores;

/**
 * Clasificacion manual del proveedor. No implementa retiro/inactivacion automatica: la politica
 * de reincidencia (C-06, 02_DECISIONES_Y_PENDIENTES.md) todavia no esta aprobada formalmente, asi
 * que el cambio de estado siempre lo decide una persona desde el panel de administracion.
 */
public enum EstadoProductor {
    ACTIVO,
    EN_REVISION,
    INACTIVO
}
