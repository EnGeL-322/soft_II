package org.ramiro.huata.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Rango de referencia configurable (densidad, punto de congelacion, acidez, grasa, solidos...).
 * Nunca se hardcodean estos numeros en el codigo (regla del proyecto, seccion 5 y 11 del contexto):
 * viven aqui y son editables desde Configuracion.
 */
@Entity
@Table(name = "parametros_calidad")
@Getter
@Setter
@NoArgsConstructor
public class ParametroCalidad extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String nombre;

    private Double valorMin;

    private Double valorMax;

    @Column(length = 40)
    private String unidad;

    @Column(length = 300)
    private String descripcion;
}
