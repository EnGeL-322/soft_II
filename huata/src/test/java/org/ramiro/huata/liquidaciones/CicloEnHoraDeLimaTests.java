package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * El ciclo jueves-miercoles se corta en hora de Lima. Antes se cortaba en UTC (Lima es UTC-5): lo
 * entregado el miercoles despues de las 19:00 se pagaba con la semana siguiente, y lo del miercoles
 * anterior a esa hora entraba en esta. Tambien desalineaba la liquidacion del resumen que ve el
 * proveedor en su app, que ya usaba la hora de Lima.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class CicloEnHoraDeLimaTests {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate JUEVES = LocalDate.of(2026, 9, 3);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 9, 9);

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
        proveedor.setCodigo("LIM-" + UUID.randomUUID().toString().substring(0, 6));
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

    /** Entrega aceptada a la hora de Lima indicada. */
    private void entregaEnLima(LocalDateTime horaLima, double litros) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setFechaHora(horaLima.atZone(LIMA).toInstant());
        r.setLitros(litros);
        r = recojoRepository.save(r);
        ControlCalidad c = new ControlCalidad();
        c.setRecojo(r);
        c.setResultado(ResultadoCalidad.ACEPTADA);
        controlCalidadRepository.save(c);
    }

    private double litrosEnPlanilla(LocalDate inicio, LocalDate fin) {
        return liquidacionService.planillaDelCiclo(inicio, fin).stream()
                .filter(f -> f.proveedorCodigo().equals(proveedor.getCodigo()))
                .mapToDouble(LiquidacionService.FilaPlanilla::litrosEntregados)
                .sum();
    }

    @Test
    void loEntregadoElMiercolesALas21SePagaEnSuSemanaYNoEnLaSiguiente() {
        entregaEnLima(MIERCOLES.atTime(21, 0), 50); // 02:00 UTC del jueves

        assertThat(litrosEnPlanilla(JUEVES, MIERCOLES)).isEqualTo(50.0);
        assertThat(litrosEnPlanilla(JUEVES.plusDays(7), MIERCOLES.plusDays(7))).isZero();
        assertThat(liquidacionService.calcular(proveedor.getId(), JUEVES, MIERCOLES).getMontoTotal())
                .isEqualTo(85.0);
    }

    @Test
    void loEntregadoElMiercolesAnteriorDeNocheNoEntraEnEsteCiclo() {
        entregaEnLima(JUEVES.minusDays(1).atTime(22, 0), 30); // 03:00 UTC del jueves de inicio

        assertThat(litrosEnPlanilla(JUEVES, MIERCOLES)).isZero();
        assertThat(litrosEnPlanilla(JUEVES.minusDays(7), MIERCOLES.minusDays(7))).isEqualTo(30.0);
    }
}
