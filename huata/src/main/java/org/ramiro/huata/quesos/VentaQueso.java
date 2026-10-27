package org.ramiro.huata.quesos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Salida de producto terminado. Siempre sale de UN lote: es lo que permite saber que queda en camara
 * y de que tanda vino lo que se vendio.
 *
 * <p>El total no se guarda: es {@code unidades * precioUnitario}. Guardarlo ademas seria un tercer
 * numero que puede contradecir a los otros dos.
 *
 * <p>Una venta equivocada se anula ({@code activo = false}) con motivo, no se borra: las unidades
 * vuelven al stock del lote y queda constancia de que hubo una venta y de por que no cuenta.
 */
@Entity
@Table(name = "ventas_queso")
@Getter
@Setter
@NoArgsConstructor
public class VentaQueso extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "lote_id")
    private LoteQueso lote;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String cliente;

    @Column(nullable = false)
    private Integer unidades;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_comprador", nullable = false, length = 20)
    private TipoComprador tipoComprador;

    /**
     * Precio efectivamente cobrado por unidad. Lo copia el servidor de la tarifa vigente segun el
     * tipo de comprador; no lo manda el formulario.
     *
     * <p>Se guarda, en vez de leerlo de la tarifa al mostrar la venta, porque la tarifa cambia: una
     * nota de venta de hace tres meses tiene que poder reimprimirse con el precio que se cobro
     * entonces, no con el de hoy.
     */
    @Column(name = "precio_unitario", nullable = false)
    private Double precioUnitario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CondicionVenta condicion;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_cobro", nullable = false, length = 20)
    private MedioCobro medioCobro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCobro estado;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    /**
     * IGV cobrado, congelado con la tasa vigente al vender (mismo criterio que {@code
     * precioUnitario}). Nulo en ventas anteriores al modulo contable (V21): no se recalcula
     * retroactivamente.
     */
    @Column(name = "monto_igv")
    private Double montoIgv;

    public double total() {
        return unidades * precioUnitario;
    }
}
