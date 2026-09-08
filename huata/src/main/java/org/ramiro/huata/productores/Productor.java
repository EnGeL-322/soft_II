package org.ramiro.huata.productores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.rutas.CentroAcopio;
import org.ramiro.huata.rutas.Ruta;

@Entity
@Table(name = "productores")
@Getter
@Setter
@NoArgsConstructor
public class Productor extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombres;

    @Column(nullable = false, length = 150)
    private String apellidos;

    @Column(length = 20)
    private String documentoIdentidad;

    @Column(length = 30)
    private String telefono;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @ManyToOne
    @JoinColumn(name = "centro_acopio_id")
    private CentroAcopio centroAcopio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoProductor estado = EstadoProductor.ACTIVO;

    @Column(length = 500)
    private String notas;
}
