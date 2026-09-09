package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;

/** Asigna un acopiador a una ruta para una fecha dada (es la "ruta del dia" que consulta el movil en Fase 2). */
@Entity
@Table(name = "asignaciones_ruta")
@Getter
@Setter
@NoArgsConstructor
public class AsignacionRuta extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(optional = false)
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    @Column(nullable = false)
    private LocalDate fecha;

    /** Texto libre, opcional: no se inventa una flota de vehiculos, solo se anota si se conoce. */
    @Column(length = 120)
    private String movilidad;

    @Column(length = 500)
    private String observacion;
}
