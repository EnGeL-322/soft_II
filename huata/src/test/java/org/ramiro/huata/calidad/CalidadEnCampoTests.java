package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.FuenteRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calidad medida en campo por el testeador ANTES de que pase el acopiador (V47): el control existe
 * sin recojo y se vincula cuando el recojo llega.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "calidad.demo@huata.pe")
class CalidadEnCampoTests {

    // Fecha lejana a proposito: ningun dato DEMO cae en ella.
    private static final Instant MANANA_EN_CAMPO = Instant.parse("2036-03-02T11:00:00Z");

    @Autowired
    private CalidadService calidadService;

    @Autowired
    private ControlCalidadRepository controles;

    @Autowired
    private ProductorRepository productores;

    @Autowired
    private RecojoRepository recojos;

    @Autowired
    private ParametroCalidadRepository parametros;

    private Productor proveedor;

    @BeforeEach
    void escenario() {
        proveedor = productores.findAll().get(0);
        // Estandar explicito: la prueba no depende de lo que sembro el DEMO.
        ParametroCalidad grasa = parametros.findByNombreIgnoreCase("Grasa").orElseGet(ParametroCalidad::new);
        grasa.setNombre("Grasa");
        grasa.setValorMin(3.2);
        grasa.setValorMax(null);
        grasa.setActivo(true);
        parametros.save(grasa);
    }

    private ControlCalidadRequest analisisDeCampo(Long productorId, Instant cuando, String responsable) {
        return analisisDeCampo(productorId, cuando, responsable, 3.4);
    }

    private ControlCalidadRequest analisisDeCampo(Long productorId, Instant cuando, String responsable, double grasa) {
        return new ControlCalidadRequest(
                null, productorId, LugarAnalisis.CAMPO, cuando, 1.0310, null, null, 6.0, grasa, null, null,
                "Analisis en campo", ResultadoCalidad.ACEPTADA, "Lactodensimetro", responsable,
                AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, Olor.NORMAL, Aspecto.NORMAL, null);
    }

    private Recojo recojo(FuenteRecojo fuente, Instant cuando) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(cuando);
        r.setLitros(80.0);
        r.setFuente(fuente);
        return recojos.save(r);
    }

    @Test
    void elTesteadorRegistraElAnalisisSinQueExistaElRecojo() {
        ControlCalidad c = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO, "Testeador QA"));

        assertThat(c.getRecojo()).isNull();
        assertThat(c.getProductor().getId()).isEqualTo(proveedor.getId());
        assertThat(c.getLugarAnalisis()).isEqualTo(LugarAnalisis.CAMPO);
        assertThat(c.getFechaHora()).isEqualTo(MANANA_EN_CAMPO);
        // La decision la sigue tomando el sistema, con o sin recojo.
        assertThat(c.getResultado()).isNotNull();
        assertThat(calidadService.historialPorProductor(proveedor.getId())).extracting(ControlCalidad::getId)
                .contains(c.getId());
    }

    @Test
    void sinRecojoNiProveedorNoSeRegistraNada() {
        assertThatThrownBy(() -> calidadService.registrarControl(analisisDeCampo(null, MANANA_EN_CAMPO, "QA")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unSegundoAnalisisDelMismoDiaActualizaElPrimeroEnVezDeDuplicarlo() {
        ControlCalidad primero = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO, "Testeador QA"));
        ControlCalidad segundo = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO.plusSeconds(1800), "Testeador QA 2"));

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getResponsable()).isEqualTo("Testeador QA 2");
    }

    @Test
    void elRecojoEnRutaDelMismoDiaSeLlevaElAnalisisDeCampo() {
        ControlCalidad campo = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO, "Testeador QA"));
        Recojo r = recojo(FuenteRecojo.RUTA, MANANA_EN_CAMPO.plusSeconds(3600));

        assertThat(calidadService.vincularControlDeCampo(r)).isPresent();
        assertThat(controles.findById(campo.getId()).orElseThrow().getRecojo().getId()).isEqualTo(r.getId());
        assertThat(calidadService.controlesSinRecojo(60)).extracting(ControlCalidad::getId)
                .doesNotContain(campo.getId());
    }

    @Test
    void unAnalisisDeOtroDiaNoSeVinculaAlRecojo() {
        calidadService.registrarControl(analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO, "Testeador QA"));
        Recojo delDiaSiguiente = recojo(FuenteRecojo.RUTA, MANANA_EN_CAMPO.plusSeconds(86_400));

        assertThat(calidadService.vincularControlDeCampo(delDiaSiguiente)).isEmpty();
    }

    @Test
    void siCumpleElEstandarSeApruebaSolo() {
        ControlCalidad c = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), Instant.now(), "Testeador QA"));

        assertThat(c.getAprobadoPor()).isEqualTo(CalidadService.APROBADO_POR_SISTEMA);
        assertThat(c.getAprobadoEn()).isNotNull();
        assertThat(c.getAdvertencia()).isNull();
        assertThat(calidadService.porAprobar(1)).extracting(ControlCalidad::getId).doesNotContain(c.getId());
    }

    @Test
    void siNoCumpleQuedaPendienteConAdvertenciaYSeApruebaAMano() {
        ControlCalidad c = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), Instant.now(), "Testeador QA", 3.0));
        assertThat(c.getAprobadoEn()).isNull();
        assertThat(c.getAdvertencia()).contains("Grasa 3 bajo el mínimo 3.2");
        assertThat(calidadService.porAprobar(1)).extracting(ControlCalidad::getId).contains(c.getId());

        ControlCalidad aprobado = calidadService.aprobar(c.getId(), "calidad.demo@huata.pe");

        assertThat(aprobado.getAprobadoPor()).isEqualTo("calidad.demo@huata.pe");
        assertThat(aprobado.getAprobadoEn()).isNotNull();
        assertThat(calidadService.porAprobar(1)).extracting(ControlCalidad::getId).doesNotContain(c.getId());
        // Aprobar otra vez no cambia quien aprobo.
        assertThat(calidadService.aprobar(c.getId(), "otro@huata.pe").getAprobadoPor()).isEqualTo("calidad.demo@huata.pe");
    }

    @Test
    void recientesIncluyeLosAprobadosSolosYLosPendientes() {
        ControlCalidad bueno = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), Instant.now(), "Testeador QA", 3.6));
        assertThat(bueno.getAprobadoEn()).isNotNull();
        assertThat(calidadService.recientes(1)).extracting(ControlCalidad::getId).contains(bueno.getId());
        assertThat(calidadService.porAprobar(1)).extracting(ControlCalidad::getId).doesNotContain(bueno.getId());
    }

    @Test
    void unAnalisisCorregidoSeVuelveAEvaluarContraElEstandar() {
        ControlCalidad c = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO, "Testeador QA", 3.0));
        calidadService.aprobar(c.getId(), "calidad.demo@huata.pe");

        // Corregido y sigue sin cumplir: la aprobacion manual anterior ya no vale.
        ControlCalidad sigueMal = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO.plusSeconds(600), "Testeador QA", 3.1));
        assertThat(sigueMal.getId()).isEqualTo(c.getId());
        assertThat(sigueMal.getAprobadoEn()).isNull();
        assertThat(sigueMal.getAdvertencia()).contains("Grasa");

        // Corregido y ahora cumple: se aprueba solo.
        ControlCalidad bien = calidadService.registrarControl(
                analisisDeCampo(proveedor.getId(), MANANA_EN_CAMPO.plusSeconds(900), "Testeador QA", 3.5));
        assertThat(bien.getAprobadoPor()).isEqualTo(CalidadService.APROBADO_POR_SISTEMA);
        assertThat(bien.getAdvertencia()).isNull();
    }

    @Test
    void elControlDeUnaEntregaDirectaSeMarcaEnPlanta() {
        Recojo directa = recojo(FuenteRecojo.PLANTA, MANANA_EN_CAMPO);
        ControlCalidad c = calidadService.registrarControl(new ControlCalidadRequest(
                directa.getId(), null, LugarAnalisis.CAMPO, null, 1.0310, null, null, 6.0, null, null, null,
                null, ResultadoCalidad.ACEPTADA, null, "Analista", null, null, null, null, null));

        // Aunque el cliente diga otra cosa: una entrega directa solo puede analizarse en planta.
        assertThat(c.getLugarAnalisis()).isEqualTo(LugarAnalisis.PLANTA);
        assertThat(c.getProductor().getId()).isEqualTo(proveedor.getId());
    }
}
