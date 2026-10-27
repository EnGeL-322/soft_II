package org.ramiro.huata.produccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Persona que participa en un lote de produccion de queso (mano de obra).
 *
 * <p>Maestro minimo y acotado a este modulo: no es un usuario del sistema ni comparte tabla con
 * acopiadores/testeadores/proveedores. Unificar a todos los "trabajadores" del CLAUDE.md en una sola
 * tabla generica es un refactor de otros modulos que nadie pidio en esta fase.
 */
@Entity
@Table(name = "trabajadores_produccion")
@Getter
@Setter
@NoArgsConstructor
public class TrabajadorProduccion extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nombre;
}
