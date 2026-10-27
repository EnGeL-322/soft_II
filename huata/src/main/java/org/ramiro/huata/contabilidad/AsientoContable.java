package org.ramiro.huata.contabilidad;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Partida del libro diario. Se genera automaticamente desde compras, produccion y ventas de queso
 * (ver {@code AsientoContableService}); no hay registro manual todavia.
 *
 * <p>La suma de {@code debe} debe igualar la suma de {@code haber} de sus lineas (partida doble): lo
 * valida el servicio antes de guardar, no una constraint de base de datos (SQL no puede sumar
 * columnas de otra tabla en un CHECK).
 */
@Entity
@Table(name = "asientos_contables")
@Getter
@Setter
@NoArgsConstructor
public class AsientoContable extends BaseEntity {

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 200)
    private String glosa;

    @Column(length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_tipo", nullable = false, length = 30)
    private OrigenAsiento origenTipo;

    @Column(name = "origen_id")
    private Long origenId;

    @OneToMany(mappedBy = "asiento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<AsientoLinea> lineas = new ArrayList<>();
}
