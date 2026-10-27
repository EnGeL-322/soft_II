package org.ramiro.huata.productores;

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

/**
 * Solicitud del proveedor para cambiar su {@link Productor#getZonaPrincipal()} (2.5). Aprobarla
 * actualiza la zona del proveedor; rechazarla solo deja constancia de la decision.
 */
@Entity
@Table(name = "solicitudes_cambio_zona")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudCambioZona extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id")
    private Productor productor;

    @Column(name = "zona_actual", length = 120)
    private String zonaActual;

    @Column(name = "zona_solicitada", nullable = false, length = 120)
    private String zonaSolicitada;

    @Column(length = 500)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitudZona estado = EstadoSolicitudZona.PENDIENTE;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_decision")
    private LocalDate fechaDecision;

    @Column(length = 120)
    private String aprobador;

    @Column(name = "observacion_decision", length = 500)
    private String observacionDecision;
}
