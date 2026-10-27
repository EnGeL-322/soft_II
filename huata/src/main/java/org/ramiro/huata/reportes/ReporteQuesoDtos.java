package org.ramiro.huata.reportes;

import java.time.LocalDate;
import java.util.List;

/** Fila diaria de "Reporte de venta de quesos": producidos/despachados/vendidos/diferencia. */
record VentaQuesoDiaRow(
        LocalDate fecha, String dia, int producidos, int despachados, int vendidos, int diferencia) {}

/**
 * Fila diaria de "Reporte de rendimiento": litros acopiados por zona (mismo orden que {@code
 * ReporteQuesoSemanalResponse.zonas}), el total acopiado, la produccion de queso del dia y el
 * rendimiento (litros acopiados / quesos producidos).
 */
record RendimientoDiaRow(
        LocalDate fecha, String dia, List<Double> litrosPorZona, double totalAcopio, int produccion, double rendimiento) {}

/** Fila diaria de ingresos por venta de queso, para el "Registro de ventas". */
record RegistroDiaRow(LocalDate fecha, String dia, double ingresos) {}

/** Un egreso (compra de insumo) dentro del ciclo, para el "Registro de ventas". */
record EgresoRow(LocalDate fecha, String documento, String descripcion, double monto) {}

/**
 * Reporte semanal de produccion y ventas de queso (RF-42, RF-63, RF-73), en el formato del cuaderno
 * fisico de la Unidad de la Cadena Productiva de Lacteos - Huata: venta de quesos por dia, rendimiento
 * de acopio por zona y registro de ingresos/egresos del ciclo.
 */
record ReporteQuesoSemanalResponse(
        LocalDate desde,
        LocalDate hasta,
        List<VentaQuesoDiaRow> ventaPorDia,
        int totalProducidos,
        int totalDespachados,
        int totalVendidos,
        int totalDiferencia,
        int totalQuesosEnDescuento,
        double stockAlmacen,
        List<String> zonas,
        List<RendimientoDiaRow> rendimientoPorDia,
        double totalAcopioCiclo,
        List<RegistroDiaRow> registroPorDia,
        double totalIngresos,
        double totalPagoProveedoresLeche,
        List<EgresoRow> egresos,
        double totalEgresos,
        double totalSaldo) {}
