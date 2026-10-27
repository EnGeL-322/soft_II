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

/** Un vertice ordenado de un {@link TrazadoAcopiador}; se reemplazan completos al reeditar. */
@Entity
@Table(name = "trazado_acopiador_puntos")
@Getter
@Setter
@NoArgsConstructor
public class TrazadoAcopiadorPunto extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trazado_id")
    private TrazadoAcopiador trazado;

    @Column(nullable = false)
    private Integer secuencia;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;
}
