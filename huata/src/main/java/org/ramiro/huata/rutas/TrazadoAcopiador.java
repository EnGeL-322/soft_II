package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;

/**
 * Recorrido que el acopiador pinta en la app movil (V27), sea porque hoy no va por la ruta designada
 * o porque va por ella igualmente y quiere dejarlo dibujado.
 *
 * <p>No sustituye a {@link RutaPunto}: esa es la geometria de la RUTA, compartida. Esta es del
 * acopiador. Mientras no tenga un {@link TipoTrazado#PREDETERMINADO}, su recorrido de referencia
 * sigue siendo el de la ruta.
 */
@Entity
@Table(name = "trazados_acopiador")
@Getter
@Setter
@NoArgsConstructor
public class TrazadoAcopiador extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    /**
     * Ruta a la que pertenece el trazado. La resuelve el servidor (asignacion del dia, o la ruta
     * habitual del acopiador) y no la elige el cliente: si la mandara el movil, un acopiador podria
     * colgar su trazado de la ruta de otro.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTrazado tipo;

    /** Obligatoria en DIA, nula en PREDETERMINADO (lo sostiene un CHECK en V27). */
    private LocalDate fecha;

    /**
     * Lo que declaro el acopiador: si su recorrido de hoy sigue la ruta designada o no. Es un dato
     * declarado, no deducido de comparar geometrias — dos trazos parecidos no prueban intencion, y es
     * justo el motivo por el que el cliente pidio esta pantalla.
     */
    @Column(name = "sigue_ruta_designada", nullable = false)
    private boolean sigueRutaDesignada = true;

    @Column(length = 500)
    private String observacion;

    /** De que trazado del dia se copio este predeterminado. Nulo en los del dia. */
    @Column(name = "origen_trazado_id")
    private Long origenTrazadoId;

    @Column(name = "predeterminado_en")
    private Instant predeterminadoEn;

    @Column(name = "predeterminado_por", length = 120)
    private String predeterminadoPor;
}
