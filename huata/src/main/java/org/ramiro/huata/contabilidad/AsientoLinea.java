package org.ramiro.huata.contabilidad;

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

/** Una linea debe/haber de un {@link AsientoContable}. */
@Entity
@Table(name = "asiento_lineas")
@Getter
@Setter
@NoArgsConstructor
public class AsientoLinea extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asiento_id")
    private AsientoContable asiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id")
    private PlanCuenta cuenta;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal debe = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal haber = BigDecimal.ZERO;
}
