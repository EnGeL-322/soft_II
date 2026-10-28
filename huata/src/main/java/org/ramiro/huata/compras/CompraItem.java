package org.ramiro.huata.compras;

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

/** Una linea de compra: cuanto se compro de una materia prima y a que costo unitario. */
@Entity
@Table(name = "compra_items")
@Getter
@Setter
@NoArgsConstructor
public class CompraItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_prima_id")
    private MateriaPrima materiaPrima;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidad;

    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitario;

    /** Cuanto de esta linea ya llego al almacen (V61). Nunca mas que lo comprado. */
    @Column(name = "cantidad_recibida", nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;

    public BigDecimal total() {
        return cantidad.multiply(costoUnitario);
    }

    public BigDecimal totalRecibido() {
        return cantidadRecibida.multiply(costoUnitario);
    }

    public BigDecimal pendiente() {
        return cantidad.subtract(cantidadRecibida);
    }
}
