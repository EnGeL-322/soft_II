package org.ramiro.huata.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Regla de nombres casi iguales en productos e insumos (pedido del cliente, 2026-10-27). */
class NombresParecidosTest {

    @Test
    void losCasosDelClienteSonParecidos() {
        assertThat(NombresParecidos.parecidos("zal", "sal")).isTrue();
        assertThat(NombresParecidos.parecidos("yogurd", "yogurt")).isTrue();
        assertThat(NombresParecidos.parecidos("yogut", "yogurt")).isTrue();
    }

    @Test
    void igualesSinTildesMayusculasNiEspacios() {
        assertThat(NombresParecidos.iguales("  Azúcar   rubia ", "AZUCAR RUBIA")).isTrue();
        // Iguales no cuenta como "parecidos": es el mismo nombre.
        assertThat(NombresParecidos.parecidos("Azúcar", "azucar")).isFalse();
    }

    @Test
    void enNombresLargosSeTolerandosLetras() {
        assertThat(NombresParecidos.parecidos("queso fresco", "queso frezca")).isTrue();
        assertThat(NombresParecidos.parecidos("queso fresco", "queso andino")).isFalse();
    }

    @Test
    void nombresDistintosDeVerdadNoSeBloquean() {
        assertThat(NombresParecidos.parecidos("sal", "té")).isFalse();
        assertThat(NombresParecidos.parecidos("leche", "cuajo")).isFalse();
        assertThat(NombresParecidos.parecidos("yogurt", "yogurt de fresa")).isFalse();
        // Nombres muy cortos no se comparan: "té" y "té" son iguales, pero "té" y "tú" no se bloquean.
        assertThat(NombresParecidos.parecidos("té", "tú")).isFalse();
    }

    /** Un numero distinto es otro producto: un envase de 1 L no es uno de 5 L. */
    @Test
    void siLosNumerosDifierenNoSonParecidos() {
        assertThat(NombresParecidos.parecidos("Envase 1 L", "Envase 5 L")).isFalse();
        assertThat(NombresParecidos.parecidos("Balde 4 kg", "Balde 4 kgs")).isTrue();
    }

    @Test
    void buscarChoquePrefiereElIgualExacto() {
        assertThat(NombresParecidos.buscarChoque("sal", List.of("zal", "Sal"), s -> s)).contains("Sal");
        assertThat(NombresParecidos.buscarChoque("vainilla", List.of("zal", "Sal"), s -> s)).isEmpty();
    }
}
