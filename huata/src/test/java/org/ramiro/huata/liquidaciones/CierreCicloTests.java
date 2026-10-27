package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierre masivo del ciclo, el "Pagar a todos" del prototipo.
 *
 * <p>Es la operacion de mayor alcance del modulo: cierra el pago de toda una semana de una vez. Lo
 * que se cubre aqui es que no pague dos veces, que no toque lo ya cerrado y que los totales que
 * devuelve cuadren con lo que realmente liquido.
 */
@SpringBootTest
@Transactional
class CierreCicloTests {

    /** Periodo aislado y muy anterior a los datos DEMO, para no mezclarse con ellos. */
    private static final LocalDate INICIO = LocalDate.of(2024, 1, 4); // jueves
    private static final LocalDate FIN = LocalDate.of(2024, 1, 10); // miercoles

    @Autowired
    private LiquidacionService liquidaciones;

    @Autowired
    private RecojoRepository recojos;

    @Autowired
    private ProductorRepository productores;

    @Autowired
    private TarifaLecheRepository tarifas;

    @BeforeEach
    void tarifaVigente() {
        if (tarifas.findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now()).isEmpty()) {
            TarifaLeche t = new TarifaLeche();
            t.setPrecioNormal(1.70);
            t.setPrecioObservada(1.30);
            t.setVigenteDesde(LocalDate.of(2020, 1, 1));
            tarifas.save(t);
        }
    }

    private Recojo entrega(Productor productor, String dia, double litros) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(productor);
        r.setFechaHora(LocalDate.parse(dia).atTime(9, 0).toInstant(ZoneOffset.UTC));
        r.setLitros(litros);
        return recojos.save(r);
    }

    @Test
    void liquidaYCierraATodosLosProveedoresConEntregasEnElPeriodo() {
        // Arrange
        Productor a = productores.findAll().get(0);
        Productor b = productores.findAll().get(1);
        entrega(a, "2024-01-04", 100);
        entrega(b, "2024-01-05", 50);

        // Act
        LiquidacionService.CierreCicloResultado r = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        assertThat(r.liquidadas()).hasSize(2);
        assertThat(r.liquidadas()).allMatch(l -> l.getEstado() == EstadoLiquidacion.CLOSED);
        assertThat(r.litrosTotales()).isEqualTo(150.0);
        assertThat(r.yaCerrados()).isZero();
    }

    @Test
    void elTotalDevueltoEsLaSumaDeLosNetosLiquidados() {
        // Arrange
        entrega(productores.findAll().get(0), "2024-01-04", 100);

        // Act
        LiquidacionService.CierreCicloResultado r = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        double sumaDeNetos = r.liquidadas().stream().mapToDouble(Liquidacion::getMontoNeto).sum();
        assertThat(r.montoNetoTotal()).isEqualTo(sumaDeNetos);
    }

    @Test
    void unaSegundaPasadaNoVuelveAPagarLoYaCerrado() {
        // Arrange: el ciclo ya se cerro una vez.
        entrega(productores.findAll().get(0), "2024-01-04", 100);
        liquidaciones.cerrarCiclo(INICIO, FIN);

        // Act
        LiquidacionService.CierreCicloResultado segunda = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert: nadie se paga dos veces.
        assertThat(segunda.liquidadas()).isEmpty();
        assertThat(segunda.yaCerrados()).isEqualTo(1);
        assertThat(segunda.montoNetoTotal()).isZero();
    }

    @Test
    void conservaLosAjustesDelProveedorAlCerrarElCiclo() {
        // Arrange: una liquidacion con un adelanto registrado antes del cierre masivo.
        Productor p = productores.findAll().get(0);
        entrega(p, "2024-01-04", 100);
        Liquidacion previa = liquidaciones.calcular(p.getId(), INICIO, FIN);
        liquidaciones.agregarAjuste(previa.getId(), new AjusteLiquidacionRequest(TipoAjuste.ADELANTO, 20.0, "Adelanto"));

        // Act
        LiquidacionService.CierreCicloResultado r = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert: el adelanto sigue restando en el neto que se cierra.
        Liquidacion cerrada = r.liquidadas().get(0);
        assertThat(cerrada.getAjustesTotal()).isEqualTo(-20.0);
        assertThat(cerrada.getMontoNeto()).isEqualTo(cerrada.getMontoTotal() - 20.0);
    }

    @Test
    void unPeriodoSinEntregasNoLiquidaANadie() {
        // Arrange: 2019 no tiene datos, ni DEMO ni de estas pruebas.

        // Act
        LiquidacionService.CierreCicloResultado r =
                liquidaciones.cerrarCiclo(LocalDate.of(2019, 1, 3), LocalDate.of(2019, 1, 9));

        // Assert
        assertThat(r.liquidadas()).isEmpty();
        assertThat(r.yaCerrados()).isZero();
        assertThat(r.montoNetoTotal()).isZero();
    }

    @Test
    void rechazaUnPeriodoConElFinAntesDelInicio() {
        // Arrange · Act · Assert
        assertThatThrownBy(() -> liquidaciones.cerrarCiclo(FIN, INICIO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior a su inicio");
    }

    @Test
    void ignoraLasEntregasDeOtrosPeriodos() {
        // Arrange: una entrega del ciclo y otra de la semana siguiente.
        Productor p = productores.findAll().get(0);
        entrega(p, "2024-01-05", 40);
        entrega(p, "2024-01-15", 999);

        // Act
        LiquidacionService.CierreCicloResultado r = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        assertThat(r.litrosTotales()).isEqualTo(40.0);
    }

    @Test
    void incluyeLaEntregaDelUltimoDiaDelPeriodo() {
        // Arrange: el limite superior es exclusivo en la consulta; el miercoles debe entrar igual.
        entrega(productores.findAll().get(0), FIN.toString(), 33);

        // Act
        LiquidacionService.CierreCicloResultado r = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        assertThat(r.litrosTotales()).isEqualTo(33.0);
    }

    @Test
    void noAdmiteAjustesSobreUnaLiquidacionYaCerradaPorElPagoGlobal() {
        // Arrange
        Productor p = productores.findAll().get(0);
        entrega(p, "2024-01-04", 100);
        Liquidacion cerrada = liquidaciones.cerrarCiclo(INICIO, FIN).liquidadas().get(0);

        // Act · Assert
        assertThatThrownBy(() -> liquidaciones.agregarAjuste(
                        cerrada.getId(), new AjusteLiquidacionRequest(TipoAjuste.PENALIDAD, 10.0, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cerrada");
    }

    @Test
    void unaEntregaCanceladaNoSeLiquida() {
        // Arrange
        Productor p = productores.findAll().get(0);
        Recojo r = entrega(p, "2024-01-04", 80);
        r.setEstado(org.ramiro.huata.recojo.EstadoRecojo.CANCELLED);
        recojos.save(r);

        // Act
        LiquidacionService.CierreCicloResultado resultado = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        assertThat(resultado.liquidadas()).isEmpty();
    }

    @Test
    void unInstanteDelUltimoDiaAMediaNocheUtcSigueDentroDelPeriodo() {
        // Arrange: el borde superior es el caso donde es facil perder una entrega por un dia.
        Productor p = productores.findAll().get(0);
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(p);
        r.setFechaHora(Instant.parse("2024-01-10T23:59:00Z"));
        r.setLitros(12.0);
        recojos.save(r);

        // Act
        LiquidacionService.CierreCicloResultado resultado = liquidaciones.cerrarCiclo(INICIO, FIN);

        // Assert
        assertThat(resultado.litrosTotales()).isEqualTo(12.0);
    }

    @Test
    void noPermiteRecalcularUnPeriodoYaCerrado() {
        // Arrange: el fallo original era que aqui se creaba una SEGUNDA liquidacion del mismo
        // proveedor y la misma semana, en vez de negarse.
        Productor p = productores.findAll().get(0);
        entrega(p, "2024-01-04", 100);
        liquidaciones.cerrarCiclo(INICIO, FIN);

        // Act · Assert
        assertThatThrownBy(() -> liquidaciones.calcular(p.getId(), INICIO, FIN))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está cerrada");
    }

    @Test
    void recalcularUnPeriodoAbiertoReutilizaLaMismaLiquidacion() {
        // Arrange
        Productor p = productores.findAll().get(0);
        entrega(p, "2024-01-04", 100);
        Liquidacion primera = liquidaciones.calcular(p.getId(), INICIO, FIN);

        // Act: se registra otra entrega y se vuelve a calcular.
        entrega(p, "2024-01-05", 50);
        Liquidacion segunda = liquidaciones.calcular(p.getId(), INICIO, FIN);

        // Assert: es la misma fila actualizada, no una nueva.
        assertThat(segunda.getId()).isEqualTo(primera.getId());
        assertThat(segunda.getLitrosTotales()).isEqualTo(150.0);
    }
}
