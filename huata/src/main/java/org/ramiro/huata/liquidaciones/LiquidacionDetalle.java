package org.ramiro.huata.liquidaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.recojo.Recojo;

@Entity
@Table(name = "liquidacion_detalles")
@Getter
@Setter
@NoArgsConstructor
public class LiquidacionDetalle extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "liquidacion_id")
    private Liquidacion liquidacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recojo_id")
    private Recojo recojo;

    @Column(nullable = false)
    private Double litros;

    @Column(nullable = false)
    private Double precioAplicado;

    @Column(nullable = false)
    private Double subtotal;
}
