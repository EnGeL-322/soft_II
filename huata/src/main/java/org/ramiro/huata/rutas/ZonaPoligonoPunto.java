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
 * Un vertice del poligono de una zona. Se reemplaza completo al editar (no se versiona punto por
 * punto), igual que {@link RutaPunto}.
 */
@Entity
@Table(name = "zona_poligono_puntos")
@Getter
@Setter
@NoArgsConstructor
public class ZonaPoligonoPunto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zona_poligono_id")
    private ZonaPoligono zonaPoligono;

    @Column(nullable = false)
    private Integer secuencia;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;
}
