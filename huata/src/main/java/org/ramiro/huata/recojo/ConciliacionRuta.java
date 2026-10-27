package org.ramiro.huata.recojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.rutas.Ruta;

/**
 * Litros recibidos en planta por ruta y fecha (2.2.4). El total recolectado NO se guarda aqui: se
 * calcula sumando {@link Recojo} de esa ruta/fecha (mismo criterio anti-drift que
 * {@code QueseriaService} usa para el stock de queso) — un numero guardado aparte que hay que
 * mantener sincronizado a mano es un numero que un dia deja de cuadrar sin que nadie lo note.
 */
@Entity
@Table(name = "conciliaciones_ruta")
@Getter
@Setter
@NoArgsConstructor
public class ConciliacionRuta extends BaseEntity {

    @Column(nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    @Column(name = "litros_recibidos_planta", nullable = false)
    private Double litrosRecibidosPlanta;

    @Column(length = 150)
    private String responsable;

    @Column(length = 500)
    private String observacion;
}
