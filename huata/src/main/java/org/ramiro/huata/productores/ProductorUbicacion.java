package org.ramiro.huata.productores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Punto de referencia del proveedor (2.6): su vivienda o su ubicacion actual, guardado desde la
 * aplicacion. No es {@code UbicacionGps}: esa tabla es el rastreo de ruta del acopiador, un concepto
 * distinto (seguimiento continuo vs un punto de referencia puntual).
 */
@Entity
@Table(name = "productor_ubicaciones")
@Getter
@Setter
@NoArgsConstructor
public class ProductorUbicacion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productor_id")
    private Productor productor;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;

    @Column(name = "precision_metros")
    private Double precisionMetros;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUbicacion tipo;

    @Column(nullable = false)
    private LocalDate fecha;
}
