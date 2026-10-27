package org.ramiro.huata.usuarios;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Pantalla (o funcion transversal) del sistema. Sus acciones son permisos: conceder "Registrar
 * ventas" en la vista Ventas es conceder {@code quesos.write}, que es lo que exige el backend.
 */
@Entity
@Table(name = "vistas")
@Getter
@Setter
@NoArgsConstructor
public class Vista extends BaseEntity {

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "modulo_id", nullable = false)
    private Modulo modulo;

    /** Path de la web. null = funcion sin pantalla propia (exportar, auditoria...). */
    @Column(length = 120)
    private String ruta;

    @Column(length = 10)
    private String icono;

    @Column(nullable = false)
    private int orden;

    /** El permiso que hace visible la vista: el mismo que exige su guard en la web. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "permiso_acceso_id", nullable = false)
    private Permiso permisoAcceso;

    @OneToMany(mappedBy = "vista", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    private List<VistaAccion> acciones = new ArrayList<>();
}
