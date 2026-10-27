package org.ramiro.huata.liquidaciones;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Regla 7.7: el signo de un ajuste lo decide su TIPO. Es la parte del calculo del pago mas facil de
 * equivocar y la que cambia el importe que se le entrega al proveedor, asi que se cubre entera.
 */
class TipoAjusteTest {

    @ParameterizedTest
    @CsvSource({
        "DESCUENTO_QUESO, 50.0, -50.0",
        "PENALIDAD,       30.5, -30.5",
        "ADELANTO,       100.0, -100.0",
        "BONIFICACION,    20.0,  20.0",
    })
    void aplicaElSignoQueCorrespondeACadaTipo(TipoAjuste tipo, double monto, double esperado) {
        // Arrange · Act
        double resultado = tipo.aplicar(monto);

        // Assert
        assertThat(resultado).isEqualTo(esperado);
    }

    @ParameterizedTest
    @EnumSource(TipoAjuste.class)
    void ignoraElSignoDelMontoRecibido(TipoAjuste tipo) {
        // Arrange: un monto guardado en negativo por error no debe invertir el ajuste. Sin el
        // valor absoluto, una PENALIDAD de -40 sumaria 40 al pago en vez de restarlo.

        // Act
        double conNegativo = tipo.aplicar(-40.0);
        double conPositivo = tipo.aplicar(40.0);

        // Assert
        assertThat(conNegativo).isEqualTo(conPositivo);
    }

    @ParameterizedTest
    @EnumSource(value = TipoAjuste.class, names = {"DESCUENTO_QUESO", "PENALIDAD", "ADELANTO"})
    void descuentoQuesoPenalidadYAdelantoRestan(TipoAjuste tipo) {
        // Arrange · Act · Assert
        assertThat(tipo.esDescuento()).isTrue();
        assertThat(tipo.aplicar(10.0)).isNegative();
    }

    @Test
    void laBonificacionEsElUnicoAjusteQueSuma() {
        // Arrange · Act · Assert
        assertThat(TipoAjuste.BONIFICACION.esDescuento()).isFalse();
        assertThat(TipoAjuste.BONIFICACION.aplicar(10.0)).isPositive();
    }

    @ParameterizedTest
    @EnumSource(TipoAjuste.class)
    void unAjusteDeCeroNoMueveElImporte(TipoAjuste tipo) {
        // Arrange · Act · Assert
        assertThat(tipo.aplicar(0.0)).isZero();
    }
}
