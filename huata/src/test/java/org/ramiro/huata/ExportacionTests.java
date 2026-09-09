package org.ramiro.huata;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.exportacion.ExportacionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ExportacionTests {
    @Autowired ExportacionService exportaciones;

    @Test void libroContieneTodasLasHojasYDatosReales() throws Exception {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(exportaciones.exportarLibro()))) {
            assertEquals(ExportacionService.HOJAS_DISPONIBLES.size(), libro.getNumberOfSheets());
            for (var nombre : ExportacionService.HOJAS_DISPONIBLES) assertNotNull(libro.getSheet(nombre));
            assertTrue(libro.getSheet("Proveedores").getLastRowNum() > 0);
            assertEquals("Codigo", libro.getSheet("Proveedores").getRow(0).getCell(0).getStringCellValue());
        }
        assertEquals(ExportacionService.HOJAS_DISPONIBLES, exportaciones.exportarDatos().keySet().stream().toList());
    }
}
