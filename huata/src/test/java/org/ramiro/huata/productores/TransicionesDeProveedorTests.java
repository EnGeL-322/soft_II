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
 * La maquina de estados aplicada sobre el service: que las transicion prohibidas fallen de verdad,
 * que el motivo sea obligatorio y que el alta nazca sin poder entregar (DESARROLLO.md 7.3).
 *
 * <p>{@link EstadoProductorTest} cubre la matriz como logica pura; esto comprueba que el service la
 * respeta en vez de limitarse a asignar el campo.
 */
@SpringBootTest
@Transactional
class TransicionesDeProveedorTests {

    @Autowired
    private ProductorService productorService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @BeforeEach
    void limpiarBitacora() {
        auditoriaRepository.deleteAll();
    }

    /** Cada caso necesita su propio DNI: el DNI es el codigo y no puede repetirse. */
    private Productor alta(String dni) {
        return productorService.crear(
                new ProductorRequest(
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
                        "Huata2026*"));
    }

    /**
     * Un proveedor dado de alta antes del 2026-10-26, cuando las altas nacian PENDIENTE_APROBACION.
     * Siguen existiendo en la base y se aprueban o rechazan con la misma transicion de siempre.
     */
    private Productor pendienteHeredado(String dni) {
        Productor p = alta(dni);
        p.setEstado(EstadoProductor.PENDIENTE_APROBACION);
        return productorRepository.saveAndFlush(p);
    }

    private Productor mover(Productor productor, EstadoProductor destino, String motivo) {
        return productorService.cambiarEstado(productor.getId(), new ProductorEstadoRequest(destino, motivo));
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elAltaNaceActivaYPuedeEntregarSinAprobacion() {
        // Decision del cliente (2026-10-26): quien da de alta ya lo revisa; no hay segundo paso.
        Productor creado = alta("44556201");

        assertThat(creado.getEstado()).isEqualTo(EstadoProductor.ACTIVO);
        productorService.exigirQuePuedeEntregar(creado); // no lanza
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void unPendienteHeredadoNoPuedeEntregarHastaAprobarse() {
        Productor pendiente = pendienteHeredado("44556211");

        assertThatThrownBy(() -> productorService.exigirQuePuedeEntregar(pendiente))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PENDIENTE_APROBACION");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void laAprobacionHabilitaAEntregar() {
        // Arrange
        Productor creado = pendienteHeredado("44556202");

        // Act
        Productor aprobado = mover(creado, EstadoProductor.ACTIVO, "Documentacion conforme");

        // Assert
        assertThat(aprobado.getEstado()).isEqualTo(EstadoProductor.ACTIVO);
        productorService.exigirQuePuedeEntregar(aprobado); // no lanza
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaTransicionQueLaMaquinaNoPermiteSeRechaza() {
        // Arrange: PENDIENTE_APROBACION no puede saltar directamente a EN_REVISION.
        Productor creado = pendienteHeredado("44556203");

        // Act / Assert
        assertThatThrownBy(() -> mover(creado, EstadoProductor.EN_REVISION, "Observado"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Transicion no permitida");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void nadieReactivaAUnRetiradoPorLaViaDelCambioDeEstado() {
        // Arrange: desde el 2026-09-27 administracion puede retirar a mano (RN-07), pero el retiro
        // sigue siendo terminal.
        Productor activo = alta("44556204");
        Productor retirado = mover(activo, EstadoProductor.RETIRADO, "Reincidencia");
        assertThat(retirado.getEstado()).isEqualTo(EstadoProductor.RETIRADO);

        // Act / Assert
        assertThatThrownBy(() -> mover(retirado, EstadoProductor.ACTIVO, "Readmision"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Transicion no permitida");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void cambiarAlMismoEstadoSeRechazaEnVezDeGenerarUnRegistroVacio() {
        // Arrange
        Productor activo = alta("44556205");
        auditoriaRepository.deleteAll();

        // Act / Assert
        assertThatThrownBy(() -> mover(activo, EstadoProductor.ACTIVO, "Otra vez"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya esta ACTIVO");
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elMotivoEsObligatorioYNoValeUnEspacioEnBlanco() {
        // Arrange
        Productor creado = alta("44556206");

        // Act / Assert
        assertThatThrownBy(() -> mover(creado, EstadoProductor.INACTIVO, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> mover(creado, EstadoProductor.INACTIVO, "   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void elCicloCompletoActivoInactivoActivoFunciona() {
        // Arrange
        Productor activo = alta("44556207");

        // Act / Assert, paso a paso.
        //
        // Se comprueba cada estado ANTES de la siguiente transicion, y no al final. Dentro de un
        // test @Transactional todas las llamadas comparten el mismo contexto de persistencia, asi
        // que el service devuelve siempre LA MISMA instancia gestionada: guardarla en una variable
        // y mirarla despues de otra transicion muestra el estado final, no el que tenia entonces.
        Productor inactivo = mover(activo, EstadoProductor.INACTIVO, "Se retira por temporada");
        assertThat(inactivo.getEstado()).isEqualTo(EstadoProductor.INACTIVO);
        assertThatThrownBy(() -> productorService.exigirQuePuedeEntregar(inactivo))
                .isInstanceOf(BusinessRuleException.class);

        Productor reactivado = mover(inactivo, EstadoProductor.ACTIVO, "Vuelve a entregar");
        assertThat(reactivado.getEstado()).isEqualTo(EstadoProductor.ACTIVO);
        productorService.exigirQuePuedeEntregar(reactivado); // no lanza
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unProveedorObservadoNoEntregaHastaQueSeResuelve() {
        // Arrange
        Productor activo = alta("44556208");

        // Act
        Productor observado = mover(activo, EstadoProductor.EN_REVISION, "Densidad fuera de banda");

        // Assert
        assertThatThrownBy(() -> productorService.exigirQuePuedeEntregar(observado))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("EN_REVISION");

        // Y al resolverse, vuelve a poder entregar.
        Productor resuelto = mover(observado, EstadoProductor.ACTIVO, "Observacion levantada");
        productorService.exigirQuePuedeEntregar(resuelto); // no lanza
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void elMotivoQuedaEnLasNotasYEnLaBitacora() {
        // Arrange
        Productor activo = alta("44556209");
        auditoriaRepository.deleteAll();

        // Act
        Productor inactivo = mover(activo, EstadoProductor.INACTIVO, "Tres rechazos seguidos");

        // Assert
        assertThat(inactivo.getNotas()).contains("[INACTIVO] Tres rechazos seguidos");
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActor()).isEqualTo("secretaria.demo@huata.pe");
                    assertThat(r.getDetalle()).contains("ACTIVO -> INACTIVO").contains("Tres rechazos seguidos");
                });
    }

    // ---- Aprobar un alta exige que el proveedor esté listo para trabajar ----

    @Autowired
    private ProductorRepository productorRepository;

    /**
     * Antes se podia aprobar a alguien sin ruta (ningun acopiador lo veia) o con datos que no cumplen
     * la regla, y al corregirlo despues el formulario lo rechazaba sin decir por que. Ahora se dice
     * todo lo que falta, de una vez, antes de aprobar.
     */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void noSeApruebaUnAltaSinRutaNiDniValidoYSeDiceTodoLoQueFalta() {
        Productor creado = pendienteHeredado("44556290");
        // Datos heredados: asi estan hoy los pendientes que venian de antes de las reglas.
        creado.setRuta(null);
        creado.setDocumentoIdentidad("987654321");
        productorRepository.saveAndFlush(creado);

        assertThatThrownBy(() -> mover(creado, EstadoProductor.ACTIVO, "Aprobado"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("un DNI válido de 8 dígitos")
                .hasMessageContaining("una ruta asignada")
                .hasMessageContaining("Editar");
        assertThat(productorRepository.findById(creado.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoProductor.PENDIENTE_APROBACION);
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void unPendienteHeredadoCompletoSeApruebaSinErrores() {
        Productor creado = pendienteHeredado("44556291");

        assertThat(mover(creado, EstadoProductor.ACTIVO, "Visita de verificación hecha").getEstado())
                .isEqualTo(EstadoProductor.ACTIVO);
    }

    /** La exigencia es de la APROBACIÓN: resolver una revisión de un proveedor heredado sigue igual. */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void resolverUnaRevisionNoExigeLosDatosDelAlta() {
        Productor creado = alta("44556292");
        mover(creado, EstadoProductor.EN_REVISION, "Observado");
        creado.setDocumentoIdentidad(null);
        productorRepository.saveAndFlush(creado);

        assertThat(mover(creado, EstadoProductor.ACTIVO, "Observación resuelta").getEstado())
                .isEqualTo(EstadoProductor.ACTIVO);
    }
}
