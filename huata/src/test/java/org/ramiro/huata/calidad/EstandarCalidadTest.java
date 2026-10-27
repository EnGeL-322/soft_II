package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** La regla estandar que decide la aprobacion automatica (V50), como logica pura. */
class EstandarCalidadTest {

    private static ParametroCalidad parametro(String nombre, Double min, Double max) {
        ParametroCalidad p = new ParametroCalidad();
        p.setNombre(nombre);
        p.setValorMin(min);
        p.setValorMax(max);
        p.setUnidad("%");
        return p;
    }

    private static final List<ParametroCalidad> ESTANDAR = List.of(
            parametro("Densidad", 1.0296, 1.034),
            parametro("Punto de congelación", -0.56, -0.52),
            parametro("Grasa", 3.2, null),
            parametro("Solidos no grasos", 8.2, null));

    private static ControlCalidad analisis(ResultadoCalidad resultado, Double grasa, Double densidad) {
        ControlCalidad c = new ControlCalidad();
        c.setResultado(resultado);
        c.setGrasa(grasa);
        c.setDensidad(densidad);
        c.setPuntoCongelacion(-0.53);
        return c;
    }

    @Test
    void aptaYDentroDeTodosLosRangosPasa() {
        assertThat(EstandarCalidad.fallas(analisis(ResultadoCalidad.ACEPTADA, 3.5, 1.031), ESTANDAR)).isEmpty();
    }

    @Test
    void elLimiteExactoTambienPasa() {
        assertThat(EstandarCalidad.fallas(analisis(ResultadoCalidad.ACEPTADA, 3.2, 1.0296), ESTANDAR)).isEmpty();
    }

    @Test
    void unValorFueraDeRangoDejaAdvertencia() {
        assertThat(EstandarCalidad.fallas(analisis(ResultadoCalidad.ACEPTADA, 3.05, 1.031), ESTANDAR))
                .containsExactly("Grasa 3.05 bajo el mínimo 3.2 %");
    }

    @Test
    void unResultadoQueNoEsAptoNoSeApruebaSolo() {
        ControlCalidad c = analisis(ResultadoCalidad.OBSERVADA, 3.5, 1.031);
        c.setMotivoRechazo("Baja calidad · densidad fuera de banda");
        assertThat(EstandarCalidad.fallas(c, ESTANDAR)).first().asString().startsWith("Resultado observada");
    }

    @Test
    void loQueNoSeMidioNoEsFalla() {
        ControlCalidad c = analisis(ResultadoCalidad.ACEPTADA, null, 0.0);
        c.setPuntoCongelacion(null);
        assertThat(EstandarCalidad.fallas(c, ESTANDAR)).isEmpty();
    }

    @Test
    void unParametroInactivoNoSeExige() {
        ParametroCalidad grasa = parametro("Grasa", 3.2, null);
        grasa.setActivo(false);
        assertThat(EstandarCalidad.fallas(analisis(ResultadoCalidad.ACEPTADA, 2.5, 1.031), List.of(grasa))).isEmpty();
    }
}
