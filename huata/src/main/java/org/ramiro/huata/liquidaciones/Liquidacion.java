package org.ramiro.huata.liquidaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;

@Entity
@Table(name = "liquidaciones")
@Getter
@Setter
@NoArgsConstructor
public class Liquidacion extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id")
    private Productor productor;

    @Column(nullable = false)
    private LocalDate periodoInicio;

    @Column(nullable = false)
    private LocalDate periodoFin;

    @Column(nullable = false)
    private Double litrosTotales = 0.0;

    @Column(nullable = false)
    private Double montoTotal = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLiquidacion estado = EstadoLiquidacion.DRAFT;
}
