package org.ramiro.huata.inventario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

record MateriaPrimaRequest(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull UnidadMedida unidadMedida,
        @PositiveOrZero BigDecimal costoUnitarioReferencial) {}

/** Ajuste manual de stock (V59). Sin fecha: hoy. */
record AjusteInventarioRequest(
        @NotNull Long materiaPrimaId,
        @NotNull AjusteInventario.Tipo tipo,
        @NotNull @jakarta.validation.constraints.Positive BigDecimal cantidad,
        LocalDate fecha,
        @NotBlank @Size(max = 300) String motivo) {}

record AjusteInventarioResponse(
        Long id, Long materiaPrimaId, String materiaPrimaNombre, AjusteInventario.Tipo tipo, BigDecimal cantidad,
        LocalDate fecha, String motivo, BigDecimal stock) {}

/** {@code stock} se calcula sobre el kardex; no es una columna de materias_primas. */
record MateriaPrimaResponse(
        Long id,
        String nombre,
        UnidadMedida unidadMedida,
        BigDecimal costoUnitarioReferencial,
        BigDecimal stock,
        boolean activo,
        /** Entra sola cuando planta recibe la leche de las rutas: no se compra ni se da de baja. */
        boolean lecheAcopio) {

    static MateriaPrimaResponse from(MateriaPrima m, BigDecimal stock) {
        return new MateriaPrimaResponse(
                m.getId(), m.getNombre(), m.getUnidadMedida(), m.getCostoUnitarioReferencial(), stock, m.isActivo(),
                m.isLecheAcopio());
    }
}

record MovimientoInventarioResponse(
        Long id,
        TipoMovimientoInventario tipo,
        Long materiaPrimaId,
        String materiaPrimaNombre,
        Long loteQuesoId,
        String loteQuesoCodigo,
        BigDecimal cantidad,
        LocalDate fecha,
        DocumentoOrigenTipo documentoOrigenTipo,
        Long documentoOrigenId,
        String usuario,
        String observaciones) {

    static MovimientoInventarioResponse from(MovimientoInventario m) {
        return new MovimientoInventarioResponse(
                m.getId(),
                m.getTipo(),
                m.getMateriaPrima() != null ? m.getMateriaPrima().getId() : null,
                m.getMateriaPrima() != null ? m.getMateriaPrima().getNombre() : null,
                m.getLoteQueso() != null ? m.getLoteQueso().getId() : null,
                m.getLoteQueso() != null ? m.getLoteQueso().getCodigo() : null,
                m.getCantidad(),
                m.getFecha(),
                m.getDocumentoOrigenTipo(),
                m.getDocumentoOrigenId(),
                m.getUsuario(),
                m.getObservaciones());
    }
}
