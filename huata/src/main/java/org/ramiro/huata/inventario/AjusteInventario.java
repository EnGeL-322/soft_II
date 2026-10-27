package org.ramiro.huata.inventario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Ajuste manual del stock de una materia prima (V59): merma, conteo fisico, leche descartada. Es el
 * documento que explica el movimiento {@code AJUSTE_ENTRADA}/{@code AJUSTE_SALIDA} del kardex; el
 * stock lo mueve ese movimiento, no esta fila.
 */
@Entity
@Table(name = "ajustes_inventario")
@Getter
@Setter
@NoArgsConstructor
public class AjusteInventario extends BaseEntity {

    public enum Tipo {
        ENTRADA,
        SALIDA
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_prima_id")
    private MateriaPrima materiaPrima;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidad;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false, length = 300)
    private String motivo;
}
