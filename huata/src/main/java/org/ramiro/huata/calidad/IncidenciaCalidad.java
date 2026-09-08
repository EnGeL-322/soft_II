package org.ramiro.huata.calidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.recojo.Recojo;

/** Seguimiento de incidencias/adulteraciones por proveedor (boton "Seguimiento" del UI/UX). */
@Entity
@Table(name = "incidencias_calidad")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaCalidad extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id")
    private Productor productor;

    @ManyToOne
    @JoinColumn(name = "recojo_id")
    private Recojo recojo;

    /** Texto libre y configurable a proposito: "Adulteracion", "Agua anadida", "Baja calidad", etc. */
    @Column(nullable = false, length = 80)
    private String tipo;

    @Column(length = 500)
    private String descripcion;

    @Column(nullable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoIncidencia estado = EstadoIncidencia.ABIERTA;
}
