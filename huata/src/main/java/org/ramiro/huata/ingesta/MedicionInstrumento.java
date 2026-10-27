package org.ramiro.huata.ingesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.recojo.Recojo;

/**
 * El ticket del analizador de leche (LACTOMAT / SmartScan AI) para una recepcion.
 *
 * <p>Es lo que MIDIO el aparato, sin dictamen. La conclusion de calidad y los valores que el
 * responsable confirma viven en ControlCalidad; esa separacion es deliberada (ver V5 y V7): la
 * tabla de calidad exige un resultado, y en el momento de medir todavia no hay ninguno.
 *
 * <p>DOS UNIDADES QUE SE PRESTAN A CONFUSION:
 * <ul>
 *   <li>{@code densidadLactometrica} son GRADOS de lactometro (23.9 / 31.4 en los tickets reales),
 *       no g/mL. Confundirlas da lecturas absurdas.</li>
 *   <li>{@code aguaAnadidaPct} es un porcentaje medido, no el enum de ControlCalidad, que es la
 *       conclusion de una persona.</li>
 * </ul>
 */
@Entity
@Table(name = "medicion_instrumento")
@Getter
@Setter
@NoArgsConstructor
public class MedicionInstrumento extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "recojo_id", unique = true)
    private Recojo recojo;

    /** Numero de serie del analizador: sin el no se puede rastrear una medicion hasta su maquina. */
    @Column(length = 40)
    private String analizadorSn;

    @Column(length = 20)
    private String modo;

    /** Momento que imprime el propio ticket, que puede no coincidir con el de registro. */
    private Instant medidoEn;

    private Double temperatura;
    private Double grasaPct;
    private Double solidosNoGrasosPct;
    /** Grados de lactometro, NO g/mL. */
    private Double densidadLactometrica;
    private Double proteinaPct;
    private Double lactosaPct;
    private Double salesPct;
    private Double solidosTotalesPct;
    /** Porcentaje medido de agua anadida. */
    private Double aguaAnadidaPct;
    private Double puntoCongelacion;
    private Double ph;
}
