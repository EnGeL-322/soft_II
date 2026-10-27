package org.ramiro.huata.reportes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Genera el Excel del reporte semanal de queso con el mismo formato que el cuaderno fisico de la
 * Unidad de la Cadena Productiva de Lacteos - Huata: una hoja de venta de quesos por dia, una de
 * rendimiento de acopio por zona y una de registro de ingresos/egresos del ciclo.
 */
@Service
public class ReporteQuesoExcelService {

    public byte[] generar(ReporteQuesoSemanalResponse reporte) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle titulo = estiloTitulo(workbook);
            CellStyle encabezado = estiloEncabezado(workbook);
            CellStyle totalEstilo = estiloTotal(workbook);

            hojaVentaQuesos(workbook, reporte, titulo, encabezado, totalEstilo);
            hojaRendimiento(workbook, reporte, titulo, encabezado, totalEstilo);
            hojaRegistroVentas(workbook, reporte, titulo, encabezado, totalEstilo);

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte semanal de queso", e);
        }
    }

    // ---- Hoja 1: REPORTE DE VENTA DE QUESOS ----

    private void hojaVentaQuesos(
            Workbook workbook, ReporteQuesoSemanalResponse r, CellStyle titulo, CellStyle encabezado, CellStyle totalEstilo) {
        Sheet hoja = workbook.createSheet("Venta de quesos");
        int fila = 0;
        celda(hoja, fila++, 0, "REPORTE DE VENTA DE QUESOS · " + r.desde() + " al " + r.hasta(), titulo);
        fila++;

        Row cabecera = hoja.createRow(fila++);
        String[] columnas = {"DIA", "FECHA", "QUESOS PRODUCIDOS", "QUESOS DESPACHADOS", "QUESOS VENDIDOS", "DIFERENCIA"};
        for (int i = 0; i < columnas.length; i++) {
            celda(cabecera, i, columnas[i], encabezado);
        }

        for (VentaQuesoDiaRow dia : r.ventaPorDia()) {
            Row fd = hoja.createRow(fila++);
            celda(fd, 0, dia.dia());
            celda(fd, 1, dia.fecha().toString());
            celda(fd, 2, dia.producidos());
            celda(fd, 3, dia.despachados());
            celda(fd, 4, dia.vendidos());
            celda(fd, 5, dia.diferencia());
        }

        Row totales = hoja.createRow(fila++);
        celda(totales, 0, "TOTAL", totalEstilo);
        celda(totales, 1, "", totalEstilo);
        celda(totales, 2, r.totalProducidos(), totalEstilo);
        celda(totales, 3, r.totalDespachados(), totalEstilo);
        celda(totales, 4, r.totalVendidos(), totalEstilo);
        celda(totales, 5, r.totalDiferencia(), totalEstilo);

        fila++;
        celda(hoja.createRow(fila++), 0, "TOTAL DE QUESOS EN DESCUENTO", encabezado);
        celda(hoja.getRow(fila - 1), 1, r.totalQuesosEnDescuento());

        celda(hoja.createRow(fila++), 0, "TOTAL EN ALMACÉN (stock real, kardex)", encabezado);
        celda(hoja.getRow(fila - 1), 1, r.stockAlmacen());

        autoAjustar(hoja, columnas.length);
    }

    // ---- Hoja 2: REPORTE DE RENDIMIENTO ----

    private void hojaRendimiento(
            Workbook workbook, ReporteQuesoSemanalResponse r, CellStyle titulo, CellStyle encabezado, CellStyle totalEstilo) {
        Sheet hoja = workbook.createSheet("Rendimiento");
        int fila = 0;
        celda(hoja, fila++, 0, "REPORTE DE RENDIMIENTO · " + r.desde() + " al " + r.hasta(), titulo);
        fila++;

        int columnas = 1 + r.rendimientoPorDia().size();

        Row cabeceraZonas = hoja.createRow(fila++);
        celda(cabeceraZonas, 0, "ZONAS", encabezado);
        for (int i = 0; i < r.rendimientoPorDia().size(); i++) {
            celda(cabeceraZonas, 1 + i, r.rendimientoPorDia().get(i).dia() + " " + r.rendimientoPorDia().get(i).fecha(), encabezado);
        }

        for (int z = 0; z < r.zonas().size(); z++) {
            Row filaZona = hoja.createRow(fila++);
            celda(filaZona, 0, r.zonas().get(z));
            for (int d = 0; d < r.rendimientoPorDia().size(); d++) {
                celda(filaZona, 1 + d, r.rendimientoPorDia().get(d).litrosPorZona().get(z));
            }
        }

        Row totalAcopio = hoja.createRow(fila++);
        celda(totalAcopio, 0, "TOTAL ACOPIO", totalEstilo);
        for (int d = 0; d < r.rendimientoPorDia().size(); d++) {
            celda(totalAcopio, 1 + d, r.rendimientoPorDia().get(d).totalAcopio(), totalEstilo);
        }

        fila++;
        Row produccion = hoja.createRow(fila++);
        celda(produccion, 0, "CANTIDAD DE PRODUCCIÓN", encabezado);
        for (int d = 0; d < r.rendimientoPorDia().size(); d++) {
            celda(produccion, 1 + d, r.rendimientoPorDia().get(d).produccion());
        }

        fila++;
        Row rendimiento = hoja.createRow(fila++);
        celda(rendimiento, 0, "RENDIMIENTO (L/queso)", encabezado);
        for (int d = 0; d < r.rendimientoPorDia().size(); d++) {
            celda(rendimiento, 1 + d, r.rendimientoPorDia().get(d).rendimiento());
        }

        autoAjustar(hoja, columnas);
    }

    // ---- Hoja 3: REGISTRO DE VENTAS (ingresos / egresos) ----

    private void hojaRegistroVentas(
            Workbook workbook, ReporteQuesoSemanalResponse r, CellStyle titulo, CellStyle encabezado, CellStyle totalEstilo) {
        Sheet hoja = workbook.createSheet("Registro de ventas");
        int fila = 0;
        celda(hoja, fila++, 0, "REGISTRO DE VENTAS · " + r.desde() + " al " + r.hasta(), titulo);
        fila++;

        Row cabecera = hoja.createRow(fila++);
        celda(cabecera, 0, "FECHA", encabezado);
        celda(cabecera, 1, "DIA", encabezado);
        celda(cabecera, 2, "INGRESOS", encabezado);

        for (RegistroDiaRow dia : r.registroPorDia()) {
            Row fd = hoja.createRow(fila++);
            celda(fd, 0, dia.fecha().toString());
            celda(fd, 1, dia.dia());
            celda(fd, 2, dia.ingresos());
        }

        Row totalIngresos = hoja.createRow(fila++);
        celda(totalIngresos, 0, "TOTAL DE INGRESOS", totalEstilo);
        celda(totalIngresos, 2, r.totalIngresos(), totalEstilo);

        Row pagoProveedores = hoja.createRow(fila++);
        celda(pagoProveedores, 0, "PAGO A LOS PROVEEDORES DE LECHE", encabezado);
        celda(pagoProveedores, 2, r.totalPagoProveedoresLeche());

        fila++;
        Row cabeceraEgresos = hoja.createRow(fila++);
        celda(cabeceraEgresos, 0, "FECHA", encabezado);
        celda(cabeceraEgresos, 1, "DOCUMENTO", encabezado);
        celda(cabeceraEgresos, 2, "DESCRIPCIÓN", encabezado);
        celda(cabeceraEgresos, 3, "MONTO", encabezado);

        for (EgresoRow egreso : r.egresos()) {
            Row fe = hoja.createRow(fila++);
            celda(fe, 0, egreso.fecha().toString());
            celda(fe, 1, egreso.documento());
            celda(fe, 2, egreso.descripcion());
            celda(fe, 3, egreso.monto());
        }

        Row totalEgresos = hoja.createRow(fila++);
        celda(totalEgresos, 0, "TOTAL DE EGRESOS", totalEstilo);
        celda(totalEgresos, 3, r.totalEgresos(), totalEstilo);

        Row totalSaldo = hoja.createRow(fila++);
        celda(totalSaldo, 0, "TOTAL DE SALDO", totalEstilo);
        celda(totalSaldo, 3, r.totalSaldo(), totalEstilo);

        autoAjustar(hoja, 4);
    }

    // ---- Helpers ----

    private void celda(Sheet hoja, int filaIdx, int colIdx, String valor, CellStyle estilo) {
        Row row = hoja.createRow(filaIdx);
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private void celda(Row row, int colIdx, String valor, CellStyle estilo) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private void celda(Row row, int colIdx, String valor) {
        row.createCell(colIdx).setCellValue(valor);
    }

    private void celda(Row row, int colIdx, double valor) {
        row.createCell(colIdx).setCellValue(valor);
    }

    private void celda(Row row, int colIdx, double valor, CellStyle estilo) {
        Cell cell = row.createCell(colIdx);
        cell.setCellValue(valor);
        cell.setCellStyle(estilo);
    }

    private void autoAjustar(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) {
            hoja.autoSizeColumn(i);
        }
    }

    private CellStyle estiloTitulo(Workbook workbook) {
        Font negrita = workbook.createFont();
        negrita.setBold(true);
        negrita.setFontHeightInPoints((short) 12);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(negrita);
        return estilo;
    }

    private CellStyle estiloEncabezado(Workbook workbook) {
        Font negrita = workbook.createFont();
        negrita.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(negrita);
        return estilo;
    }

    private CellStyle estiloTotal(Workbook workbook) {
        Font negrita = workbook.createFont();
        negrita.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(negrita);
        estilo.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        return estilo;
    }
}
