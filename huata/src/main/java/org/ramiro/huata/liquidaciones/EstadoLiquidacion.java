package org.ramiro.huata.liquidaciones;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado de la liquidacion y las transiciones que se le permiten.
 *
 * <pre>
 * DRAFT       --calcular------------> CALCULATED   (no es una transicion manual: la hace el calculo)
 * CALCULATED  --revisar-------------> REVIEWED
 * CALCULATED  --pagar--------------->  CLOSED      (Pago unico y Pagar a todos)
 * REVIEWED    --pagar--------------->  CLOSED
 * CLOSED                                           (terminal)
 * </pre>
 *
 * <p><b>Por que CLOSED es terminal.</b> Una liquidacion cerrada es dinero ya entregado al proveedor:
 * reabrirla permitiria recalcular un importe que la cooperativa ya pago, y el recalculo cambiaria el
 * numero sin dejar rastro de cual fue el pagado. Una correccion posterior se hace con un ajuste
 * (regla 7.7) en el ciclo siguiente, que si queda registrado. Ademas no existe ningun permiso de
 * reapertura en el catalogo: nadie podria autorizarla aunque el estado lo admitiera.
 *
 * <p><b>Por que CALCULATED puede ir directo a CLOSED sin pasar por REVIEWED.</b> Es lo que hacen
 * "Pago unico" y "Pagar a todos": calculan y cobran en el acto. REVIEWED es un paso opcional de
 * revision previa, no un tramite obligatorio; forzarlo bloquearia las dos pantallas de Secretaria.
 *
 * <p><b>La unica vuelta atras la hace el recalculo, no una persona.</b> {@code calcular()} sobre una
 * liquidacion REVIEWED la deja otra vez en CALCULATED: si los importes se rehacen, la revision
 * anterior ya no revisa nada. Por eso REVIEWED &rarr; CALCULATED no esta en el mapa de transiciones
 * manuales: no es algo que se pida, es una consecuencia de recalcular.
 *
 * <p>DRAFT solo existe como valor inicial de la entidad: {@code LiquidacionService.calcular} deja la
 * liquidacion en CALCULATED en cuanto la guarda, asi que no hay ninguna transicion manual que salga
 * de DRAFT ni ninguna que vuelva a el.
 */
public enum EstadoLiquidacion {

    /** Recien creada, sin calcular. No es alcanzable por la API: el calculo la deja en CALCULATED. */
    DRAFT,

    /** Calculada con la tarifa vigente. Admite ajustes y recalculo. */
    CALCULATED,

    /** Revisada antes de pagar. Sigue admitiendo ajustes: revisar no es cerrar. */
    REVIEWED,

    /** Pagada. Terminal: ni ajustes, ni recalculo, ni vuelta atras. */
    CLOSED;

    /**
     * Transiciones que puede ordenar una persona. Lo que no esta aqui, no se puede hacer: el mapa es
     * la regla, no una sugerencia para la interfaz.
     */
    private static final Map<EstadoLiquidacion, Set<EstadoLiquidacion>> TRANSICIONES_MANUALES = Map.of(
            // El calculo es quien saca de DRAFT, y lo hace escribiendo el estado junto con los
            // importes. Permitir "pasar a CALCULATED" a mano dejaria una liquidacion marcada como
            // calculada con los totales en cero.
            DRAFT, EnumSet.noneOf(EstadoLiquidacion.class),
            CALCULATED, EnumSet.of(REVIEWED, CLOSED),
            REVIEWED, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(EstadoLiquidacion.class));

    /** Cierto si una persona puede llevar la liquidacion de este estado a {@code destino}. */
    public boolean permiteIr(EstadoLiquidacion destino) {
        return TRANSICIONES_MANUALES.getOrDefault(this, EnumSet.noneOf(EstadoLiquidacion.class)).contains(destino);
    }

    public Set<EstadoLiquidacion> destinosPosibles() {
        return EnumSet.copyOf(TRANSICIONES_MANUALES.getOrDefault(this, EnumSet.noneOf(EstadoLiquidacion.class)));
    }

    /**
     * Cierto mientras el importe todavia se puede tocar (ajustes de 7.7 y recalculo). Unica
     * definicion de "liquidacion abierta" del modulo.
     */
    public boolean admiteCambios() {
        return this != CLOSED;
    }
}
