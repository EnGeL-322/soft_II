package org.ramiro.huata.liquidaciones;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Tarifa de leche vigente. El Project Charter excluye precios de la version inicial, pero el
 * levantamiento operativo trabajo con S/ 1.75 (normal) / S/ 1.30 (observada). Por eso se modela
 * como configuracion versionada por fecha (C-01, 02_DECISIONES_Y_PENDIENTES.md) y no como
 * constante: activarla en produccion requiere que Administracion la confirme.
 */
@Entity
@Table(name = "tarifas_leche")
@Getter
@Setter
@NoArgsConstructor
public class TarifaLeche extends BaseEntity {

    @Column(nullable = false)
    private Double precioNormal;

    @Column(nullable = false)
    private Double precioObservada;

    /** S/ por litro de un proveedor Excelencia. NULL en tarifas anteriores a la regla 7.4. */
    @Column(name = "precio_excelencia")
    private Double precioExcelencia;

    @Column(nullable = false)
    private LocalDate vigenteDesde;

    private LocalDate vigenteHasta;
}
