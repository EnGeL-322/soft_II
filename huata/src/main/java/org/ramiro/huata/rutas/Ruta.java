package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Ruta de recojo. La "zona" es un campo de texto libre y configurable a proposito: la decision
 * tecnica (C-05, 02_DECISIONES_Y_PENDIENTES.md) fue no codificar una lista fija de zonas.
 */
@Entity
@Table(name = "rutas")
@Getter
@Setter
@NoArgsConstructor
public class Ruta extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 120)
    private String zona;

    @Column(length = 300)
    private String descripcion;
}
