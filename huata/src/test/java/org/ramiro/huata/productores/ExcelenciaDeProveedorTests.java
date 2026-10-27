package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marca de Excelencia (DESARROLLO.md 7.4): administrable y auditada.
 *
 * <p>Cambia lo que se le paga al proveedor, asi que se trata como una decision y no como un campo
 * mas del formulario: exige motivo y deja registro.
 */
@SpringBootTest
@Transactional
class ExcelenciaDeProveedorTests {

    @Autowired
    private ProductorService productorService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private RutaRepository rutaRepository;

    private Productor proveedor;

    @BeforeEach
    void alta() {
        proveedor = productorService.crear(
                new ProductorRequest(
                        // El DNI es el codigo del proveedor; identifica la ficha de esta prueba.
                        "44556610",
                        "Ana",
                        "Quispe",
                        "951000111",
                        rutaRepository.findAll().get(0).getId(),
                        null,
                        null,
                        null,
                        null,
                        "prov44556610@huata.pe",
                        "Huata2026*"));
        auditoriaRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void unProveedorNaceSinLaMarca() {
        assertThat(proveedor.isExcelencia()).isFalse();
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void concederLaMarcaQuedaAuditadoConMotivo() {
        // Act
        Productor conMarca =
                productorService.cambiarExcelencia(proveedor.getId(), new ProductorExcelenciaRequest(true, "Seis ciclos sin incidencias"));

        // Assert
        assertThat(conMarca.isExcelencia()).isTrue();
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getAccion()).isEqualTo("Excelencia concedida");
                    assertThat(r.getActor()).isEqualTo("admin@huata.pe");
                    assertThat(r.getDetalle()).contains("44556610").contains("Seis ciclos sin incidencias");
                });
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void retirarLaMarcaTambienQuedaAuditado() {
        // Arrange
        productorService.cambiarExcelencia(proveedor.getId(), new ProductorExcelenciaRequest(true, "Concedida"));
        auditoriaRepository.deleteAll();

        // Act
        Productor sinMarca = productorService.cambiarExcelencia(
                proveedor.getId(), new ProductorExcelenciaRequest(false, "Dos rechazos en el ciclo"));

        // Assert
        assertThat(sinMarca.isExcelencia()).isFalse();
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> assertThat(r.getAccion()).isEqualTo("Excelencia retirada"));
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elMotivoEsObligatorio() {
        assertThatThrownBy(() ->
                        productorService.cambiarExcelencia(proveedor.getId(), new ProductorExcelenciaRequest(true, "  ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Excelencia exige un motivo");
    }

    /** Conceder lo que ya se tiene no cambia nada; registrarlo ensuciaria la bitacora. */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void concederDosVecesSeRechazaYNoDejaRegistro() {
        productorService.cambiarExcelencia(proveedor.getId(), new ProductorExcelenciaRequest(true, "Concedida"));
        auditoriaRepository.deleteAll();

        assertThatThrownBy(() -> productorService.cambiarExcelencia(
                        proveedor.getId(), new ProductorExcelenciaRequest(true, "Otra vez")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene");
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    /**
     * La marca no es un estado: sobrevive a los cambios de estado. Si se perdiera al inactivar y
     * reactivar a alguien, habria que reconcederla a mano y nadie se acordaria.
     */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void laMarcaSobreviveAlCicloDeEstados() {
        productorService.cambiarExcelencia(proveedor.getId(), new ProductorExcelenciaRequest(true, "Concedida"));
        productorService.cambiarEstado(
                proveedor.getId(), new ProductorEstadoRequest(EstadoProductor.INACTIVO, "Temporada"));
        Productor reactivado = productorService.cambiarEstado(
                proveedor.getId(), new ProductorEstadoRequest(EstadoProductor.ACTIVO, "Vuelve"));

        assertThat(reactivado.isExcelencia()).isTrue();
    }
}
