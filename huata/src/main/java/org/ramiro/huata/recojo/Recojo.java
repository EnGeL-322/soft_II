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

    /**
     * ACOPIADO o NO_ENTREGADO (6.3). Nulo en recojos anteriores a V14 y en cualquier recojo cargado
     * por un canal que no distingue "visita": no se infiere retroactivamente. Blanco/pendiente no es
     * un valor de este campo, ver {@link EstadoVisita}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_visita", length = 20)
    private EstadoVisita estadoVisita;

    /** Obligatorio cuando estadoVisita = NO_ENTREGADO. Distinto de motivoCancelacion: ese es para anular un recojo ya registrado. */
    @Column(name = "motivo_no_entrega", length = 300)
    private String motivoNoEntrega;

    /**
     * GPS del acopiador (o de la app de planta) al momento de registrar. Nulo en recojos anteriores
     * a esta version o cuando el cliente no lo envio (V28).
     */
    private Double latitud;

    private Double longitud;

    /**
     * Distancia en metros entre {@code latitud}/{@code longitud} y la ultima ubicacion conocida del
     * proveedor en ese momento. Nulo si falta cualquiera de los dos puntos: no hay con que comparar,
     * no es "0m" (ver {@code RecojoService.registrar}).
     */
    @Column(name = "distancia_proveedor_m")
    private Double distanciaProveedorM;

    /**
     * Distancia en metros entre {@code latitud}/{@code longitud} y el punto establecido asociado al
     * proveedor de este recojo, si tiene uno (V43, Opcion B1). Nulo si el proveedor no tiene punto
     * establecido o falta el GPS del acopiador: no hay con que comparar, no es "0m".
     */
    @Column(name = "distancia_punto_establecido_m")
    private Double distanciaPuntoEstablecidoM;

    /**
     * true si {@code distanciaProveedorM} O {@code distanciaPuntoEstablecidoM} superaron el limite
     * configurado (RECOJO_DISTANCIA_MAXIMA_METROS) al registrar. NO rechaza el recojo: un GPS
     * impreciso o un proveedor con el pin mal puesto no debe borrar una entrega real ya pagada —
     * queda marcado para que calidad/administracion lo revise.
     */
    @Column(name = "gps_fuera_de_rango", nullable = false)
    private boolean gpsFueraDeRango = false;

    /**
     * true si este recojo fue corregido despues de creado (ver {@code RecojoService.corregir}).
     * Un proveedor entrega leche una sola vez al dia (ver {@code registrar}), asi que la unica forma
     * legitima de arreglar un litros mal tecleado es corregir el registro existente, no crear uno
     * nuevo -- y toda correccion exige una justificacion y queda marcada para revision, en vez de
     * dejar que el numero cambie en silencio (RN-13 aplicado aqui por analogia: no ocultar que un
     * valor que alimenta la liquidacion fue tocado despues de registrado).
     */
    @Column(nullable = false)
    private boolean observado = false;

    /** Justificacion obligatoria de la correccion. Nulo si el recojo nunca se corrigio. */
    @Column(name = "motivo_observacion", length = 300)
    private String motivoObservacion;
}
