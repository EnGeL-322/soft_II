package org.ramiro.huata.produccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Producto terminado que la planta fabrica y vende: queso fresco, paria, yogur, manjar...
 *
 * <p>Antes era el enum fijo {@code TipoQueso} (tres quesos). Pasar a catalogo es lo que permite que
 * la planta produzca otra cosa de la leche sin tocar codigo: cada producto tiene su receta (y con
 * ella sus materias primas), sus lotes y su lista de precios.
 *
 * <p>No se borra: se da de baja ({@code activo = false}). Recetas, lotes, ventas y precios viejos lo
 * referencian, y un producto dado de baja solo deja de ofrecerse para producir o poner precio; lo que
 * quede en camara se puede seguir vendiendo.
 */
@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
public class Producto extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    /**
     * Como se cuenta lo que sale del lote ("unidad", "botella 1 L", "balde 4 kg"). Es una etiqueta:
     * el stock y las ventas siguen contandose en unidades enteras.
     */
    @Column(name = "unidad_venta", nullable = false, length = 40)
    private String unidadVenta;

    @Column(length = 500)
    private String descripcion;

    /**
     * Cuantos dias dura desde que se produce (V33). Con esto cada lote nace con su fecha de
     * vencimiento. Nulo = no se sabe / no vence: no se inventa una fecha.
     */
    @Column(name = "vida_util_dias")
    private Integer vidaUtilDias;

    /** Codigo para mostrar: P-0001. Sale del id, no se guarda. */
    public String codigo() {
        return getId() == null ? null : String.format("P-%04d", getId());
    }
}
