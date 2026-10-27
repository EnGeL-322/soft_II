package org.ramiro.huata.quesos;

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
import org.ramiro.huata.produccion.Producto;
import org.ramiro.huata.produccion.RecetaQueso;

/**
 * Lote de produccion de queso: lo que la queseria saca de una tanda de leche.
 *
 * <p><b>El stock no esta aqui.</b> No hay campo "unidades disponibles" a proposito: seria un
 * contador que hay que acordarse de mover en cada venta, anulacion y correccion, y el dia que uno de
 * esos caminos se olvide, la camara y las ventas dejan de cuadrar sin que nadie pueda decir cual de
 * las dos miente. Desde V13 el stock (de este lote y de cada materia prima) se calcula sobre
 * {@code movimientos_inventario}: {@link QueseriaService} ya no resta las ventas activas, suma el
 * kardex.
 *
 * <p><b>receta/costos son nullable a proposito.</b> Los lotes creados antes de V13 no tienen receta
 * ni costo, y no se inventan retroactivamente (ver backfill de la migracion). El servicio exige estos
 * campos para cualquier lote NUEVO; la columna se queda nullable solo para no romper el historico.
 */
@Entity
@Table(name = "lotes_queso")
@Getter
@Setter
@NoArgsConstructor
public class LoteQueso extends BaseEntity {

    /** Como se nombra el lote fuera del sistema: en la camara y en la nota de venta. Unico. */
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Que se produjo en el lote. EAGER: la venta y el listado siempre muestran su nombre. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_leche", nullable = false, length = 30)
    private OrigenLeche origenLeche;

    @Column(name = "litros_destinados", nullable = false)
    private Double litrosDestinados;

    @Column(name = "unidades_producidas", nullable = false)
    private Integer unidadesProducidas;

    @Column(nullable = false, length = 120)
    private String responsable;

    @Column(length = 500)
    private String observaciones;

    /** Version de receta usada al crear el lote (snapshot). Nulo en lotes anteriores a V13. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receta_id")
    private RecetaQueso receta;

    /** Cuanto se queria producir. La diferencia contra unidadesProducidas es la merma del lote. */
    @Column(name = "cantidad_objetivo")
    private Integer cantidadObjetivo;

    @Column(name = "costo_materia_prima", precision = 12, scale = 2)
    private BigDecimal costoMateriaPrima;

    @Column(name = "costo_mano_obra", precision = 12, scale = 2)
    private BigDecimal costoManoObra;

    @Column(name = "costo_total", precision = 12, scale = 2)
    private BigDecimal costoTotal;

    @Column(name = "costo_unitario", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    /** Quesos del lote que salieron con defecto y se reprocesaron. Informativo: no descuenta stock. */
    @Column(name = "quesos_reparados")
    private Integer quesosReparados;

    /** Quesos de recorte (retazo) generados por el lote, en unidades. */
    @Column(name = "quesos_recorte")
    private Integer quesosRecorte;

    /** Peso del recorte del lote, en kilogramos. */
    @Column(name = "recorte_kg")
    private Double recorteKg;

    /**
     * Hasta cuando se puede vender (V33). Sale de los dias que dura el producto, o se corrige al
     * producir. Nula en lotes anteriores y en productos sin dato: no se inventa.
     */
    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /** Por que se anulo (V59). Un lote anulado queda con activo = false; no se borra. */
    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    /** Vencido en esa fecha: ya no se vende. */
    public boolean vencidoEl(LocalDate fecha) {
        return fechaVencimiento != null && fecha.isAfter(fechaVencimiento);
    }

    /**
     * Litros que costo cada unidad. Es el numero con el que la queseria se compara consigo misma de
     * una semana a otra, y el que delata una tanda que cuajo mal.
     */
    public double rendimientoLitrosPorUnidad() {
        return litrosDestinados / unidadesProducidas;
    }

    /** Nula si el lote no registro un objetivo (lotes anteriores a V13, o creados sin uno). */
    public Integer merma() {
        return cantidadObjetivo != null ? cantidadObjetivo - unidadesProducidas : null;
    }
}
