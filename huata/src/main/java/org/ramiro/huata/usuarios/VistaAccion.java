package org.ramiro.huata.usuarios;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lo que se puede hacer en una vista, con el nombre que entiende quien administra los accesos. */
@Entity
@Table(
        name = "vista_acciones",
        uniqueConstraints = @UniqueConstraint(name = "vista_acciones_unica", columnNames = {"vista_id", "permiso_id"}))
@Getter
@Setter
@NoArgsConstructor
public class VistaAccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vista_id", nullable = false)
    private Vista vista;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "permiso_id", nullable = false)
    private Permiso permiso;

    @Column(nullable = false, length = 80)
    private String etiqueta;

    @Column(nullable = false)
    private int orden;
}
