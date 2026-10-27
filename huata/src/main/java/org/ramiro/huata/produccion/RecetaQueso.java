package org.ramiro.huata.produccion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.ramiro.huata.common.BaseEntity;

/**
 * Receta/BOM de un producto (queso, yogur...), versionada por fecha (igual criterio que {@code TarifaQueso}): no
 * se edita en sitio, una version nueva es una fila nueva. Un {@code LoteQueso} referencia la version
 * vigente al crearse (snapshot): si la receta cambia despues, ese lote no se entera.
 */
@Entity
@Table(name = "recetas_queso")
@Getter
@Setter
@NoArgsConstructor
public class RecetaQueso extends BaseEntity {

    /**
     * Que producto fabrica esta receta. EAGER (el valor por defecto de ManyToOne) a proposito: el
     * catalogo es chico y toda respuesta de receta lleva el nombre del producto; LAZY obligaria a
     * cuidar cada lectura fuera de la transaccion (open-in-view=false).
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "rendimiento_litros_por_unidad", precision = 10, scale = 4)
    private BigDecimal rendimientoLitrosPorUnidad;

    @Column(length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "receta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RecetaItem> items = new ArrayList<>();
}
