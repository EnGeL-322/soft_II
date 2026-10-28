package org.ramiro.huata.produccion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.quesos.LoteQueso;

/**
 * Produccion planificada (V61, prototipo aprobado por el cliente). Se crea aunque falte stock: eso
 * es justamente lo que alimenta las sugerencias de compra. Si esta lista o espera insumos NO se
 * guarda: se calcula al consultarla, porque el almacen cambia con cada recepcion y cada lote.
 *
 * <p>Se cumple al registrar el lote que la referencia; anular ese lote la devuelve a PENDIENTE.
 */
@Entity
@Table(name = "ordenes_produccion")
@Getter
@Setter
@NoArgsConstructor
public class OrdenProduccion extends BaseEntity {

    public enum Estado {
        PENDIENTE,
        TERMINADA,
        ANULADA
    }

    /** OP-2026-0001. Se pone despues de guardar, a partir del id, como el codigo de los lotes. */
    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /** Receta vigente al planificar: con ella se calcula lo que hace falta. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receta_id")
    private RecetaQueso receta;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "fecha_plan", nullable = false)
    private LocalDate fechaPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    /** El lote que la cumplio. Solo en TERMINADA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteQueso lote;

    @Column(length = 500)
    private String observaciones;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;
}
