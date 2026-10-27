package org.ramiro.huata.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Materia prima o insumo de queseria (cuajo, sal, envases...).
 *
 * <p>El stock no esta aqui: se calcula sobre {@link MovimientoInventario}, mismo criterio anti-drift
 * que ya usa {@code LoteQueso} para el stock de queso (un contador que hay que mover a mano en cada
 * compra/consumo/ajuste es un contador que un dia deja de cuadrar sin que nadie pueda decir por que).
 */
@Entity
@Table(name = "materias_primas")
@Getter
@Setter
@NoArgsConstructor
public class MateriaPrima extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 20)
    private UnidadMedida unidadMedida;

    @Column(name = "costo_unitario_referencial", precision = 12, scale = 4)
    private BigDecimal costoUnitarioReferencial;

    /**
     * La leche que llega del acopio (V31). Solo una materia prima la tiene: es la que suma lo que
     * planta recibe de cada ruta y la que las recetas descuentan al producir.
     */
    @Column(name = "leche_acopio", nullable = false)
    private boolean lecheAcopio;
}
