package org.ramiro.huata.compras;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/** Cuanto llego de una linea de compra, con el lote y el vencimiento que trae el proveedor. */
@Entity
@Table(name = "recepcion_items")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recepcion_id")
    private RecepcionCompra recepcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_item_id")
    private CompraItem compraItem;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidad;

    @Column(length = 60)
    private String lote;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    public BigDecimal total() {
        return cantidad.multiply(compraItem.getCostoUnitario());
    }
}
