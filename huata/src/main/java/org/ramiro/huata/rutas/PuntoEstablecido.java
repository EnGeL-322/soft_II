package org.ramiro.huata.rutas;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;

/**
 * Punto de referencia que administracion define a mano en el mapa (nombre + latitud/longitud), para
 * que la app del acopiador lo muestre junto a su ruta del dia. Es distinto de {@link RutaPunto} (la
 * geometria del trazo, anonima y se reemplaza completa) y de
 * {@link org.ramiro.huata.productores.ProductorUbicacion} (la ubicacion del proveedor): este punto
 * tiene nombre propio, pertenece a una ruta, y opcionalmente a un acopiador y/o a un proveedor.
 *
 * <p>Cuando {@code proveedor} esta presente, {@code RecojoService.registrar} tambien compara el GPS
 * del acopiador contra este punto con la misma regla de cercania que ya aplica contra
 * {@code ProductorUbicacion} (Opcion B1 acordada con el cliente, 2026-09-30): marca, no bloquea.
 *
 * <p>{@code activo}, {@code createdBy} y {@code createdAt} vienen de {@link BaseEntity}: son
 * exactamente "estado", "creado_por" y "fecha_creacion" pedidos por el cliente, sin duplicarlos aqui.
 */
@Entity
@Table(name = "puntos_establecidos")
@Getter
@Setter
@NoArgsConstructor
public class PuntoEstablecido extends BaseEntity {

    @Version
    @Column(nullable = false)
    private Long version;

    /** Baja definitiva lógica: conserva referencias y auditoría, pero no permite reactivar. */
    @Column(nullable = false)
    private boolean eliminado;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private Double latitud;

    @Column(nullable = false)
    private Double longitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    /** Opcional: si se indica, la app de ESE acopiador lo ve aunque el punto no sea de su ruta habitual. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    /** Opcional: activa la comparacion de cercania al registrar el recojo de este proveedor. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Productor proveedor;

    @Column(length = 300)
    private String observacion;
}
