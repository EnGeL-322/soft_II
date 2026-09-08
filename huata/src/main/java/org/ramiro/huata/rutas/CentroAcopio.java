package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

@Entity
@Table(name = "centros_acopio")
@Getter
@Setter
@NoArgsConstructor
public class CentroAcopio extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 200)
    private String ubicacion;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;
}
