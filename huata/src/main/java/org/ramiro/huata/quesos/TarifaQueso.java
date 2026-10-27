package org.ramiro.huata.quesos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.produccion.Producto;

/**
 * Precio de venta de la unidad de UN producto segun a quien se le venda, versionado por fecha.
 *
 * <p><b>Desde V28 cada producto tiene su lista de precios</b> (antes habia una sola para "el queso")
 * y {@code vigenteHasta} SI decide: un periodo con fecha de fin es una promocion, rige solo entre sus
 * dos fechas y al terminar vuelve a regir el precio que estaba antes. Si dos periodos cubren la misma
 * fecha, manda el que empezo mas tarde (una promocion dentro del precio normal). Ver
 * {@code QueseriaService.tarifaVigente}.
 *
 * <p>Los tres precios van en la misma fila porque son el mismo concepto —cuanto cuesta la unidad en
 * este periodo— y se revisan juntos. Una fila por tipo de comprador permitiria que el precio de
 * mayorista tuviera una vigencia distinta a la del publico, y al reimprimir una nota de venta vieja
 * no habria forma de decir cual de las dos vigencias mandaba.
 *
 * <p>Se edita en Configuracion, no en la pantalla de venta: es una decision de la cooperativa, no de
 * quien despacha el producto.
 */
@Entity
@Table(name = "tarifas_queso")
@Getter
@Setter
@NoArgsConstructor
public class TarifaQueso extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "precio_publico_general", nullable = false)
    private Double precioPublicoGeneral;

    @Column(name = "precio_mayorista", nullable = false)
    private Double precioMayorista;

    /** Precio preferente para el proveedor de leche que compra queso. */
    @Column(name = "precio_proveedor", nullable = false)
    private Double precioProveedor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    /** Nombre del periodo o de la promocion ("Fiestas Patrias"). Opcional. */
    @Column(length = 120)
    private String descripcion;
}
