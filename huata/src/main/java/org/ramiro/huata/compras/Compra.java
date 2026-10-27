package org.ramiro.huata.compras;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Cabecera de una compra de materia prima/insumos.
 *
 * <p>El total no se guarda: se calcula sumando {@link CompraItem#total()} de sus items, igual
 * criterio que {@code VentaQueso.total()} — un total guardado es un tercer numero que puede
 * contradecir a los items que lo explican.
 */
@Entity
@Table(name = "compras")
@Getter
@Setter
@NoArgsConstructor
public class Compra extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_compra_id")
    private ProveedorCompra proveedor;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(length = 60)
    private String documento;

    @Column(length = 500)
    private String observaciones;

    /** Por que se anulo (V59). Una compra anulada queda con activo = false; no se borra. */
    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CompraItem> items = new ArrayList<>();

    public java.math.BigDecimal total() {
        return items.stream().map(CompraItem::total).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
