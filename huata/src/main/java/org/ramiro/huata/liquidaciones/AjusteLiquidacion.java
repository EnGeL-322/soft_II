package org.ramiro.huata.liquidaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Ajuste manual sobre una liquidacion: descuento de queso, penalidad, adelanto o bonificacion
 * (DESARROLLO.md regla 7.7).
 *
 * <p>El monto se guarda SIEMPRE en positivo; el signo lo pone {@link TipoAjuste#aplicar(double)}.
 * Guardar el signo en el dato ademas del tipo abriria la puerta a que ambos se contradigan.
 */
@Entity
@Table(name = "ajustes_liquidacion")
@Getter
@Setter
@NoArgsConstructor
public class AjusteLiquidacion extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "liquidacion_id")
    private Liquidacion liquidacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoAjuste tipo;

    /** Importe en positivo. */
    @Column(nullable = false)
    private Double monto;

    @Column(length = 200)
    private String motivo;

    /** Importe con el signo que le corresponde al tipo. */
    public double montoConSigno() {
        return tipo.aplicar(monto);
    }
}
