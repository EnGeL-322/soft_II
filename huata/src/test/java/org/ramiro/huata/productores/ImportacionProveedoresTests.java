package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.exportacion.ExportacionService;
import org.ramiro.huata.productores.ImportacionProveedoresService.Accion;
import org.ramiro.huata.productores.ImportacionProveedoresService.ResultadoImportacion;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Importar el padron desde Excel (DESARROLLO.md 12): por los mismos services, aditiva o de
 * actualizacion por Codigo, nunca reemplazo; vista previa sin efectos; todo o nada.
 *
 * <p>Sin @Transactional a proposito: la importacion abre su propia transaccion y la deshace en la
 * vista previa o ante un error. Dentro de la transaccion de una prueba ese "deshacer" no ocurriria
 * hasta el final y no se podria comprobar. Los datos usan DNI y rutas al azar para no chocar.
 */
@SpringBootTest
@WithMockUser(username = "admin.pruebas@huata.pe")
class ImportacionProveedoresTests {

    private static final List<String> COLUMNAS =
            List.of("Codigo", "Nombres", "Apellidos", "Ruta", "Telefono", "Usuario", "Clave_inicial");

    @Autowired
    private ImportacionProveedoresService importacion;

    @Autowired
    private ProductorService productorService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private ExportacionService exportacionService;

    @Autowired
    private WebApplicationContext contexto;

    private Ruta ruta;
    private Productor existente;

    private static String dni() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }

    @BeforeEach
    void escenario() {
        ruta = new Ruta();
        ruta.setNombre("Ruta Import " + UUID.randomUUID().toString().substring(0, 6));
        ruta.setZona("ZONA-IMPORT");
        ruta = rutaRepository.save(ruta);
        String codigo = dni();
        existente = productorService.crear(new ProductorRequest(codigo, "Juana", "Ccama", null, ruta.getId(),
                "nota previa", null, null, null, "imp." + codigo + "@huata.pe", "Clave2026*"));
    }

    private static byte[] excel(List<List<String>> filas) {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Proveedores");
            Row cab = hoja.createRow(0);
            for (int i = 0; i < COLUMNAS.size(); i++) cab.createCell(i).setCellValue(COLUMNAS.get(i));
            for (int r = 0; r < filas.size(); r++) {
                Row fila = hoja.createRow(r + 1);
                for (int c = 0; c < filas.get(r).size(); c++) fila.createCell(c).setCellValue(filas.get(r).get(c));
            }
            libro.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<String> filaNueva(String codigo) {
        return List.of(codigo, "Pedro", "Huanca", ruta.getNombre(), "", "imp." + codigo + "@huata.pe", "Clave2026*");
    }

    private List<String> filaExistente(String nombres) {
        return List.of(existente.getCodigo(), nombres, "Ccama", ruta.getNombre(), "951234567", "", "");
    }

    @Test
    void laVistaPreviaDiceLoQuePasaraPeroNoGuardaNada() {
        String nuevo = dni();

        ResultadoImportacion r = importacion.procesar(excel(List.of(filaNueva(nuevo), filaExistente("Juana Rosa"))), false);

        assertThat(r.aplicado()).isFalse();
        assertThat(r.crear()).isEqualTo(1);
        assertThat(r.actualizar()).isEqualTo(1);
        assertThat(productorRepository.findByCodigoIgnoreCase(nuevo)).isEmpty();
        assertThat(productorRepository.findById(existente.getId()).orElseThrow().getNombres()).isEqualTo("Juana");
    }

    @Test
    void alConfirmarCreaActivoYActualizaSinPerderLoQueElArchivoNoTrae() {
        String nuevo = dni();

        ResultadoImportacion r = importacion.procesar(excel(List.of(filaNueva(nuevo), filaExistente("Juana Rosa"))), true);

        assertThat(r.aplicado()).isTrue();
        Productor creado = productorRepository.findByCodigoIgnoreCase(nuevo).orElseThrow();
        assertThat(creado.getEstado()).isEqualTo(EstadoProductor.ACTIVO);
        assertThat(creado.getUsuario()).isNotNull();
        Productor actualizado = productorRepository.findById(existente.getId()).orElseThrow();
        assertThat(actualizado.getNombres()).isEqualTo("Juana Rosa");
        assertThat(actualizado.getTelefono()).isEqualTo("951234567");
        assertThat(actualizado.getNotas()).isEqualTo("nota previa");
        assertThat(actualizado.getUsuario().getUsername()).isEqualTo(existente.getUsuario().getUsername());
    }

    @Test
    void siUnaFilaTieneErrorNoSeAplicaNingunaAunqueSeConfirme() {
        String nuevo = dni();

        ResultadoImportacion r = importacion.procesar(excel(List.of(
                filaNueva(nuevo),
                List.of("123", "Mal", "Dni", ruta.getNombre(), "", "x@huata.pe", "Clave2026*"))), true);

        assertThat(r.aplicado()).isFalse();
        assertThat(r.errores()).isEqualTo(1);
        assertThat(r.filas()).filteredOn(f -> f.accion() == Accion.ERROR).singleElement()
                .satisfies(f -> assertThat(f.fila()).isEqualTo(3));
        assertThat(productorRepository.findByCodigoIgnoreCase(nuevo)).isEmpty();
    }

    @Test
    void unaRutaQueNoExisteSeExplicaEnLaFila() {
        ResultadoImportacion r = importacion.procesar(excel(List.of(
                List.of(existente.getCodigo(), "Juana", "Ccama", "Ruta Fantasma", "", "", ""))), false);

        assertThat(r.filas().get(0).mensaje()).contains("Ruta Fantasma");
    }

    @Test
    void elExcelQueExportaProveedoresSePuedeVolverAImportar() {
        ResultadoImportacion r = importacion.procesar(exportacionService.exportar("Proveedores"), false);

        assertThat(r.filas()).filteredOn(f -> f.codigo().equals(existente.getCodigo())).singleElement()
                .satisfies(f -> assertThat(f.accion()).isEqualTo(Accion.ACTUALIZAR));
        assertThat(r.crear()).isZero();
    }

    @Test
    void sinProveedoresWriteNoSePuedeImportarAunqueTengaRespaldoWrite() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        var archivo = new MockMultipartFile("archivo", "p.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel(List.of(filaExistente("X"))));

        mvc.perform(multipart("/api/v1/importaciones/proveedores").file(archivo)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("p").authorities(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority(Permiso.RESPALDO_WRITE))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/importaciones/proveedores").file(archivo)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("p").authorities(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.RESPALDO_WRITE),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority(Permiso.PROVEEDORES_WRITE))))
                .andExpect(status().isOk());
    }
}
