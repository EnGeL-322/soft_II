package org.ramiro.huata.produccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.quesos.LoteQueso;

/**
 * Que y cuanto se consumio REALMENTE de una materia prima en un lote (no lo que la receta sugeria
 * como referencia: lo que la persona realmente registro haber usado). Genera un movimiento
 * {@code SALIDA_PRODUCCION_CONSUMO} en el kardex.
 *
 * <p>{@code costoUnitarioMomento} se congela al consumir, misma filosofia que
 * {@code VentaQueso.precioUnitario}: el costo de este lote no cambia si mas tarde cambia el costo de
 * referencia de la materia prima.
 */
@Entity
@Table(name = "lote_consumo")
@Getter
@Setter
@NoArgsConstructor
public class LoteConsumo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id")
    private LoteQueso lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_prima_id")
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_consumida", nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidadConsumida;

    @Column(name = "costo_unitario_momento", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitarioMomento;

    public BigDecimal costoTotal() {
        return cantidadConsumida.multiply(costoUnitarioMomento);
    }
}
