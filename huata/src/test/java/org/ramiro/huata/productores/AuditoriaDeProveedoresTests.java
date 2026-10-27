package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.auditoria.RegistroAuditoria;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criterio de salida de la Fase 1: "ninguna escritura de Provider ocurre sin su registro en
 * AuditLog (probado)".
 *
 * <p>Vive en el paquete del modulo porque sus DTOs son package-private, y eso esta bien: un test no
 * es razon para abrir la encapsulacion de un modulo.
 *
 * <p>Se ejercita el SERVICE y no el controller: si la auditoria viviera en el controller, cualquier
 * otra via de escritura —una importacion de Excel, un comando, un job— entraria sin dejar rastro.
 *
 * <p>Las aserciones miran el ACTOR ademas de la accion. Un registro que no dice quien lo hizo no
 * cumple el criterio: la mitad del valor de la bitacora es poder responder "quien".
 */
@SpringBootTest
@Transactional
class AuditoriaDeProveedoresTests {

    @Autowired
    private ProductorService productorService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @BeforeEach
    void limpiarBitacora() {
        // Los datos DEMO dejan registros propios; se parte de cero para que cada asercion hable solo
        // de lo que hizo su propia prueba.
        auditoriaRepository.deleteAll();
    }

    private List<RegistroAuditoria> registros() {
        return auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 50)).getContent();
    }

    /**
     * El DNI es ahora la identidad del proveedor: es su codigo, y no puede repetirse. Antes cada caso
     * se distinguia con un codigo tipo "AUD-001"; ahora se distingue con un DNI propio de 8 digitos.
     */
    private ProductorRequest solicitud(String dni) {
        return new ProductorRequest(
                dni,
                "Ana",
                "Quispe Mamani",
                "951000111",
                rutaRepository.findAll().get(0).getId(),
                null,
                null,
                null,
                null,
                "prov" + dni + "@huata.pe",
                "Huata2026*");
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void elAltaQuedaRegistradaConSuAutor() {
        // Arrange / Act
        Productor creado = productorService.crear(solicitud("44556601"));

        // Assert
        assertThat(creado.getUsuario().getUsername()).isEqualTo("prov44556601@huata.pe");
        assertThat(creado.getUsuario().getRoles()).extracting("nombre").containsExactly("PROVEEDOR");
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Alta");
            assertThat(r.getEntidad()).isEqualTo("Proveedores");
            assertThat(r.getActor()).isEqualTo("calidad.demo@huata.pe");
            assertThat(r.getDetalle()).contains(creado.getCodigo());
        });
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void laModificacionQuedaRegistrada() {
        // Arrange
        Productor creado = productorService.crear(solicitud("44556602"));
        auditoriaRepository.deleteAll();

        // Act
        productorService.actualizar(
                creado.getId(),
                new ProductorRequest(
                        "44556602", "Ana Maria", "Quispe Mamani", "951000222", creado.getRuta().getId(), null, null, null,
                        null,
                        creado.getUsuario().getUsername(),
                        null));

        // Assert
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Modificacion");
            assertThat(r.getActor()).isEqualTo("admin@huata.pe");
        });
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void elCambioDeEstadoRegistraDeDondeVeniaYPorQue() {
        // Arrange: el alta nace ACTIVO (2026-10-26), asi que puede pasar directo a EN_REVISION.
        Productor creado = productorService.crear(solicitud("44556603"));
        EstadoProductor inicial = EstadoProductor.ACTIVO;
        auditoriaRepository.deleteAll();

        // Act
        productorService.cambiarEstado(
                creado.getId(),
                new ProductorEstadoRequest(EstadoProductor.EN_REVISION, "Tercera muestra rechazada"));

        // Assert: sin el estado anterior el registro no permite reconstruir la historia del proveedor.
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Cambio de estado");
            assertThat(r.getActor()).isEqualTo("secretaria.demo@huata.pe");
            assertThat(r.getDetalle())
                    .contains(inicial.name())
                    .contains(EstadoProductor.EN_REVISION.name())
                    .contains("Tercera muestra rechazada");
        });
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void laInactivacionQuedaRegistrada() {
        // Arrange
        Productor creado = productorService.crear(solicitud("44556604"));
        auditoriaRepository.deleteAll();

        // Act
        productorService.inactivar(creado.getId());

        // Assert
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Inactivacion");
            assertThat(r.getDetalle()).contains("44556604");
        });
    }

    /** Un alta rechazada por regla de negocio no escribio nada, asi que no debe auditar nada. */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void unAltaRechazadaPorDniDuplicadoNoDejaRegistro() {
        // Arrange
        productorService.crear(solicitud("44556605"));
        auditoriaRepository.deleteAll();

        // Act
        try {
            productorService.crear(solicitud("44556605"));
        } catch (RuntimeException esperada) {
            // La regla de negocio es el objeto de otra prueba; aqui solo interesa la bitacora.
        }

        // Assert
        assertThat(registros()).isEmpty();
    }
}
