package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regla 7.5: correccion de densidad a 15 C y categoria de la leche.
 *
 * <p>La prueba principal recorre LAS 200 FILAS del Excel real de la planta
 * (tabla_correccion_densidad_15C.csv). No se copian valores a mano: si alguien cambia la formula o
 * los umbrales, el Excel deja de cuadrar y el test lo dice. Es logica pura y se cubre entera, como
 * pide DESARROLLO.md para esta regla.
 */
class CorreccionDensidadServiceTest {

    private final CorreccionDensidadService servicio = new CorreccionDensidadService();

    /** Las 200 filas del Excel de la planta. */
    static Stream<Arguments> tablaDeLaPlanta() throws Exception {
        var recurso = CorreccionDensidadServiceTest.class.getResourceAsStream("/tabla_correccion_densidad_15C.csv");
        try (var lector = new BufferedReader(new InputStreamReader(recurso, StandardCharsets.UTF_8))) {
            List<String> lineas = lector.lines().skip(1).filter(l -> !l.isBlank()).toList();
            return lineas.stream().map(l -> {
                String[] c = l.split(",");
                return Arguments.of(
                        Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]), c[3].trim());
            }).toList().stream();
        }
    }

    @ParameterizedTest(name = "lectura {0} a {1} C -> {2} ({3})")
    @MethodSource("tablaDeLaPlanta")
    void reproduceElExcelDeLaPlantaFilaAFila(
            double lectura, double temperatura, double densidadEsperada, String categoriaEsperada) {
        // Arrange · Act
        double corregida = servicio.corregirA15C(lectura, temperatura);
        CategoriaLeche categoria = servicio.clasificar(lectura, temperatura);

        // Assert
        assertThat(corregida).isCloseTo(densidadEsperada, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(categoria.etiqueta()).isEqualTo(categoriaEsperada);
    }

    @Test
    void aQuinceGradosLaLecturaNoSeCorrige() {
        // Arrange · Act · Assert: 15 C es la temperatura de referencia del lactodensimetro.
        assertThat(servicio.corregirA15C(31.0, 15.0)).isCloseTo(1.0310, org.assertj.core.data.Offset.offset(1e-9));
    }

    @ParameterizedTest
    @CsvSource({
        // Por encima de 15 C la leche se dilata y el aparato lee de menos: la correccion suma.
        "31.0, 20.0, 1.0320",
        "31.0, 25.0, 1.0330",
        // Por debajo, resta. Es el caso de W1.jpeg (12.2 C), fuera de la tabla del Excel.
        "31.4, 12.2, 1.03084",
        // Y el de W.jpeg: lectura 23.9, tambien fuera de la tabla (empieza en 25).
        "23.9, 16.9, 1.02428",
    })
    void corrigeTambienFueraDelRangoTabulado(double lectura, double temperatura, double esperada) {
        // Arrange · Act · Assert
        assertThat(servicio.corregirA15C(lectura, temperatura))
                .isCloseTo(esperada, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void losTicketsRealesSeClasificanAunqueEsteFueraDelExcel() {
        // Arrange: los dos tickets fotografiados en planta.

        // Act · Assert
        // W1: leche buena pese a estar la temperatura por debajo del rango tabulado.
        assertThat(servicio.clasificar(31.4, 12.2)).isEqualTo(CategoriaLeche.REGULAR);
        // W: lectura muy baja, coherente con el 23% de agua anadida que declara su propio ticket.
        assertThat(servicio.clasificar(23.9, 16.9)).isEqualTo(CategoriaLeche.CON_AGUA);
    }

    @ParameterizedTest
    @CsvSource({
        "1.0279, CON_AGUA",
        "1.0280, MALO",
        "1.0299, MALO",
        "1.0300, REGULAR",
        "1.0309, REGULAR",
        "1.0310, BUENO",
        "1.0319, BUENO",
        "1.0320, MUY_BUENO",
    })
    void losUmbralesCaenExactamenteDondeDiceElExcel(double densidad, CategoriaLeche esperada) {
        // Arrange · Act · Assert: los limites son el punto donde es facil equivocarse por un decimal.
        assertThat(CategoriaLeche.de(densidad)).isEqualTo(esperada);
    }

    @ParameterizedTest
    @CsvSource({
        "CON_AGUA,  RECHAZADA",
        "MALO,      RECHAZADA",
        "REGULAR,   OBSERVADA",
        "BUENO,     ACEPTADA",
        "MUY_BUENO, ACEPTADA",
    })
    void traduceLaCategoriaAlResultadoDeLaRecepcion(CategoriaLeche categoria, ResultadoCalidad esperado) {
        // Arrange · Act · Assert
        assertThat(servicio.resultadoDe(categoria)).isEqualTo(esperado);
    }
}
