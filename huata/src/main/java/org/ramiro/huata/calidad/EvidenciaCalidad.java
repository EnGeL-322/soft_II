package org.ramiro.huata.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/** Foto/firma/voucher asociado a un control de calidad o a una incidencia ("Ver foto" / "Ver evidencia"). */
@Entity
@Table(name = "evidencias_calidad")
@Getter
@Setter
@NoArgsConstructor
public class EvidenciaCalidad extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "control_calidad_id")
    private ControlCalidad controlCalidad;

    @ManyToOne
    @JoinColumn(name = "incidencia_id")
    private IncidenciaCalidad incidencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvidencia tipo;

    /** URL/ruta del archivo. En Fase 1 se asume ya subido a un storage externo; el backend solo referencia la URL. */
    @Column(nullable = false, length = 500)
    private String urlArchivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvidencia estado = EstadoEvidencia.UPLOADED;

    // ---- Archivo en el almacen de objetos (V40). Nulos en evidencias que solo guardan una URL. ----

    @Column(length = 300)
    private String claveArchivo;

    @Column(length = 50)
    private String tipoContenido;

    private Long tamanoBytes;

    @Column(length = 64)
    private String sha256;

    /** Texto crudo que devolvio el OCR, tal cual (RNF-20). */
    @Column(columnDefinition = "TEXT")
    private String textoOcr;

    /** Valores que propuso el OCR (JSON), para compararlos con los confirmados en el control. */
    @Column(columnDefinition = "TEXT")
    private String valoresOcr;

    private Double confianzaOcr;

    /** Quien confirmo los valores del voucher (RN-14): el usuario que guardo el control. */
    @Column(length = 120)
    private String verificadoPor;

    @Column(length = 64)
    private String clientUuid;
}
