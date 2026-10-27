package org.ramiro.huata.compras;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Proveedor de materia prima/insumos de queseria (ferreteria, quimicos, envases...).
 *
 * <p>No es el mismo concepto que {@code Productor} (proveedor de leche): son negocios distintos que
 * comparten la palabra "proveedor" en espanol. Mantenerlos separados evita que una compra de sal
 * termine mezclada con la ficha de un productor de leche.
 */
@Entity
@Table(name = "proveedores_compra")
@Getter
@Setter
@NoArgsConstructor
public class ProveedorCompra extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String contacto;

    @Column(length = 30)
    private String documento;

    @Column(length = 500)
    private String observaciones;
}
