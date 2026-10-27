package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.liquidaciones.TarifaLeche;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CalidadService} de punta a punta, con la regla 7.1 ya cableada.
 *
 * <p>{@link DecisionRecepcionServiceTest} prueba la regla como logica pura; esto prueba que el
 * service la USA: que el resultado guardado es el que decide el sistema y no el que llego en la
 * peticion, y que la banda de densidad y las tarifas se leen de donde se configuran.
 */
@SpringBootTest
@Transactional
class CalidadServiceTests {

    @Autowired
    private CalidadService calidadService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ParametroCalidadRepository parametroCalidadRepository;

    @Autowired
    private TarifaLecheRepository tarifaLecheRepository;

    @Autowired
    private ControlCalidadRepository controlCalidadRepository;

    private Productor proveedor;

    @BeforeEach
    void escenario() {
        proveedor = productorRepository.findAll().get(0);

        // Banda y tarifas explicitas: las pruebas no deben depender de lo que sembro el DEMO.
        ParametroCalidad densidad = parametroCalidadRepository
                .findByNombreIgnoreCase("Densidad")
                .orElseGet(ParametroCalidad::new);
        densidad.setNombre("Densidad");
        densidad.setValorMin(1.0296);
        densidad.setValorMax(1.0340);
        densidad.setUnidad("g/mL a 15C");
        parametroCalidadRepository.save(densidad);

        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setVigenteDesde(LocalDate.now().minusDays(1));
        tarifa.setPrecioNormal(1.70);
        tarifa.setPrecioObservada(1.30);
        tarifa.setPrecioExcelencia(2.00);
        tarifaLecheRepository.save(tarifa);
    }

    private Recojo recepcion() {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(Instant.parse("2026-09-14T12:00:00Z"));
        r.setLitros(120.0);
        return recojoRepository.save(r);
    }

    private ControlCalidadRequest muestra(
            Long recojoId, Double densidad, AguaAnadida agua, ResultadoLugol lugol, ResultadoCalidad declarada) {
        return new ControlCalidadRequest(
                recojoId, null, null, null, densidad, null, null, 6.4, null, null, null, null, declarada, "Lactodensimetro",
                "Analista", agua, lugol, Olor.NORMAL, Aspecto.NORMAL, null);
    }

    // --------------------------------------------------------------- La decision es del sistema

    /**
     * El caso que motiva toda la regla 7.1: alguien declara APTA una muestra con agua confirmada.
     * Antes se guardaba tal cual; ahora el sistema la rechaza.
     */
    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void declararAptaUnaMuestraConAguaConfirmadaNoLaVuelveApta() {
        Recojo r = recepcion();

        ControlCalidad control = calidadService.registrarControl(
                muestra(r.getId(), 1.0310, AguaAnadida.CONFIRMADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThat(control.getResultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(control.getMotivoRechazo()).contains("agua");
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void unaMuestraLimpiaDentroDeLaBandaQuedaAceptada() {
        Recojo r = recepcion();

        ControlCalidad control = calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThat(control.getResultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
        assertThat(control.getMotivoRechazo()).isNull();
    }

    /** La banda se lee de parametros_calidad: si la planta la mueve, la decision cambia sola. */
    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void moverLaBandaDeDensidadCambiaLaDecisionSinTocarCodigo() {
        Recojo dentro = recepcion();
        assertThat(calidadService
                        .registrarControl(muestra(
                                dentro.getId(), 1.0300, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA,
                                ResultadoCalidad.ACEPTADA))
                        .getResultado())
                .isEqualTo(ResultadoCalidad.ACEPTADA);

        ParametroCalidad densidad = parametroCalidadRepository.findByNombreIgnoreCase("Densidad").orElseThrow();
        densidad.setValorMin(1.0320);
        parametroCalidadRepository.save(densidad);

        Recojo fuera = recepcion();
        assertThat(calidadService
                        .registrarControl(muestra(
                                fuera.getId(), 1.0300, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA,
                                ResultadoCalidad.ACEPTADA))
                        .getResultado())
                .isEqualTo(ResultadoCalidad.OBSERVADA);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void unaRecepcionSinDensidadNoSePenaliza() {
        // Las recepciones de ruta llegan sin densidad y se miden despues en planta.
        Recojo r = recepcion();

        ControlCalidad control = calidadService.registrarControl(muestra(
                r.getId(), null, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThat(control.getResultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void elMotivoEscritoPorElAnalistaNoSePisa() {
        Recojo r = recepcion();
        ControlCalidadRequest req = new ControlCalidadRequest(
                r.getId(), null, null, null, 1.0310, null, null, 6.4, null, null, null, null, ResultadoCalidad.ACEPTADA,
                "Lactodensimetro", "Analista", AguaAnadida.CONFIRMADA, ResultadoLugol.NEGATIVA, Olor.NORMAL,
                Aspecto.NORMAL, "Porongo con residuo de detergente");

        ControlCalidad control = calidadService.registrarControl(req);

        assertThat(control.getResultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(control.getMotivoRechazo()).isEqualTo("Porongo con residuo de detergente");
    }

    // ------------------------------------------------------------------------------ Controles

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void unaRecepcionNoAdmiteDosControles() {
        Recojo r = recepcion();
        calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThatThrownBy(() -> calidadService.registrarControl(muestra(
                        r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA,
                        ResultadoCalidad.ACEPTADA)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tiene un control");
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void registrarUnControlSobreUnaRecepcionInexistenteFalla() {
        assertThatThrownBy(() -> calidadService.registrarControl(muestra(
                        999_999L, 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA,
                        ResultadoCalidad.ACEPTADA)))
                .isInstanceOf(NotFoundException.class);
    }

    /** Corregir un control vuelve a pasar por la regla: no se puede "arreglar" un rechazo editando. */
    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void actualizarUnControlVuelveAAplicarLaRegla() {
        Recojo r = recepcion();
        ControlCalidad control = calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.CONFIRMADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));
        assertThat(control.getResultado()).isEqualTo(ResultadoCalidad.RECHAZADA);

        ControlCalidad corregido = calidadService.actualizarControl(
                control.getId(),
                muestra(r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA,
                        ResultadoCalidad.ACEPTADA));

        assertThat(corregido.getResultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void elHistorialDevuelveLosControlesDelProveedor() {
        int previos = calidadService.historialPorProductor(proveedor.getId()).size();
        Recojo r = recepcion();
        calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThat(calidadService.historialPorProductor(proveedor.getId())).hasSize(previos + 1);
    }

    // ----------------------------------------------------------------------------- Parametros

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void guardarUnParametroNuevoLoCreaYEditarloLoActualiza() {
        ParametroCalidad creado = calidadService.guardarParametro(
                null, new ParametroCalidadRequest("Acidez", 0.13, 0.17, "%", "Rango operativo"));
        assertThat(creado.getId()).isNotNull();

        ParametroCalidad editado = calidadService.guardarParametro(
                creado.getId(), new ParametroCalidadRequest("Acidez", 0.14, 0.18, "%", "Ajustado"));

        assertThat(editado.getId()).isEqualTo(creado.getId());
        assertThat(editado.getValorMin()).isEqualTo(0.14);
        assertThat(calidadService.listarParametros()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void editarUnParametroInexistenteFalla() {
        assertThatThrownBy(() -> calidadService.guardarParametro(
                        999_999L, new ParametroCalidadRequest("X", 0.0, 1.0, "u", null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------------------------------------------------------------------------- Incidencias

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaIncidenciaSeRegistraYSeSigueHastaCerrarla() {
        Recojo r = recepcion();

        IncidenciaCalidad incidencia = calidadService.registrarIncidencia(new IncidenciaCalidadRequest(
                proveedor.getId(), r.getId(), "ADULTERACION", "Lugol positivo", null));

        assertThat(incidencia.getId()).isNotNull();
        assertThat(calidadService.seguimientoPorProductor(proveedor.getId())).isNotEmpty();
        assertThat(calidadService.listarIncidencias()).isNotEmpty();

        IncidenciaCalidad cerrada = calidadService.cambiarEstadoIncidencia(
                incidencia.getId(), new CambiarEstadoIncidenciaRequest(EstadoIncidencia.CERRADA));
        assertThat(cerrada.getEstado()).isEqualTo(EstadoIncidencia.CERRADA);
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaIncidenciaSinRecojoEsValida() {
        // No toda incidencia nace de una recepcion: puede ser una queja o una observacion de ruta.
        IncidenciaCalidad incidencia = calidadService.registrarIncidencia(
                new IncidenciaCalidadRequest(proveedor.getId(), null, "OBSERVACION", "Porongo sucio", null));

        assertThat(incidencia.getRecojo()).isNull();
    }

    @Test
    @WithMockUser(username = "secretaria.demo@huata.pe")
    void unaIncidenciaDeUnProveedorInexistenteFalla() {
        assertThatThrownBy(() -> calidadService.registrarIncidencia(
                        new IncidenciaCalidadRequest(999_999L, null, "X", null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------------------ Evidencia

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void laEvidenciaSeAsociaAlControlYALaIncidencia() {
        Recojo r = recepcion();
        ControlCalidad control = calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));
        IncidenciaCalidad incidencia = calidadService.registrarIncidencia(
                new IncidenciaCalidadRequest(proveedor.getId(), r.getId(), "ADULTERACION", null, null));

        calidadService.registrarEvidencia(
                new EvidenciaCalidadRequest(control.getId(), null, TipoEvidencia.FOTO, "https://x/1.jpg"));
        calidadService.registrarEvidencia(
                new EvidenciaCalidadRequest(null, incidencia.getId(), TipoEvidencia.FOTO, "https://x/2.jpg"));

        assertThat(calidadService.evidenciaDeControl(control.getId())).hasSize(1);
        assertThat(calidadService.evidenciaDeIncidencia(incidencia.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void laEvidenciaDeUnControlInexistenteFalla() {
        assertThatThrownBy(() -> calidadService.registrarEvidencia(
                        new EvidenciaCalidadRequest(999_999L, null, TipoEvidencia.FOTO, "https://x/1.jpg")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @WithMockUser(username = "calidad.demo@huata.pe")
    void elControlQuedaEnLaBaseYNoSoloEnMemoria() {
        Recojo r = recepcion();
        ControlCalidad control = calidadService.registrarControl(muestra(
                r.getId(), 1.0310, AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA));

        assertThat(controlCalidadRepository.findByRecojoId(r.getId()))
                .get()
                .satisfies(c -> assertThat(c.getId()).isEqualTo(control.getId()));
    }
}
