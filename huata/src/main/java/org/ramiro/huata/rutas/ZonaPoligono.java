package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Poligono geografico opcional de una zona. Ruta.zona sigue siendo texto libre (decision C-05: sin
 * catalogo fijo de zonas); una zona sin fila aqui simplemente no dibuja poligono en el mapa.
 */
@Entity
@Table(name = "zona_poligonos")
@Getter
@Setter
@NoArgsConstructor
public class ZonaPoligono extends BaseEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String zona;

    @Column(length = 20)
    private String color;
}
