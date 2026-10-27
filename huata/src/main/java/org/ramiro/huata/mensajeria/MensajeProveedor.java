package org.ramiro.huata.mensajeria;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.usuarios.Usuario;

@Entity
@Table(name = "mensajes_proveedor")
@Getter @Setter @NoArgsConstructor
public class MensajeProveedor extends BaseEntity {
    @ManyToOne(optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private ConversacionProveedor conversacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_usuario_id", nullable = false)
    private Usuario autor;

    @Column(nullable = false, length = 1000)
    private String cuerpo;

    /** Lo genera el cliente antes de tener senal; un reintento con el mismo valor no duplica (V44). */
    @Column(name = "client_uuid", length = 64, unique = true)
    private String clientUuid;

    private Instant leidoPorProveedorAt;
    private Instant leidoPorPlantaAt;

    // Nombre de columna explicito: con dos mayusculas seguidas ("AP") la estrategia de nombres de
    // Hibernate no separa la "a" y buscaria "entregadoaproveedor_at", que no existe (V44). En H2 no
    // se nota porque Hibernate crea el esquema; con Flyway + validate el backend no arranca.

    /** Llego al telefono del proveedor (doble check gris). */
    @Column(name = "entregado_a_proveedor_at")
    private Instant entregadoAProveedorAt;

    /** Llego a la bandeja de la planta, compartida entre Administracion y Secretaria. */
    @Column(name = "entregado_a_planta_at")
    private Instant entregadoAPlantaAt;

    /** Lo escribio el proveedor de la conversacion (y no alguien de la planta). */
    public boolean esDelProveedor() {
        Usuario usuarioProveedor = conversacion.getProductor().getUsuario();
        return usuarioProveedor != null && usuarioProveedor.getId().equals(autor.getId());
    }

    /**
     * Estado del mensaje frente a su destinatario: si lo escribio el proveedor, cuenta lo que hizo la
     * planta, y al reves. "Pendiente" y "error" no existen aqui: son de la cola del telefono, antes de
     * que el servidor lo tenga.
     */
    public EstadoMensaje estado() {
        boolean delProveedor = esDelProveedor();
        Instant leido = delProveedor ? leidoPorPlantaAt : leidoPorProveedorAt;
        Instant entregado = delProveedor ? entregadoAPlantaAt : entregadoAProveedorAt;
        if (leido != null) return EstadoMensaje.LEIDO;
        if (entregado != null) return EstadoMensaje.ENTREGADO;
        return EstadoMensaje.ENVIADO;
    }
}
