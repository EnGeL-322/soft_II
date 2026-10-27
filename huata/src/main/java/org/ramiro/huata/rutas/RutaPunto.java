package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Un punto de la geometria de una ruta (2.1.3-2.1.5): coleccion ordenada de lat/lon que se puede
 * dibujar, editar y borrar en el mapa. Se reemplaza completa al editar (no se versiona punto por
 * punto): es un poligono, no un historial de cambios.
 */
@Entity
@Table(name = "ruta_puntos")
@Getter
@Setter
@NoArgsConstructor
public class RutaPunto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Column(nullable = false)
    private Integer secuencia;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;
}
