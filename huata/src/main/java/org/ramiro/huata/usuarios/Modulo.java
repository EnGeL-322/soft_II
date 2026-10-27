package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/** Grupo del menu ("Acopio", "Producción"...). Agrupa vistas; no concede nada por si mismo. */
@Entity
@Table(name = "modulos")
@Getter
@Setter
@NoArgsConstructor
public class Modulo extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false)
    private int orden;
}
