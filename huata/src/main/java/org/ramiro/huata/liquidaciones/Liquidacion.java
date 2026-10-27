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

    /** Bruto: suma de los subtotales de las entregas, antes de ajustes. */
    @Column(nullable = false)
    private Double montoTotal = 0.0;

    /**
     * Suma de los ajustes CON su signo (regla 7.7): negativa si predominan descuentos, penalidades
     * o adelantos; positiva si predominan bonificaciones.
     */
    @Column(nullable = false)
    private Double ajustesTotal = 0.0;

    /** Lo que realmente se paga: montoTotal + ajustesTotal. Es el importe de la boleta. */
    @Column(nullable = false)
    private Double montoNeto = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoLiquidacion estado = EstadoLiquidacion.DRAFT;
}
