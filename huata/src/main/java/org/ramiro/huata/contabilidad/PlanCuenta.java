package org.ramiro.huata.contabilidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Cuenta del plan contable (PCGE) de la queseria. Sembrada desde el prototipo del cliente
 * (queseria.html); los codigos y nombres deben confirmarse con un contador antes de usarse en libros
 * oficiales (ver CLAUDE.md seccion 33 - preguntas abiertas de facturacion/impuestos).
 */
@Entity
@Table(name = "plan_cuentas")
@Getter
@Setter
@NoArgsConstructor
public class PlanCuenta extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;
}
