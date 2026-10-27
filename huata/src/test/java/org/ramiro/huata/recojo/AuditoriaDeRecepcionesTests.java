package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.auditoria.RegistroAuditoria;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criterio de salida de la Fase 1: "ninguna escritura de Reception ocurre sin su registro en
 * AuditLog (probado)".
 *
 * <p>Los litros registrados son la base de la liquidacion del proveedor y una cancelacion le quita
 * dinero: son las dos escrituras del sistema donde mas falta hace poder responder "quien y por que".
 */
@SpringBootTest
@Transactional
class AuditoriaDeRecepcionesTests {

    // Un recojo en ruta exige el acopiador que lo hizo (RecojoService, 2026-10-14).
    @org.springframework.beans.factory.annotation.Autowired
    private org.ramiro.huata.acopiadores.AcopiadorRepository acopiadoresDePrueba;

    private Long acopiadorDePrueba() {
        return acopiadoresDePrueba.findByActivoTrueOrderByNombresAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    var a = new org.ramiro.huata.acopiadores.Acopiador();
                    a.setNombres("Acopiador");
                    a.setApellidos("De prueba");
                    return acopiadoresDePrueba.save(a);
                })
                .getId();
    }


    @Autowired
    private RecojoService recojoService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    private Productor proveedor;

    @BeforeEach
    void prepararEscenario() {
        proveedor = productorRepository.findAll().get(0);
        auditoriaRepository.deleteAll();
    }

    private List<RegistroAuditoria> registros() {
        return auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 50)).getContent();
    }

    private RecojoRequest recepcionDe(String uuid, double litros, String hora) {
        return new RecojoRequest(
                uuid, proveedor.getId(), acopiadorDePrueba(), null, Instant.parse(hora), litros, 6.4, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void elRegistroQuedaTrazadoConProveedorYLitros() {
        // Arrange / Act
        Recojo recepcion =
                recojoService.registrar(recepcionDe(UUID.randomUUID().toString(), 145.0, "2035-05-10T12:00:00Z"));

        // Assert
        assertThat(recepcion.getId()).isNotNull();
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Registro de recepcion");
            assertThat(r.getEntidad()).isEqualTo("Acopio");
            assertThat(r.getActor()).isEqualTo("calidad.demo@huata.pe");
            assertThat(r.getDetalle()).contains(proveedor.getCodigo()).contains("145");
        });
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void laCancelacionRegistraAutorYMotivo() {
        // Arrange
        Recojo recepcion =
                recojoService.registrar(recepcionDe(UUID.randomUUID().toString(), 98.0, "2035-05-10T13:00:00Z"));
        auditoriaRepository.deleteAll();

        // Act
        recojoService.cancelar(recepcion.getId(), new RecojoCancelRequest("Envase contaminado"));

        // Assert
        assertThat(registros()).singleElement().satisfies(r -> {
            assertThat(r.getAccion()).isEqualTo("Cancelacion de recepcion");
            assertThat(r.getActor()).isEqualTo("secretaria.demo@huata.pe");
            assertThat(r.getDetalle()).contains("Envase contaminado");
        });
    }

    /** Anular sin explicar tambien se registra: el hueco debe verse en la bitacora, no desaparecer. */
    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaCancelacionSinMotivoLoDiceExplicitamente() {
        // Arrange
        Recojo recepcion =
                recojoService.registrar(recepcionDe(UUID.randomUUID().toString(), 60.0, "2035-05-10T15:00:00Z"));
        auditoriaRepository.deleteAll();

        // Act
        recojoService.cancelar(recepcion.getId(), new RecojoCancelRequest(null));

        // Assert
        assertThat(registros()).singleElement().satisfies(r -> assertThat(r.getDetalle())
                .contains("sin motivo declarado"));
    }

    /**
     * El cliente movil reintenta con el mismo clientUuid cuando pierde la respuesta. Ese reintento
     * no escribe nada, asi que tampoco debe auditar: si lo hiciera, la bitacora mostraria
     * recepciones que nunca ocurrieron y no cuadraria con los litros realmente recibidos.
     */
    @Test
    @WithMockUser(username = "acopiador.demo@huata.pe")
    void elReintentoDelMovilNoGeneraUnSegundoRegistro() {
        // Arrange
        RecojoRequest peticion = recepcionDe(UUID.randomUUID().toString(), 77.0, "2035-05-10T14:00:00Z");
        recojoService.registrar(peticion);

        // Act
        recojoService.registrar(peticion);

        // Assert
        assertThat(registros()).hasSize(1);
    }

    /** Cancelar dos veces falla por regla de negocio; la segunda no escribio, no debe auditar. */
    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaCancelacionRepetidaNoDejaUnSegundoRegistro() {
        // Arrange
        Recojo recepcion =
                recojoService.registrar(recepcionDe(UUID.randomUUID().toString(), 55.0, "2035-05-10T16:00:00Z"));
        recojoService.cancelar(recepcion.getId(), new RecojoCancelRequest("Duplicada"));
        auditoriaRepository.deleteAll();

        // Act
        try {
            recojoService.cancelar(recepcion.getId(), new RecojoCancelRequest("Duplicada"));
        } catch (RuntimeException esperada) {
            // La regla la cubre otra prueba; aqui solo interesa que no se audite un no-cambio.
        }

        // Assert
        assertThat(registros()).isEmpty();
    }
}
