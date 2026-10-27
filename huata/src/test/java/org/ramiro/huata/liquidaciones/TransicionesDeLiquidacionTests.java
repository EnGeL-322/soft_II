package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La maquina de estados de la liquidacion aplicada sobre el service.
 *
 * <p>Lo que se protege aqui es concreto: una liquidacion CERRADA es dinero ya entregado al
 * proveedor. Mientras {@code cambiarEstado} fue un {@code setEstado} suelto, un PATCH bastaba para
 * devolverla a DRAFT y recalcular el periodo pagado con otra tarifa. Ademas el cierre no dejaba
 * rastro de quien lo hizo, que es justo lo que se le pregunta a la bitacora meses despues.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class TransicionesDeLiquidacionTests {

    private static final LocalDate INICIO = LocalDate.of(2026, 9, 3);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 9);

    @Autowired
    private LiquidacionService liquidacionService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private TarifaLecheRepository tarifaLecheRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private AsientoContableService asientoContableService;

    private Productor proveedor;

    @BeforeEach
    void escenario() {
        proveedor = new Productor();
        proveedor.setCodigo("TRA-" + UUID.randomUUID().toString().substring(0, 6));
        proveedor.setNombres("Ana");
        proveedor.setApellidos("Quispe");
        proveedor.setEstado(EstadoProductor.ACTIVO);
        proveedor = productorRepository.save(proveedor);

        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setVigenteDesde(LocalDate.now().minusMonths(1));
        tarifa.setPrecioNormal(1.70);
        tarifa.setPrecioObservada(1.30);
        tarifaLecheRepository.save(tarifa);

        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(Instant.parse("2026-09-04T12:00:00Z"));
        r.setLitros(100.0);
        recojoRepository.save(r);
    }

    private Liquidacion calculada() {
        return liquidacionService.calcular(proveedor.getId(), INICIO, FIN);
    }

    private Liquidacion mover(Liquidacion l, EstadoLiquidacion destino) {
        return liquidacionService.cambiarEstado(l.getId(), new CambiarEstadoLiquidacionRequest(destino));
    }

    @Test
    void elCalculoDejaLaLiquidacionCalculadaYEsaEsLaUnicaSalidaDeDraft() {
        assertThat(calculada().getEstado()).isEqualTo(EstadoLiquidacion.CALCULATED);
        assertThat(EstadoLiquidacion.DRAFT.destinosPosibles()).isEmpty();
    }

    @Test
    void elCaminoCompletoCalculadaRevisadaCerradaFunciona() {
        Liquidacion l = calculada();

        // Se comprueba cada estado ANTES de la siguiente transicion: dentro de un test
        // @Transactional todas las llamadas devuelven la MISMA instancia gestionada, asi que mirarla
        // al final mostraria el estado final y no el que tenia entonces.
        assertThat(mover(l, EstadoLiquidacion.REVIEWED).getEstado()).isEqualTo(EstadoLiquidacion.REVIEWED);
        assertThat(mover(l, EstadoLiquidacion.CLOSED).getEstado()).isEqualTo(EstadoLiquidacion.CLOSED);
    }

    /** Pago unico y Pagar a todos cobran sin pasar por revision: la revision es opcional. */
    @Test
    void sePuedeCerrarDirectamenteDesdeCalculadaSinPasarPorRevisada() {
        assertThat(mover(calculada(), EstadoLiquidacion.CLOSED).getEstado()).isEqualTo(EstadoLiquidacion.CLOSED);
    }

    @Test
    void unaLiquidacionCerradaNoVuelveAtrasPorNingunCamino() {
        Liquidacion cerrada = mover(calculada(), EstadoLiquidacion.CLOSED);

        for (EstadoLiquidacion destino : new EstadoLiquidacion[] {
            EstadoLiquidacion.DRAFT, EstadoLiquidacion.CALCULATED, EstadoLiquidacion.REVIEWED
        }) {
            assertThatThrownBy(() -> mover(cerrada, destino))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no se reabre");
        }
        assertThat(cerrada.getEstado()).isEqualTo(EstadoLiquidacion.CLOSED);
    }

    /**
     * El agujero completo, extremo a extremo: reabrir para recalcular un periodo ya pagado. Sin el
     * corte en cambiarEstado, el importe entregado al proveedor cambiaba con solo bajar la tarifa.
     */
    @Test
    void nadieRecalculaUnPeriodoYaPagadoReabriendoLaLiquidacion() {
        Liquidacion cerrada = mover(calculada(), EstadoLiquidacion.CLOSED);
        double pagado = cerrada.getMontoTotal();

        assertThatThrownBy(() -> mover(cerrada, EstadoLiquidacion.CALCULATED))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(this::calculada)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está cerrada");

        assertThat(cerrada.getMontoTotal()).isEqualTo(pagado);
    }

    @Test
    void volverAPedirElEstadoQueYaTieneSeRechazaEnVezDeGenerarUnRegistroVacio() {
        Liquidacion revisada = mover(calculada(), EstadoLiquidacion.REVIEWED);
        auditoriaRepository.deleteAll();

        assertThatThrownBy(() -> mover(revisada, EstadoLiquidacion.REVIEWED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está en REVIEWED");
        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    void elCierreQuedaEnLaBitacoraConAutorYEstadoDeOrigen() {
        Liquidacion l = calculada();
        auditoriaRepository.deleteAll();

        mover(l, EstadoLiquidacion.CLOSED);

        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActor()).isEqualTo("secretaria.demo@huata.pe");
                    assertThat(r.getAccion()).isEqualTo("Cierre de liquidación");
                    assertThat(r.getDetalle()).contains(proveedor.getCodigo()).contains("CALCULATED -> CLOSED");
                });
    }

    /** El cierre masivo pasa por la misma maquina: no hay una segunda puerta al estado CLOSED. */
    @Test
    void elCierreDelCicloDejaLasLiquidacionesCerradasYNoRepiteLasYaCerradas() {
        LiquidacionService.CierreCicloResultado primera = liquidacionService.cerrarCiclo(INICIO, FIN);
        assertThat(primera.liquidadas()).anySatisfy(l -> assertThat(l.getEstado())
                .isEqualTo(EstadoLiquidacion.CLOSED));

        LiquidacionService.CierreCicloResultado segunda = liquidacionService.cerrarCiclo(INICIO, FIN);
        assertThat(segunda.yaCerrados()).isPositive();
    }

    @Test
    void cerrarUnaLiquidacionGeneraElAsientoDePagoEnElLibroDiario() {
        Liquidacion cerrada = mover(calculada(), EstadoLiquidacion.CLOSED);
        LocalDate fechaPago = FIN.plusDays(2); // RN-05: viernes siguiente al cierre del miercoles

        var asientos = asientoContableService.libroDiario(fechaPago, fechaPago).stream()
                .filter(a -> a.origenTipo() == OrigenAsiento.LIQUIDACION_PAGO && cerrada.getId().equals(a.origenId()))
                .toList();

        assertThat(asientos).singleElement().satisfies(a -> {
            assertThat(a.totalDebe()).isEqualByComparingTo(java.math.BigDecimal.valueOf(cerrada.getMontoNeto()).setScale(2));
            assertThat(a.lineas()).anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("6021"));
            assertThat(a.lineas()).anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("1011"));
        });
    }

    @Test
    void elCierreDelCicloTambienGeneraLosAsientosDePago() {
        LiquidacionService.CierreCicloResultado resultado = liquidacionService.cerrarCiclo(INICIO, FIN);
        Liquidacion cerrada = resultado.liquidadas().stream()
                .filter(l -> l.getProductor().getId().equals(proveedor.getId()))
                .findFirst()
                .orElseThrow();
        LocalDate fechaPago = FIN.plusDays(2);

        assertThat(asientoContableService.libroDiario(fechaPago, fechaPago))
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.LIQUIDACION_PAGO);
                    assertThat(a.origenId()).isEqualTo(cerrada.getId());
                });
    }

    /**
     * reabrir() es la unica salida de CLOSED de todo el modulo (ver LiquidacionService.reabrir):
     * regresa a CALCULATED y anula el pago original en sentido contrario, sin borrar el asiento ya
     * registrado.
     */
    @Test
    void reabrirUnaLiquidacionCerradaLaRegresaACalculatedYAnulaElAsientoDePago() {
        Liquidacion cerrada = mover(calculada(), EstadoLiquidacion.CLOSED);
        double montoOriginal = cerrada.getMontoNeto();

        Liquidacion reabierta = liquidacionService.reabrir(cerrada.getId());

        assertThat(reabierta.getEstado()).isEqualTo(EstadoLiquidacion.CALCULATED);
        assertThat(asientoContableService.libroDiario(LocalDate.now(), LocalDate.now()))
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.LIQUIDACION_REAPERTURA);
                    assertThat(a.origenId()).isEqualTo(cerrada.getId());
                    assertThat(a.totalDebe()).isEqualByComparingTo(java.math.BigDecimal.valueOf(montoOriginal).setScale(2));
                    assertThat(a.lineas()).anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("1011"));
                    assertThat(a.lineas()).anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("6021"));
                });
        // El asiento del pago original sigue ahi: reabrir anula, no borra el libro diario.
        LocalDate fechaPagoOriginal = FIN.plusDays(2);
        assertThat(asientoContableService.libroDiario(fechaPagoOriginal, fechaPagoOriginal))
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.LIQUIDACION_PAGO);
                    assertThat(a.origenId()).isEqualTo(cerrada.getId());
                });
    }

    @Test
    void reabrirUnaLiquidacionQueNoEstaCerradaFalla() {
        Liquidacion calculada = calculada();

        assertThatThrownBy(() -> liquidacionService.reabrir(calculada.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void unaLiquidacionCerradaTampocoAdmiteAjustes() {
        Liquidacion cerrada = mover(calculada(), EstadoLiquidacion.CLOSED);

        assertThatThrownBy(() -> liquidacionService.agregarAjuste(
                        cerrada.getId(), new AjusteLiquidacionRequest(TipoAjuste.ADELANTO, 10.0, "Adelanto")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no admite ajustes");
    }
}
