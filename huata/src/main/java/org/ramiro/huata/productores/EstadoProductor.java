package org.ramiro.huata.productores;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado del proveedor y las transiciones que se le permiten (DESARROLLO.md 7.3).
 *
 * <pre>
 * (alta)               ---------------------> ACTIVO         (desde 2026-10-26, sin aprobacion)
 * PENDIENTE_APROBACION --aprobacion manual--> ACTIVO         (solo altas anteriores)
 * ACTIVO               --observacion-------->  EN_REVISION
 * ACTIVO              &lt;--&gt;                     INACTIVO      (motivo obligatorio)
 * EN_REVISION          --resuelta------------> ACTIVO
 * RETIRADO                                                   (terminal)
 * </pre>
 *
 * <p><b>Retiro (RN-07), decidido por el cliente el 2026-09-27.</b> Se llega a {@link #RETIRADO} de dos
 * formas: automaticamente, al alcanzar el limite de detecciones de mala calidad configurado
 * ({@code RetiroPorMalaCalidadService}), o a mano desde ACTIVO o EN_REVISION, con motivo, cuando
 * administracion lo decide. Sigue siendo terminal: la readmision no es una transicion.
 *
 * <p><b>Tres aristas que el diagrama de 7.3 no dibuja y aqui se asumen</b>, porque sin ellas el
 * modelo se bloquea. Estan marcadas en el mapa de abajo y conviene confirmarlas:
 * PENDIENTE_APROBACION &rarr; INACTIVO, EN_REVISION &rarr; ACTIVO y EN_REVISION &rarr; INACTIVO.
 */
public enum EstadoProductor {

    /**
     * Alta registrada, todavia sin aprobar. No puede entregar leche. Desde el 2026-10-26 ninguna alta
     * nace asi (ver {@link #inicial()}); queda para los proveedores que ya estaban pendientes, que se
     * siguen aprobando o rechazando con la misma transicion.
     */
    PENDIENTE_APROBACION,

    /** Unico estado que permite recibir recepciones. */
    ACTIVO,

    /** Observado por calidad. Sigue en el padron pero no entrega hasta que se resuelva. */
    EN_REVISION,

    /** Baja administrativa, reversible. */
    INACTIVO,

    /** Retirado por reincidencia (regla 7.2). Terminal: la readmision no es una transicion. */
    RETIRADO;

    /**
     * Transiciones que puede ordenar una persona. Lo que no esta aqui, no se puede hacer: el mapa es
     * la regla, no una sugerencia para la interfaz.
     */
    private static final Map<EstadoProductor, Set<EstadoProductor>> TRANSICIONES_MANUALES = Map.of(
            // ACTIVO es la aprobacion de 7.3. INACTIVO es rechazar el alta: sin esa salida, un alta
            // equivocada quedaria atrapada y habria que aprobar a alguien que no se quiere aprobar
            // solo para poder darlo de baja despues. (Arista asumida, no esta en el diagrama.)
            PENDIENTE_APROBACION, EnumSet.of(ACTIVO, INACTIVO),

            // EN_REVISION la marca hoy una persona. Cuando 7.2 se confirme, la regla automatica
            // usara esta misma arista en vez de una paralela.
            ACTIVO, EnumSet.of(EN_REVISION, INACTIVO, RETIRADO),

            // Volver a ACTIVO es "observacion resuelta". Sin esta salida, un proveedor observado no
            // podria entregar nunca mas. (Arista asumida, no esta en el diagrama.)
            EN_REVISION, EnumSet.of(ACTIVO, INACTIVO, RETIRADO),

            // Reactivacion manual de 7.3.
            INACTIVO, EnumSet.of(ACTIVO),

            // Terminal mientras 7.2 no se decida; cuando exista, sera el unico estado de llegada
            // automatico y seguira sin salida manual.
            RETIRADO, EnumSet.noneOf(EstadoProductor.class));

    /** Cierto si una persona puede llevar al proveedor de este estado a {@code destino}. */
    public boolean permiteIr(EstadoProductor destino) {
        return TRANSICIONES_MANUALES.getOrDefault(this, EnumSet.noneOf(EstadoProductor.class)).contains(destino);
    }

    public Set<EstadoProductor> destinosPosibles() {
        return EnumSet.copyOf(TRANSICIONES_MANUALES.getOrDefault(this, EnumSet.noneOf(EstadoProductor.class)));
    }

    /**
     * Unico estado que habilita a entregar leche (7.3: "un proveedor que no esta Activo no puede
     * recibir nuevas recepciones").
     */
    public boolean puedeEntregar() {
        return this == ACTIVO;
    }

    /**
     * Estado en el que nace un alta. Decision del cliente (2026-10-26): quien da de alta al proveedor
     * (administracion, desde Usuarios o importando el Excel) ya lo revisa al crearlo, asi que nace
     * ACTIVO y puede entregar leche de inmediato, sin un segundo paso de aprobacion. Antes nacia
     * PENDIENTE_APROBACION (7.3).
     */
    public static EstadoProductor inicial() {
        return ACTIVO;
    }
}
