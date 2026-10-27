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
import org.ramiro.huata.quesos.LoteQueso;

/**
 * Que trabajador participo en un lote y cuanto costo su mano de obra. La mano de obra es un
 * costo/recurso de produccion, no un item de inventario: no genera ningun movimiento de kardex.
 */
@Entity
@Table(name = "lote_mano_obra")
@Getter
@Setter
@NoArgsConstructor
public class LoteManoObra extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lote_id")
    private LoteQueso lote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trabajador_produccion_id")
    private TrabajadorProduccion trabajador;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
}
