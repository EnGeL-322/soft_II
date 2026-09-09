package org.ramiro.huata.reportes;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

@Entity
@Table(name = "cierres_acopio")
@Getter
@Setter
public class CierreAcopio extends BaseEntity {
    @Column(nullable = false, unique = true)
    private LocalDate fecha;
    private double litrosSistema;
    private double litrosPlanta;
    @Column(length = 1000)
    private String observacion;
    private boolean cerrado;
    @Version
    private Long version;
}
