package org.ramiro.huata.recojo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.common.BaseEntity;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.rutas.Ruta;

/**
 * Registro de acopio en campo. "clientUuid" es la llave idempotente pensada para la Fase 2
 * (movil offline-first): el backend la usa desde ya para no duplicar un recojo si se reintenta el
 * envio (doble clic, reintento de red).
 */
@Entity
@Table(name = "recojos")
@Getter
@Setter
@NoArgsConstructor
public class Recojo extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String clientUuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "productor_id")
    private Productor productor;

    @ManyToOne
    @JoinColumn(name = "acopiador_id")
    private Acopiador acopiador;

    @ManyToOne
    @JoinColumn(name = "ruta_id")
    private Ruta ruta;

    @Column(nullable = false)
    private Instant fechaHora;

    @Column(nullable = false)
    private Double litros;

    private Double temperatura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoRecojo estado = EstadoRecojo.SYNCED;

    @Column(length = 300)
    private String motivoCancelacion;

    /**
     * PLANTA (entrega directa) o RUTA (recogido por un acopiador) — igual que el prototipo.
     * Nullable a nivel de base de datos a propósito: Hibernate ddl-auto=update no puede agregar
     * una columna NOT NULL sin default sobre una tabla con filas existentes (Postgres la rechaza).
     * El valor por defecto se aplica en la capa de aplicación (ver RecojoService.registrar).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FuenteRecojo fuente = FuenteRecojo.RUTA;

    private Integer numeroEnvases;

    private Double capacidadReferenciaL;

    @Column(length = 150)
    private String responsableRecepcion;

    /** Litros que reportó la app móvil (Fase 2) para cruzar contra lo recibido en planta; nulo mientras no exista ese envío. */
    private Double litrosAppMovil;
}
