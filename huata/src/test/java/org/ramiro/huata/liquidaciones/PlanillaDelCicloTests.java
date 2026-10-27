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
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planilla del ciclo: lo que ve Secretaria antes de pagar.
 *
 * <p>Lo importante de estas pruebas es que el importe pendiente que se muestra salga de la MISMA
 * funcion de tarifa que la liquidacion real. Si la pantalla dijera un numero y el pago ejecutara
 * otro, el error se descubriria con el proveedor delante.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class PlanillaDelCicloTests {

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
        proveedor.setCodigo("PLA-" + UUID.randomUUID().toString().substring(0, 6));
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

    private void entrega(double litros, ResultadoCalidad resultado, int dia, EstadoRecojo estado) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(Instant.parse("2026-09-0" + dia + "T12:00:00Z"));
        r.setLitros(litros);
        if (estado != null) {
            r.setEstado(estado);
        }
        r = recojoRepository.save(r);
        if (resultado != null) {
            ControlCalidad c = new ControlCalidad();
            c.setRecojo(r);
            c.setResultado(resultado);
            controlCalidadRepository.save(c);
        }
    }

    private LiquidacionService.FilaPlanilla miFila() {
        return liquidacionService.planillaDelCiclo(INICIO, FIN).stream()
                .filter(f -> f.proveedorCodigo().equals(proveedor.getCodigo()))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void laPlanillaSuelaLoPendienteConLaMismaTarifaQueElPago() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);

        LiquidacionService.FilaPlanilla fila = miFila();
        double liquidado = liquidacionService.calcular(proveedor.getId(), INICIO, FIN).getMontoTotal();

        assertThat(fila.importePendiente()).isEqualTo(170.0);
        assertThat(fila.importePendiente()).isEqualTo(liquidado);
    }

    /** Los litros rechazados se muestran, pero no se pagan ni cuentan como pendientes. */
    @Test
    void losLitrosRechazadosSeVenPeroNoSeCobran() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        entrega(40, ResultadoCalidad.RECHAZADA, 5, null);

        LiquidacionService.FilaPlanilla fila = miFila();

        assertThat(fila.litrosEntregados()).isEqualTo(140.0);
        assertThat(fila.litrosRechazados()).isEqualTo(40.0);
        assertThat(fila.litrosPendientes()).isEqualTo(100.0);
        assertThat(fila.importePendiente()).isEqualTo(170.0);
    }

    @Test
    void unaRecepcionCanceladaNoEntraEnLaPlanilla() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        entrega(50, ResultadoCalidad.ACEPTADA, 5, EstadoRecojo.CANCELLED);

        assertThat(miFila().litrosEntregados()).isEqualTo(100.0);
    }

    /**
     * Una liquidacion calculada pero no cerrada NO descuenta: hasta que no se cierra, ese dinero no
     * se pago y el proveedor sigue teniendolo pendiente.
     */
    @Test
    void calcularSinCerrarNoDescuentaDeLoPendiente() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        liquidacionService.calcular(proveedor.getId(), INICIO, FIN);

        LiquidacionService.FilaPlanilla fila = miFila();

        assertThat(fila.liquidado()).isFalse();
        assertThat(fila.litrosPendientes()).isEqualTo(100.0);
    }

    @Test
    void alCerrarLaLiquidacionElSaldoPasaAPagado() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        Liquidacion l = liquidacionService.calcular(proveedor.getId(), INICIO, FIN);
        liquidacionService.cambiarEstado(l.getId(), new CambiarEstadoLiquidacionRequest(EstadoLiquidacion.REVIEWED));
        liquidacionService.cambiarEstado(l.getId(), new CambiarEstadoLiquidacionRequest(EstadoLiquidacion.CLOSED));

        LiquidacionService.FilaPlanilla fila = miFila();

        assertThat(fila.liquidado()).isTrue();
        assertThat(fila.litrosPendientes()).isZero();
        assertThat(fila.litrosPagados()).isEqualTo(100.0);
        assertThat(fila.importePendiente()).isZero();
    }

    /**
     * Regresion: un recojo registrado DESPUES de cerrar la liquidacion del ciclo no debe desaparecer
     * como si ya estuviera pagado. Antes de este fix, en cuanto existia una liquidacion CLOSED para
     * el periodo, filaDe() volvia a sumar TODOS los recojos del periodo en caliente y los reportaba
     * como litrosPagados/importePendiente=0, sin importar si de verdad estaban dentro de esa
     * liquidacion: el litro nuevo se pagaba solo, en la pantalla, sin que nadie lo hubiera cobrado.
     */
    @Test
    void unRecojoRegistradoDespuesDelCierreSigueApareciendoComoPendiente() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        Liquidacion l = liquidacionService.calcular(proveedor.getId(), INICIO, FIN);
        liquidacionService.cambiarEstado(l.getId(), new CambiarEstadoLiquidacionRequest(EstadoLiquidacion.CLOSED));

        // Acopio tardio, registrado despues del cierre, para el mismo ciclo ya pagado.
        entrega(20, ResultadoCalidad.ACEPTADA, 5, null);

        LiquidacionService.FilaPlanilla fila = miFila();

        assertThat(fila.liquidado()).isFalse();
        assertThat(fila.tieneLiquidacionCerrada()).isTrue();
        assertThat(fila.liquidacionId()).isEqualTo(l.getId());
        assertThat(fila.litrosPagados()).isEqualTo(100.0);
        assertThat(fila.litrosPendientes()).isEqualTo(20.0);
        assertThat(fila.importePendiente()).isEqualTo(34.0);
    }

    @Test
    void unProveedorSinEntregasEnElCicloNoApareceEnLaPlanilla() {
        assertThat(liquidacionService.planillaDelCiclo(INICIO, FIN))
                .noneSatisfy(f -> assertThat(f.proveedorCodigo()).isEqualTo(proveedor.getCodigo()));
    }

    @Test
    void laTarifaAplicadaEsElPromedioRealDelCicloCuandoHayCalidadesMezcladas() {
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);
        entrega(100, ResultadoCalidad.OBSERVADA, 5, null);

        LiquidacionService.FilaPlanilla fila = miFila();

        // (170 + 130) / 200 litros pagables = 1.50
        assertThat(fila.tarifaAplicada()).isEqualTo(1.50);
    }

    @Test
    void laMarcaDeExcelenciaSeVeEnLaPlanilla() {
        proveedor.setExcelencia(true);
        productorRepository.save(proveedor);
        entrega(100, ResultadoCalidad.ACEPTADA, 4, null);

        LiquidacionService.FilaPlanilla fila = miFila();

        assertThat(fila.excelencia()).isTrue();
        assertThat(fila.importePendiente()).isEqualTo(200.0);
    }
}
