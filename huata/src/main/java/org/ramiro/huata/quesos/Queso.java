package org.ramiro.huata.quesos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Producción/venta de quesos. Aparece en prototipos historicos de Huata (modulo "Producción ·
 * Quesos") pero no en el alcance oficial del Charter; se implementa igualmente a pedido explicito,
 * dejando "totalVenta" nullable porque el propio prototipo reconoce que el monto de venta no
 * siempre tiene evidencia registrada.
 */
@Entity
@Table(name = "quesos")
@Getter
@Setter
@NoArgsConstructor
public class Queso extends BaseEntity {

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 120)
    private String fuente;

    @Column(nullable = false)
    private Double producidosKg;

    @Column(nullable = false)
    private Double vendidosKg = 0.0;

    /** Null = "no visible en evidencia" (mismo criterio del prototipo), no se inventa un monto. */
    private Double totalVenta;

    @Column(length = 500)
    private String observaciones;
}
