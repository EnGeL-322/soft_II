package org.ramiro.huata.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.recojo.Recojo;

/**
 * Control de calidad de la leche de un proveedor. Los campos numericos son de captura libre; los
 * rangos de referencia contra los que se comparan viven en {@link ParametroCalidad} (configurable).
 *
 * <p>Desde V47 el recojo es opcional: el testeador analiza en campo ANTES de que pase el acopiador,
 * y el control se vincula al recojo cuando este se registra (ver RecojoService).
 */
@Entity
@Table(name = "controles_calidad")
@Getter
@Setter
@NoArgsConstructor
public class ControlCalidad extends BaseEntity {

    @OneToOne
    @JoinColumn(name = "recojo_id", unique = true)
    private Recojo recojo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id", nullable = false)
    private Productor productor;

    /** Momento del analisis. */
    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    /** Nulo solo en controles anteriores a V47 de recojos de ruta: no consta donde se midieron. */
    @Enumerated(EnumType.STRING)
    @Column(name = "lugar_analisis", length = 20)
    private LugarAnalisis lugarAnalisis;

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

    /** V48: quien aprobo el analisis en la web. Nulo = pendiente de aprobacion. */
    @Column(name = "aprobado_por", length = 120)
    private String aprobadoPor;

    @Column(name = "aprobado_en")
    private Instant aprobadoEn;

    /** V50: por que no cumplio el estandar de calidad. Nula si lo cumple (y entonces se aprueba solo). */
    @Column(length = 500)
    private String advertencia;

    /**
     * Con recojo, el proveedor y la fecha salen de el si no se dieron: es el mismo dato y asi no
     * pueden contradecirse.
     */
    @PrePersist
    @PreUpdate
    void completarDesdeElRecojo() {
        if (recojo != null) {
            if (productor == null) productor = recojo.getProductor();
            if (fechaHora == null) fechaHora = recojo.getFechaHora();
        }
        if (fechaHora == null) fechaHora = Instant.now();
    }

    /** Proveedor del control: el propio, o el del recojo si todavia no se completo. */
    public Productor proveedor() {
        return productor != null ? productor : recojo != null ? recojo.getProductor() : null;
    }
}
