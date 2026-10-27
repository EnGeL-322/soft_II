package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Excepcion de acceso de UNA persona respecto de lo que le dan sus roles: concederle una accion
 * puntual o quitarsela, sin crear un rol nuevo para cada caso.
 */
@Entity
@Table(
        name = "usuario_permisos",
        uniqueConstraints = @UniqueConstraint(name = "usuario_permisos_unico", columnNames = {"usuario_id", "permiso_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UsuarioPermiso extends BaseEntity {

    public enum Tipo {
        CONCEDER,
        REVOCAR
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "permiso_id", nullable = false)
    private Permiso permiso;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    public UsuarioPermiso(Usuario usuario, Permiso permiso, Tipo tipo) {
        this.usuario = usuario;
        this.permiso = permiso;
        this.tipo = tipo;
    }
}
