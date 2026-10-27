package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Como se convierte la calidad en dinero al liquidar.
 *
 * <p>Existe por un fallo concreto: el calculo era {@code aceptada ? precioNormal : precioObservada},
 * de modo que una recepcion RECHAZADA —leche devuelta al proveedor por adulteracion— se le liquidaba
 * a tarifa reducida en lugar de no pagarse. La regla 7.1 dice {@code accepted = false, price = 0}.
 *
 * <p>Aqui NO se vuelve a decidir la calidad: eso lo hizo DecisionRecepcionService en la recepcion.
 * Se comprueba que la liquidacion respeta esa decision, que es lo que evita dos criterios en el
 * sistema.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class TarifadoDeLiquidacionTests {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 3);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 9);

    @Autowired
    private LiquidacionService liquidacionService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ControlCalidadRepository controlCalidadRepository;

    @Autowired
    private TarifaLecheRepository tarifaLecheRepository;

    private Productor proveedor;

    @BeforeEach
    void escenario() {
        proveedor = new Productor();
        proveedor.setCodigo("TAR-" + UUID.randomUUID().toString().substring(0, 6));
        proveedor.setNombres("Ana");
        proveedor.setApellidos("Quispe");
        proveedor.setEstado(EstadoProductor.ACTIVO);
        proveedor = productorRepository.save(proveedor);

        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setVigenteDesde(LocalDate.now().minusMonths(1));
        tarifa.setPrecioNormal(1.70);
        tarifa.setPrecioObservada(1.30);
        tarifa.setPrecioExcelencia(2.00);
        tarifaLecheRepository.save(tarifa);
    }

    private void entrega(double litros, ResultadoCalidad resultado, int dia) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(Instant.parse("2026-09-0" + dia + "T12:00:00Z"));
        r.setLitros(litros);
        r = recojoRepository.save(r);
        if (resultado != null) {
            ControlCalidad c = new ControlCalidad();
            c.setRecojo(r);
            c.setResultado(resultado);
            controlCalidadRepository.save(c);
        }
    }

    private Liquidacion liquidar() {
        return liquidacionService.calcular(proveedor.getId(), INICIO, FIN);
    }

    /** El fallo que motiva esta clase. */
    @Test
    void unaRecepcionRechazadaNoSePaga() {
        entrega(100, ResultadoCalidad.RECHAZADA, 4);

        Liquidacion l = liquidar();

        assertThat(l.getLitrosTotales()).isEqualTo(100.0);
        assertThat(l.getMontoTotal()).isZero();
    }

    @Test
    void unaObservadaSePagaATarifaReducida() {
        entrega(100, ResultadoCalidad.OBSERVADA, 4);

        assertThat(liquidar().getMontoTotal()).isEqualTo(130.0);
    }

    @Test
    void unaAceptadaSePagaATarifaNormal() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4);

        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0);
    }

    /** Sin control registrado se paga: es leche que entro en planta y nadie objeto. */
    @Test
    void unaRecepcionSinControlSePagaComoAceptada() {
        entrega(100, null, 4);

        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0);
    }

    @Test
    void seMezclanLasTresCalidadesEnElMismoCiclo() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4);
        entrega(100, ResultadoCalidad.OBSERVADA, 5);
        entrega(100, ResultadoCalidad.RECHAZADA, 6);

        Liquidacion l = liquidar();

        assertThat(l.getLitrosTotales()).isEqualTo(300.0);
        assertThat(l.getMontoTotal()).isEqualTo(170.0 + 130.0 + 0.0);
    }

    // ------------------------------------------------------------------------ 7.4 Excelencia

    @Test
    void unProveedorExcelenciaConElCicloLimpioCobraSuTarifa() {
        proveedor.setExcelencia(true);
        productorRepository.save(proveedor);
        entrega(100, ResultadoCalidad.ACEPTADA, 4);
        entrega(100, ResultadoCalidad.ACEPTADA, 5);

        assertThat(liquidar().getMontoTotal()).isEqualTo(400.0);
    }

    /**
     * 7.4 al pie de la letra: "si el ciclo tiene una sola incidencia, la tarifa preferencial se
     * pierde para toda la semana". No solo para la entrega con incidencia.
     */
    @Test
    void unaSolaIncidenciaLeQuitaLaTarifaPreferencialATodaLaSemana() {
        proveedor.setExcelencia(true);
        productorRepository.save(proveedor);
        entrega(100, ResultadoCalidad.ACEPTADA, 4);
        entrega(100, ResultadoCalidad.OBSERVADA, 5);

        // Las aceptadas bajan de 2.00 a 1.70 y la observada se paga a 1.30.
        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0 + 130.0);
    }

    @Test
    void unRechazoTambienLeQuitaLaTarifaPreferencial() {
        proveedor.setExcelencia(true);
        productorRepository.save(proveedor);
        entrega(100, ResultadoCalidad.ACEPTADA, 4);
        entrega(100, ResultadoCalidad.RECHAZADA, 5);

        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0);
    }

    /** Una tarifa anterior a 7.4 no tiene precio de Excelencia: se cobra el normal, no se rompe. */
    @Test
    void sinPrecioDeExcelenciaVigenteSeCobraElNormal() {
        tarifaLecheRepository.deleteAll();
        TarifaLeche antigua = new TarifaLeche();
        antigua.setVigenteDesde(LocalDate.now().minusMonths(1));
        antigua.setPrecioNormal(1.70);
        antigua.setPrecioObservada(1.30);
        tarifaLecheRepository.save(antigua);

        proveedor.setExcelencia(true);
        productorRepository.save(proveedor);
        entrega(100, ResultadoCalidad.ACEPTADA, 4);

        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0);
    }

    @Test
    void unProveedorSinLaMarcaNuncaCobraLaTarifaDeExcelencia() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4);

        assertThat(liquidar().getMontoTotal()).isEqualTo(170.0);
    }
}
