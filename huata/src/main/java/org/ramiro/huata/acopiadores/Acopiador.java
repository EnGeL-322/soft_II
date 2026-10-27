package org.ramiro.huata.acopiadores;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.usuarios.Usuario;

@Entity
@Table(name = "acopiadores")
@Getter
@Setter
@NoArgsConstructor
public class Acopiador extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String nombres;

    @Column(nullable = false, length = 150)
    private String apellidos;

    @Column(length = 20)
    private String documentoIdentidad;

    @Column(length = 30)
    private String telefono;

    /** Cuenta con la que inicia sesion (web ahora; app movil en Fase 2). Opcional. */
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /** Ruta y zona habitual donde realiza el acopio. Sus proveedores se obtienen de esta ruta. */
    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;
}
