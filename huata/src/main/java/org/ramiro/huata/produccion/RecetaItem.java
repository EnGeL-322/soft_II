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

/** Cuanta materia prima hace falta por cada unidad de queso, segun la receta. */
@Entity
@Table(name = "receta_items")
@Getter
@Setter
@NoArgsConstructor
public class RecetaItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id")
    private RecetaQueso receta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_prima_id")
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_por_unidad", nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidadPorUnidad;
}
