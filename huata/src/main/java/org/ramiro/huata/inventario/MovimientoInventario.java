package org.ramiro.huata.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.quesos.LoteQueso;

/**
 * Una fila del kardex: entrada o salida de UNA materia prima o de UN lote de queso terminado (nunca
 * ambos, nunca ninguno — ver {@code movimientos_inventario_item_unico} en V13).
 *
 * <p>No se edita ni se borra: una anulacion de venta o un ajuste insertan una fila nueva que compensa
 * a la anterior. {@link #cantidad} siempre es positiva; la direccion la decide {@link #tipo}
 * ({@link TipoMovimientoInventario#esEntrada()}).
 */
@Entity
@Table(name = "movimientos_inventario")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoInventario extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimientoInventario tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "materia_prima_id")
    private MateriaPrima materiaPrima;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_queso_id")
    private LoteQueso loteQueso;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private LocalDate fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_origen_tipo", nullable = false, length = 20)
    private DocumentoOrigenTipo documentoOrigenTipo;

    @Column(name = "documento_origen_id", nullable = false)
    private Long documentoOrigenId;

    @Column(nullable = false, length = 120)
    private String usuario;

    @Column(length = 300)
    private String observaciones;
}
