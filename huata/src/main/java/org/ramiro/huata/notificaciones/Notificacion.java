package org.ramiro.huata.notificaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.usuarios.Usuario;

/**
 * Notificacion enlazada al registro que la origino (p.ej. una incidencia de calidad), tal como
 * pide 04_UI_UX_Y_FLUJOS.md: el clic debe llevar directo al detalle correspondiente.
 */
@Entity
@Table(name = "notificaciones")
@Getter
@Setter
@NoArgsConstructor
public class Notificacion extends BaseEntity {

    /** Null = notificacion general (visible para todo el equipo administrativo). */
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    /** Tipo de entidad relacionada, p.ej. "INCIDENCIA_CALIDAD", "LIQUIDACION". */
    @Column(length = 60)
    private String entidadTipo;

    private Long entidadId;

    @Column(nullable = false)
    private boolean leida = false;
}
