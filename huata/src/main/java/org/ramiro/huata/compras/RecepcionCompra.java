package org.ramiro.huata.compras;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Lo que realmente llego de una compra (V61): es lo que mete el insumo al almacen. Una compra puede
 * recibirse en varias partes; quien la recibe queda en {@code createdBy}.
 */
@Entity
@Table(name = "recepciones_compra")
@Getter
@Setter
@NoArgsConstructor
public class RecepcionCompra extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compra_id")
    private Compra compra;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Guia, acta o factura con la que llego. */
    @Column(length = 60)
    private String documento;

    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecepcionItem> items = new ArrayList<>();

    public BigDecimal total() {
        return items.stream().map(RecepcionItem::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Codigo para mostrar: RC-0001. Sale del id, no se guarda. */
    public String codigo() {
        return getId() == null ? null : String.format("RC-%04d", getId());
    }
}
