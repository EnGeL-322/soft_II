package org.ramiro.huata.contabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Partida doble del libro diario/mayor: la matematica que sostiene todo el modulo contable
 * (registrar, libroDiario, libroMayor). Los asientos "de verdad" (uno por compra/lote/venta) se
 * prueban donde se generan (CompraServiceTests, QueseriaServiceTests); aqui se prueba el motor.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class AsientoContableServiceTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired
    private AsientoContableService service;

    @Test
    void registrarPersisteElAsientoConSusLineas() {
        AsientoContable asiento = service.registrar(
                HOY,
                "Prueba",
                "Comentario de prueba",
                OrigenAsiento.COMPRA,
                1L,
                List.of(LineaContable.debe("2411", BigDecimal.valueOf(100)), LineaContable.haber("4212", BigDecimal.valueOf(100))));

        assertThat(asiento.getId()).isNotNull();
        assertThat(asiento.getLineas()).hasSize(2);
    }

    @Test
    void unAsientoQueNoCuadraNoSeGuarda() {
        assertThatThrownBy(() -> service.registrar(
                        HOY,
                        "Descuadrado",
                        null,
                        OrigenAsiento.COMPRA,
                        null,
                        List.of(LineaContable.debe("2411", BigDecimal.valueOf(100)), LineaContable.haber("4212", BigDecimal.valueOf(99)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Partida doble");
    }

    @Test
    void unaLineaSinCuentaConocidaFalla() {
        assertThatThrownBy(() -> service.registrar(
                        HOY,
                        "Cuenta inexistente",
                        null,
                        OrigenAsiento.COMPRA,
                        null,
                        List.of(LineaContable.debe("9999", BigDecimal.TEN), LineaContable.haber("4212", BigDecimal.TEN))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void lasLineasEnCeroSeDescartanYSiNoQuedaNingunaFalla() {
        assertThatThrownBy(() -> service.registrar(
                        HOY,
                        "Todo en cero",
                        null,
                        OrigenAsiento.COMPRA,
                        null,
                        List.of(LineaContable.debe("2411", BigDecimal.ZERO), LineaContable.haber("4212", BigDecimal.ZERO))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void libroMayorAcumulaElSaldoComoDebeMenosHaber() {
        service.registrar(
                HOY.minusDays(2),
                "Primero",
                null,
                OrigenAsiento.COMPRA,
                null,
                List.of(LineaContable.debe("1011", BigDecimal.valueOf(200)), LineaContable.haber("7021", BigDecimal.valueOf(200))));
        service.registrar(
                HOY.minusDays(1),
                "Segundo",
                null,
                OrigenAsiento.VENTA,
                null,
                List.of(LineaContable.debe("6921", BigDecimal.valueOf(50)), LineaContable.haber("1011", BigDecimal.valueOf(50))));

        LibroMayorResponse libroMayor = service.libroMayor("1011", HOY.minusDays(3), HOY);

        assertThat(libroMayor.movimientos()).hasSize(2);
        assertThat(libroMayor.movimientos().get(0).saldo()).isEqualByComparingTo("200.00");
        assertThat(libroMayor.movimientos().get(1).saldo()).isEqualByComparingTo("150.00");
        assertThat(libroMayor.saldoFinal()).isEqualByComparingTo("150.00");
    }

    @Test
    void planCuentasTraeElCatalogoCompletoSembrado() {
        assertThat(service.planCuentas()).extracting(PlanCuentaResponse::codigo).contains("1011", "2111", "7021", "40111");
    }
}
