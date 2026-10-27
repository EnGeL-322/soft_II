package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link RecojoService}: registro, cancelacion y los filtros de busqueda.
 *
 * <p>Los filtros importan mas de lo que parece: la pantalla de acopio, el cierre de jornada y la
 * liquidacion salen todos de esta consulta. Un filtro que no filtra suma litros de otro proveedor o
 * de otro dia a una planilla de pago.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "calidad.demo@huata.pe")
class RecojoServiceTests {

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
    private RecojoRepository recojoRepository;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    private Productor proveedorA;
    private Productor proveedorB;

    @BeforeEach
    void escenario() {
        proveedorA = productorRepository.findAll().get(0);
        proveedorB = productorRepository.findAll().get(1);
    }

    private RecojoRequest peticion(Productor p, double litros, String hora) {
        return new RecojoRequest(
                UUID.randomUUID().toString(), p.getId(), acopiadorDePrueba(), null, Instant.parse(hora), litros, 6.4, null, null,
                null, null, null, null, null, null, null, null);
    }

    // ------------------------------------------------------------------------------- Registro

    @Test
    void registrarDevuelveLaRecepcionPersistida() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 120.0, "2035-05-01T12:00:00Z"));

        assertThat(r.getId()).isNotNull();
        assertThat(r.getEstado()).isEqualTo(EstadoRecojo.SYNCED);
        assertThat(recojoRepository.findById(r.getId())).isPresent();
    }

    /** Sin clientUuid el servidor genera uno: el cliente movil no siempre lo manda. */
    @Test
    void sinClientUuidSeGeneraUno() {
        Recojo r = recojoService.registrar(new RecojoRequest(
                null, proveedorA.getId(), acopiadorDePrueba(), null, Instant.parse("2035-05-01T12:30:00Z"), 90.0, 6.0, null, null,
                null, null, null, null, null, null, null, null));

        assertThat(r.getClientUuid()).isNotBlank();
    }

    /** Idempotencia: el reintento devuelve la misma recepcion, no una segunda. */
    @Test
    void elMismoClientUuidNoCreaDosRecepciones() {
        RecojoRequest p = peticion(proveedorA, 100.0, "2035-05-01T13:00:00Z");

        Recojo primera = recojoService.registrar(p);
        Recojo segunda = recojoService.registrar(p);

        assertThat(segunda.getId()).isEqualTo(primera.getId());
    }

    @Test
    void laRutaSeHeredaDelProveedorSiNoSeIndica() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 80.0, "2035-05-01T13:30:00Z"));

        if (proveedorA.getRuta() != null) {
            assertThat(r.getRuta()).isNotNull();
            assertThat(r.getRuta().getId()).isEqualTo(proveedorA.getRuta().getId());
        }
    }

    /** Un proveedor entrega leche una sola vez al dia: la segunda entrega real del mismo dia se rechaza. */
    @Test
    void unaSegundaEntregaRealElMismoDiaFalla() {
        recojoService.registrar(peticion(proveedorA, 50.0, "2035-05-03T08:00:00Z"));

        assertThatThrownBy(() -> recojoService.registrar(peticion(proveedorA, 30.0, "2035-05-03T18:00:00Z")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("una sola vez al día");
    }

    /** Un NO_ENTREGADO no cuenta como entrega: no bloquea que ese mismo dia se registre la entrega real. */
    @Test
    void unNoEntregadoNoBloqueaLaEntregaRealDelMismoDia() {
        recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), proveedorA.getId(), acopiadorDePrueba(), null,
                Instant.parse("2035-05-03T08:00:00Z"), 0.0, null, null, null, null, null, null,
                EstadoVisita.NO_ENTREGADO, "No tuvo leche", null, null, null));

        Recojo r = recojoService.registrar(peticion(proveedorA, 45.0, "2035-05-03T18:00:00Z"));

        assertThat(r.getId()).isNotNull();
    }

    /** Un dia distinto para el mismo proveedor no esta restringido. */
    @Test
    void elMismoProveedorPuedeEntregarEnDiasDistintos() {
        recojoService.registrar(peticion(proveedorA, 50.0, "2035-05-03T08:00:00Z"));

        Recojo r = recojoService.registrar(peticion(proveedorA, 55.0, "2035-05-04T08:00:00Z"));

        assertThat(r.getId()).isNotNull();
    }

    @Test
    void registrarParaUnProveedorInexistenteFalla() {
        assertThatThrownBy(() -> recojoService.registrar(new RecojoRequest(
                        UUID.randomUUID().toString(), 999_999L, acopiadorDePrueba(), null, Instant.now(), 10.0, 6.0, null, null,
                        null, null, null, null, null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    /** 7.3: la guardia de estado tambien protege esta via, no solo el formulario. */
    @Test
    void unProveedorNoActivoNoPuedeEntregar() {
        Productor inactivo = productorRepository.findAll().stream()
                .filter(p -> p.getEstado() != EstadoProductor.ACTIVO)
                .findFirst()
                .orElseGet(() -> {
                    proveedorB.setEstado(EstadoProductor.EN_REVISION);
                    return productorRepository.save(proveedorB);
                });

        assertThatThrownBy(() -> recojoService.registrar(peticion(inactivo, 50.0, "2035-05-01T14:00:00Z")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no puede entregar");
    }

    // ---------------------------------------------------------------------------- Cancelacion

    @Test
    void cancelarDejaLaRecepcionEnCancelledConSuMotivo() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 70.0, "2035-05-01T15:00:00Z"));

        Recojo cancelada = recojoService.cancelar(r.getId(), new RecojoCancelRequest("Envase contaminado"));

        assertThat(cancelada.getEstado()).isEqualTo(EstadoRecojo.CANCELLED);
        assertThat(cancelada.getMotivoCancelacion()).isEqualTo("Envase contaminado");
    }

    @Test
    void cancelarDosVecesSeRechaza() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 60.0, "2035-05-01T15:30:00Z"));
        recojoService.cancelar(r.getId(), new RecojoCancelRequest("Duplicada"));

        assertThatThrownBy(() -> recojoService.cancelar(r.getId(), new RecojoCancelRequest("Otra vez")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya esta cancelado");
    }

    @Test
    void cancelarUnaRecepcionInexistenteFalla() {
        assertThatThrownBy(() -> recojoService.cancelar(999_999L, new RecojoCancelRequest("x")))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------------------- Consulta

    @Test
    void obtenerRespuestaDevuelveLaVistaDeLaRecepcion() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 55.0, "2035-05-01T16:00:00Z"));

        RecojoResponse vista = recojoService.obtenerRespuesta(r.getId());

        assertThat(vista.id()).isEqualTo(r.getId());
        assertThat(vista.litros()).isEqualTo(55.0);
    }

    @Test
    void obtenerUnaRecepcionInexistenteFalla() {
        assertThatThrownBy(() -> recojoService.obtener(999_999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void elFiltroPorProveedorSoloDevuelveLasSuyas() {
        recojoService.registrar(peticion(proveedorA, 41.0, "2035-05-01T17:00:00Z"));

        var pagina = recojoService.buscar(
                proveedorA.getId(), null, null, null, null, null, PageRequest.of(0, 100));

        assertThat(pagina.content()).isNotEmpty();
        assertThat(pagina.content()).allSatisfy(r -> assertThat(r.productorId()).isEqualTo(proveedorA.getId()));
    }

    @Test
    void elFiltroPorEstadoSeparaLasCanceladas() {
        Recojo r = recojoService.registrar(peticion(proveedorA, 42.0, "2035-05-01T17:30:00Z"));
        recojoService.cancelar(r.getId(), new RecojoCancelRequest("Prueba"));

        var canceladas = recojoService.buscar(
                null, null, null, EstadoRecojo.CANCELLED, null, null, PageRequest.of(0, 200));

        assertThat(canceladas.content()).isNotEmpty();
        assertThat(canceladas.content())
                .allSatisfy(x -> assertThat(x.estado()).isEqualTo(EstadoRecojo.CANCELLED));
    }

    /** El rango de fechas es el que alimenta el cierre de jornada y la liquidacion semanal. */
    @Test
    void elFiltroPorRangoDeFechasExcluyeLoQueQuedaFuera() {
        recojoService.registrar(peticion(proveedorA, 43.0, "2035-05-01T18:00:00Z"));

        var dentro = recojoService.buscar(
                proveedorA.getId(), null, null, null,
                Instant.parse("2035-05-01T00:00:00Z"), Instant.parse("2035-05-02T00:00:00Z"),
                PageRequest.of(0, 100));
        var fuera = recojoService.buscar(
                proveedorA.getId(), null, null, null,
                Instant.parse("2035-05-02T00:00:00Z"), Instant.parse("2035-05-03T00:00:00Z"),
                PageRequest.of(0, 100));

        assertThat(dentro.content()).isNotEmpty();
        assertThat(fuera.content()).noneSatisfy(r -> assertThat(r.litros()).isEqualTo(43.0));
    }

    @Test
    void sinFiltrosDevuelveTodoPaginado() {
        var pagina = recojoService.buscar(null, null, null, null, null, null, PageRequest.of(0, 5));

        assertThat(pagina.content()).hasSizeLessThanOrEqualTo(5);
        assertThat(pagina.totalElements()).isPositive();
    }

    // Filtros de la pantalla de acopio: se aplican en la consulta para que la paginación cuente
    // solo lo filtrado (antes se filtraba en el navegador sobre una sola página).

    private static final Instant DIA_FILTROS = Instant.parse("2036-02-10T00:00:00Z");
    private static final Instant FIN_DIA_FILTROS = Instant.parse("2036-02-11T00:00:00Z");

    private PageResponse<RecojoResponse> buscarEnDiaDePrueba(String texto, FuenteRecojo fuente, String resultado) {
        return recojoService.buscar(
                null, null, null, null, DIA_FILTROS, FIN_DIA_FILTROS, texto, fuente, resultado, PageRequest.of(0, 100));
    }

    @Test
    void elFiltroDeTextoBuscaPorNombreDelProveedorSinDistinguirMayusculas() {
        recojoService.registrar(peticion(proveedorA, 51.0, "2036-02-10T12:00:00Z"));
        recojoService.registrar(peticion(proveedorB, 52.0, "2036-02-10T12:05:00Z"));

        var pagina = buscarEnDiaDePrueba(
                (proveedorA.getNombres() + " " + proveedorA.getApellidos()).toUpperCase(), null, null);

        assertThat(pagina.content()).isNotEmpty();
        assertThat(pagina.content()).allSatisfy(r -> assertThat(r.productorId()).isEqualTo(proveedorA.getId()));
        assertThat(pagina.totalElements()).isEqualTo(pagina.content().size());
    }

    @Test
    void elFiltroPorFuenteSoloDevuelveEsaFuente() {
        recojoService.registrar(peticion(proveedorA, 53.0, "2036-02-10T13:00:00Z"));

        var ruta = buscarEnDiaDePrueba(null, FuenteRecojo.RUTA, null);
        var otras = buscarEnDiaDePrueba(null, FuenteRecojo.PLANTA, null);

        assertThat(ruta.content()).allSatisfy(r -> assertThat(r.fuente()).isEqualTo(FuenteRecojo.RUTA));
        assertThat(otras.content()).noneSatisfy(r -> assertThat(r.litros()).isEqualTo(53.0));
    }

    @Test
    void elFiltroPendienteDevuelveLasRecepcionesSinControlDeCalidad() {
        recojoService.registrar(peticion(proveedorA, 54.0, "2036-02-10T14:00:00Z"));

        var pendientes = buscarEnDiaDePrueba(null, null, "PENDIENTE");
        var aceptadas = buscarEnDiaDePrueba(null, null, "ACEPTADA");

        assertThat(pendientes.content()).anySatisfy(r -> assertThat(r.litros()).isEqualTo(54.0));
        assertThat(pendientes.content()).allSatisfy(r -> assertThat(r.resultadoCalidad()).isNull());
        assertThat(aceptadas.content()).noneSatisfy(r -> assertThat(r.litros()).isEqualTo(54.0));
    }

    @Test
    void unResultadoDeCalidadInvalidoSeRechazaConMensajeClaro() {
        assertThatThrownBy(() -> buscarEnDiaDePrueba(null, null, "REGULAR"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no válido");
    }

    @Test
    void elFiltroPorRutaYPorAcopiadorNoRompenCuandoNoHayCoincidencias() {
        assertThat(recojoService
                        .buscar(null, 999_999L, null, null, null, null, PageRequest.of(0, 10))
                        .content())
                .isEmpty();
        assertThat(recojoService
                        .buscar(null, null, 999_999L, null, null, null, PageRequest.of(0, 10))
                        .content())
                .isEmpty();
    }

    @Test
    void reporteAdministrativoAgrupaLitrosYVisitasPorAcopiador() {
        var acopiador = acopiadorRepository.findByActivoTrueOrderByNombresAsc().get(0);
        Long rutaId = acopiador.getRuta() != null ? acopiador.getRuta().getId() : null;
        recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), proveedorA.getId(), acopiador.getId(), rutaId,
                Instant.parse("2035-03-10T15:00:00Z"), 75.0, null, FuenteRecojo.RUTA, null, null,
                null, null, EstadoVisita.ACOPIADO, null, null, null, null));
        recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), proveedorA.getId(), acopiador.getId(), rutaId,
                Instant.parse("2035-03-10T16:00:00Z"), 0.0, null, FuenteRecojo.RUTA, null, null,
                null, null, EstadoVisita.NO_ENTREGADO, "No tuvo leche", null, null, null));

        var reporte = recojoService
                .reporteAcopiadores(LocalDate.parse("2035-03-10"), LocalDate.parse("2035-03-10"), acopiador.getId())
                .get(0);

        assertThat(reporte.litros()).isEqualTo(75.0);
        assertThat(reporte.recepciones()).isEqualTo(2);
        assertThat(reporte.proveedoresVisitados()).isEqualTo(1);
        assertThat(reporte.noEntregados()).isEqualTo(1);
        assertThat(reporte.dias()).hasSize(1);
    }
}
