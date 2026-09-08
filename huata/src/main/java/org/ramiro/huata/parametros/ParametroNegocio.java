package org.ramiro.huata.parametros;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Configuracion general clave/valor para todo lo que el proyecto marca como "no hardcodear":
 * limite de temperatura de transporte, canal de notificaciones, etc. (seccion 11 del contexto).
 */
@Entity
@Table(name = "parametros_negocio")
@Getter
@Setter
@NoArgsConstructor
public class ParametroNegocio extends BaseEntity {

    @Column(nullable = false, unique = true, length = 80)
    private String clave;

    @Column(nullable = false, length = 300)
    private String valor;

    @Column(length = 300)
    private String descripcion;
}
