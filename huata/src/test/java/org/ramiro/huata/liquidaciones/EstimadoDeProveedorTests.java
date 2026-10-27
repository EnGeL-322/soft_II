package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
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
 * {@link LiquidacionService#estimadoActual} y {@link LiquidacionService#cicloActual}: el "cobro
 * estimado de la semana" que ve el proveedor en su app (RF-80), sin crear ninguna
 * {@link Liquidacion}.
 *
 * <p>Las fechas se anclan a {@code cicloActual()} y no a una fecha fija: es la misma función que
 * usa el código de producción, así que si el criterio de corte de ciclo cambia algún día, esta
 * prueba se mueve con él en vez de quedar fijada a un ciclo que dejó de existir.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class EstimadoDeProveedorTests {

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
    private LocalDate inicioCiclo;

    @BeforeEach
    void escenario() {
        inicioCiclo = LiquidacionService.cicloActual()[0];

        proveedor = new Productor();
        proveedor.setCodigo("EST-" + UUID.randomUUID().toString().substring(0, 6));
        proveedor.setNombres("Rosa");
        proveedor.setApellidos("Mamani");
        proveedor.setEstado(EstadoProductor.ACTIVO);
        proveedor = productorRepository.save(proveedor);

        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setVigenteDesde(LocalDate.now().minusMonths(1));
        tarifa.setPrecioNormal(1.70);
        tarifa.setPrecioObservada(1.30);
        tarifa.setPrecioExcelencia(2.00);
        tarifaLecheRepository.save(tarifa);
    }

    /** Una entrega dentro del ciclo en curso, {@code diasDesdeElJueves} días después de que abrió. */
    private void entregaEnElCiclo(double litros, ResultadoCalidad resultado, int diasDesdeElJueves) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(inicioCiclo.plusDays(diasDesdeElJueves).atTime(12, 0).toInstant(ZoneOffset.UTC));
        r.setLitros(litros);
        r = recojoRepository.save(r);
        ControlCalidad c = new ControlCalidad();
        c.setRecojo(r);
        c.setResultado(resultado);
        controlCalidadRepository.save(c);
    }

    @Test
    void elCicloActualEmpiezaEnJuevesYDuraSieteDias() {
        LocalDate[] ciclo = LiquidacionService.cicloActual();

        assertThat(ciclo[0].getDayOfWeek()).isEqualTo(java.time.DayOfWeek.THURSDAY);
        assertThat(ciclo[1]).isEqualTo(ciclo[0].plusDays(6));
        assertThat(LocalDate.now()).isBetween(ciclo[0], ciclo[1]);
    }

    @Test
    void elEstimadoUsaLaMismaTarifaQueLaPlanillaDelCiclo() {
        entregaEnElCiclo(100, ResultadoCalidad.ACEPTADA, 0);

        LiquidacionService.FilaPlanilla estimado = liquidacionService.estimadoActual(proveedor.getId());

        assertThat(estimado.proveedorCodigo()).isEqualTo(proveedor.getCodigo());
        assertThat(estimado.litrosEntregados()).isEqualTo(100.0);
        assertThat(estimado.importePendiente()).isEqualTo(170.0);
        assertThat(estimado.liquidado()).isFalse();
    }

    @Test
    void unaEntregaFueraDelCicloActualNoCuentaEnElEstimado() {
        // 10 días antes del jueves de este ciclo cae en el ciclo anterior, no en el actual.
        entregaEnElCiclo(100, ResultadoCalidad.ACEPTADA, -10);

        LiquidacionService.FilaPlanilla estimado = liquidacionService.estimadoActual(proveedor.getId());

        assertThat(estimado.litrosEntregados()).isZero();
        assertThat(estimado.importePendiente()).isZero();
    }

    @Test
    void sinTarifaConfiguradaElEstimadoFalla() {
        tarifaLecheRepository.deleteAll();

        assertThatThrownBy(() -> liquidacionService.estimadoActual(proveedor.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void resumenPorPeriodoSeparaProduccionPagadaYPendiente() {
        entregaEnElCiclo(100, ResultadoCalidad.ACEPTADA, 0);
        entregaEnElCiclo(20, ResultadoCalidad.RECHAZADA, 1);

        var resumen = liquidacionService.resumenPagoProveedor(
                proveedor.getId(), inicioCiclo, inicioCiclo.plusDays(6));

        assertThat(resumen.litrosProducidos()).isEqualTo(120.0);
        assertThat(resumen.litrosPagados()).isZero();
        assertThat(resumen.litrosPendientes()).isEqualTo(100.0);
        assertThat(resumen.montoPagado()).isZero();
        assertThat(resumen.montoPendiente()).isPositive();
        assertThat(resumen.aceptadas()).isEqualTo(1);
        assertThat(resumen.rechazadas()).isEqualTo(1);
    }
}
