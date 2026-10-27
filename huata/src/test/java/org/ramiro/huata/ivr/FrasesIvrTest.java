package org.ramiro.huata.ivr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Lo que dice la voz sintetica: tiene que sonar como lo diria una persona, no como una planilla. */
class FrasesIvrTest {

    @Test
    void losLitrosSeRedondeanAlMedioLitroYUsanSingularYMedio() {
        assertThat(FrasesIvr.litros(42.5)).isEqualTo("42 litros y medio");
        assertThat(FrasesIvr.litros(42.3)).isEqualTo("42 litros y medio");
        assertThat(FrasesIvr.litros(42.2)).isEqualTo("42 litros");
        assertThat(FrasesIvr.litros(1)).isEqualTo("1 litro");
        assertThat(FrasesIvr.litros(1.5)).isEqualTo("1 litro y medio");
        assertThat(FrasesIvr.litros(0.5)).isEqualTo("medio litro");
        assertThat(FrasesIvr.litros(0)).isEqualTo("0 litros");
    }

    @Test
    void losSolesSeDicenConCentimosYSingular() {
        assertThat(FrasesIvr.soles(502.35)).isEqualTo("502 soles con 35 céntimos");
        assertThat(FrasesIvr.soles(502)).isEqualTo("502 soles");
        assertThat(FrasesIvr.soles(1)).isEqualTo("1 sol");
        assertThat(FrasesIvr.soles(1.01)).isEqualTo("1 sol con 1 céntimo");
        assertThat(FrasesIvr.soles(0.5)).isEqualTo("50 céntimos");
        assertThat(FrasesIvr.soles(0)).isEqualTo("0 soles");
        // El redondeo a centimos no debe perder el ultimo centimo por error de coma flotante.
        assertThat(FrasesIvr.soles(0.1 + 0.2)).isEqualTo("30 céntimos");
    }

    @Test
    void laFechaDiceElDiaDeLaSemana() {
        assertThat(FrasesIvr.fecha(LocalDate.of(2026, 10, 9))).isEqualTo("el viernes 9 de octubre");
        assertThat(FrasesIvr.fecha(LocalDate.of(2026, 10, 7))).isEqualTo("el miércoles 7 de octubre");
    }

    @Test
    void elTelefonoSeComparaPorSusUltimosNueveDigitos() {
        assertThat(TelefonoPeru.normalizar("+51987654321")).isEqualTo("987654321");
        assertThat(TelefonoPeru.normalizar("51987654321")).isEqualTo("987654321");
        assertThat(TelefonoPeru.normalizar("987 654 321")).isEqualTo("987654321");
        assertThat(TelefonoPeru.normalizar("987-654-321")).isEqualTo("987654321");
        assertThat(TelefonoPeru.normalizar("12345")).isNull();
        assertThat(TelefonoPeru.normalizar(null)).isNull();
    }

    @Test
    void elAudioDeCadaZonaSaleDeSuNombre() {
        assertThat(IvrService.audioDeZona("COLLANA I")).isEqualTo("zona_collana_i");
        assertThat(IvrService.audioDeZona("COLLANA II")).isEqualTo("zona_collana_ii");
        assertThat(IvrService.audioDeZona("Quillallaka / Santa Bárbara")).isEqualTo("zona_quillallaka_santa_barbara");
    }
}
