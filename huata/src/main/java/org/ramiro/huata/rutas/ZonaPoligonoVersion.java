package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Copia de un limite de zona tomada justo antes de modificarlo o eliminarlo (V56). Existe para que
 * un borrado o un cambio por accidente se pueda deshacer desde la web; {@code createdBy} y
 * {@code createdAt} dicen quien hizo el cambio y cuando.
 */
@Entity
@Table(name = "zona_poligono_versiones")
@Getter
@Setter
@NoArgsConstructor
public class ZonaPoligonoVersion extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String zona;

    @Column(length = 20)
    private String color;

    /** JSON {@code [[lat, lng], ...]} en el orden de los vertices. */
    @Column(nullable = false, columnDefinition = "text")
    private String vertices;

    @Column(nullable = false, length = 40)
    private String motivo;
}
