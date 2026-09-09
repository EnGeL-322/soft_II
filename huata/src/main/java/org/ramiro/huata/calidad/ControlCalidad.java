package org.ramiro.huata.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.recojo.Recojo;

/**
 * Control de calidad de un recojo puntual. Los campos numericos son de captura libre; los rangos
 * de referencia contra los que se comparan viven en {@link ParametroCalidad} (configurable).
 */
@Entity
@Table(name = "controles_calidad")
@Getter
@Setter
@NoArgsConstructor
public class ControlCalidad extends BaseEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "recojo_id", unique = true)
    private Recojo recojo;

    private Double densidad;
    private Double puntoCongelacion;
    private Double acidez;
    private Double temperatura;
    private Double grasa;
    private Double solidosNoGrasos;
    private Double solidosTotales;

    /** Lenguaje simple para el usuario operativo: "olor agrio", "grumos", "aspecto normal", etc. */
    @Column(length = 500)
    private String revisionVisual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoCalidad resultado;

    @Column(length = 120)
    private String equipoUtilizado;

    @Column(length = 150)
    private String responsable;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AguaAnadida aguaAnadida;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResultadoLugol lugol;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Olor olor;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Aspecto aspecto;

    /** Solo aplica cuando resultado = RECHAZADA. */
    @Column(length = 300)
    private String motivoRechazo;
}
