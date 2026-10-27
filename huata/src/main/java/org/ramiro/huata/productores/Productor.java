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
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.usuarios.Usuario;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoProductor estado = EstadoProductor.ACTIVO;

    @Column(length = 500)
    private String notas;

    /**
     * Marca administrable de Excelencia (7.4). Convive con el estado: un proveedor Excelencia puede
     * estar ACTIVO o INACTIVO sin dejar de tenerla. Solo surte efecto mientras la muestra salga
     * apta; una sola incidencia le hace perder la tarifa preferencial en toda la semana.
     */
    @Column(nullable = false)
    private boolean excelencia = false;

    /** Cuenta con la que el proveedor inicia sesion en la app movil. Opcional (mismo patron que Acopiador.usuario). */
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Vinculo ACTUAL con su acopiador responsable (2.1.2). No es historico: reasignar sobrescribe.
     * Si se pide conservar el historial de reasignaciones, esto pasa a ser una tabla aparte.
     */
    @ManyToOne
    @JoinColumn(name = "acopiador_responsable_id")
    private Acopiador acopiadorResponsable;

    /** Zona propia del proveedor, independiente de Ruta.zona. La cambia el admin o una solicitud aprobada. */
    @Column(name = "zona_principal", length = 120)
    private String zonaPrincipal;

    /** Secuencia de visita dentro de su ruta. Nulo = sin orden definido. */
    @Column(name = "orden_en_ruta")
    private Integer ordenEnRuta;
}
