package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.ramiro.huata.calidad.DecisionRecepcionService.Decision;
import org.ramiro.huata.calidad.DecisionRecepcionService.Muestra;
import org.ramiro.huata.calidad.DecisionRecepcionService.ParametrosDecision;

/**
 * Regla 7.1 al completo. DESARROLLO.md enumera los casos minimos y aqui esta cada uno:
 * agua confirmada, Lugol positivo, densidad bajo el minimo, densidad sobre el maximo, densidad
 * exactamente en cada limite (deben ser APTAS), densidad 0 (no medida), todo normal, todo normal
 * con proveedor Excelencia, y adulteracion y baja calidad simultaneas.
 *
 * <p>Logica pura: no levanta contexto de Spring, corre en milisegundos y se puede cubrir al 100%.
 */
class DecisionRecepcionServiceTest {

    private final DecisionRecepcionService servicio = new DecisionRecepcionService();

    /** Banda de densidad y tarifas del DEMO; las cifras son las de DESARROLLO.md 7.4. */
    private static final ParametrosDecision PARAMS = new ParametrosDecision(1.0296, 1.0340, 1.70, 1.30, 2.00);

    private static Muestra normal(double densidad) {
        return new Muestra(AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA, densidad);
    }

    private Decision decidir(Muestra muestra) {
        return servicio.decidir(muestra, false, PARAMS);
    }

    // ------------------------------------------------------------------ Regla 1: adulteracion

    @Test
    @DisplayName("Agua confirmada: se rechaza y no se paga")
    void aguaConfirmadaSeRechaza() {
        Decision d = decidir(new Muestra(
                AguaAnadida.CONFIRMADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(d.aceptada()).isFalse();
        assertThat(d.precioPorLitro()).isZero();
        assertThat(d.motivo()).contains("agua");
    }

    @Test
    @DisplayName("Lugol positivo: almidon anadido, se rechaza")
    void lugolPositivoSeRechaza() {
        Decision d = decidir(new Muestra(
                AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.POSITIVA, ResultadoCalidad.ACEPTADA, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(d.precioPorLitro()).isZero();
        assertThat(d.motivo()).contains("Lugol");
    }

    /**
     * El caso que DESARROLLO.md pide probar explicitamente: la regla 1 manda sobre la 2. Una muestra
     * que dispara ambas se rechaza; si el orden se invirtiera, se pagaria leche adulterada a tarifa
     * reducida en vez de devolverla.
     */
    @ParameterizedTest(name = "adulterada + baja calidad simultaneas: {0} / densidad {1}")
    @CsvSource({
        "CONFIRMADA, 1.0100",
        "CONFIRMADA, 1.0500",
        "SOSPECHA,   1.0100",
    })
    @DisplayName("Adulteracion y baja calidad a la vez: gana la adulteracion")
    void laAdulteracionTienePrioridadSobreLaBajaCalidad(AguaAnadida agua, double densidad) {
        Muestra muestra = agua == AguaAnadida.CONFIRMADA
                ? new Muestra(agua, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA, densidad)
                // Sospecha sola es baja calidad; se le suma Lugol positivo para que tambien sea adulterada.
                : new Muestra(agua, ResultadoLugol.POSITIVA, ResultadoCalidad.ACEPTADA, densidad);

        Decision d = decidir(muestra);

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(d.aceptada()).isFalse();
        assertThat(d.precioPorLitro()).isZero();
    }

    // ----------------------------------------------------------------- Regla 2: baja calidad

    @ParameterizedTest(name = "densidad {0} fuera de banda")
    @CsvSource({"1.0295", "1.0100", "1.0341", "1.0500"})
    @DisplayName("Densidad fuera de la banda: observada a tarifa reducida")
    void densidadFueraDeBandaEsBajaCalidad(double densidad) {
        Decision d = decidir(normal(densidad));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.OBSERVADA);
        assertThat(d.aceptada()).isTrue();
        assertThat(d.precioPorLitro()).isEqualTo(1.30);
    }

    /**
     * Los limites pertenecen a la banda. Tratarlos como fuera castigaria al proveedor por un
     * redondeo del lactodensimetro, y es el error clasico de escribir {@code <=} donde va {@code <}.
     */
    @ParameterizedTest(name = "densidad exactamente en el limite: {0}")
    @CsvSource({"1.0296", "1.0340"})
    @DisplayName("Densidad justo en el limite: APTA")
    void losLimitesDeLaBandaSonAptos(double densidad) {
        Decision d = decidir(normal(densidad));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
        assertThat(d.precioPorLitro()).isEqualTo(1.70);
    }

    /**
     * Densidad 0 = no se midio. Las recepciones de ruta llegan sin densidad y se miden despues en
     * planta: si el 0 disparara baja calidad, se les pagaria 1.30 a todas por no haber sido medidas.
     */
    @Test
    @DisplayName("Densidad 0 es 'no medida', no una densidad fuera de banda")
    void densidadCeroNoDisparaBajaCalidad() {
        Decision d = decidir(normal(0));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
        assertThat(d.precioPorLitro()).isEqualTo(1.70);
    }

    @Test
    @DisplayName("Sospecha de agua: observada, no rechazada")
    void sospechaDeAguaEsBajaCalidad() {
        Decision d = decidir(new Muestra(
                AguaAnadida.SOSPECHA, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.OBSERVADA);
        assertThat(d.aceptada()).isTrue();
        assertThat(d.precioPorLitro()).isEqualTo(1.30);
    }

    // ------------------------------------------------------------------------ Regla 3: apta

    @Test
    @DisplayName("Todo normal: apta a tarifa normal")
    void todoNormalEsAptaATarifaNormal() {
        Decision d = decidir(normal(1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.ACEPTADA);
        assertThat(d.aceptada()).isTrue();
        assertThat(d.precioPorLitro()).isEqualTo(1.70);
    }

    @Test
    @DisplayName("Proveedor Excelencia y muestra apta: cobra la tarifa preferencial")
    void proveedorExcelenciaCobraSuTarifa() {
        Decision d = servicio.decidir(normal(1.0310), true, PARAMS);

        assertThat(d.precioPorLitro()).isEqualTo(2.00);
        assertThat(d.motivo()).contains("Excelencia");
    }

    /**
     * 7.4: la tarifa preferencial se pierde en cuanto hay una incidencia. No basta con que el
     * proveedor este marcado: la muestra tiene que salir apta.
     */
    @ParameterizedTest(name = "Excelencia con muestra {0} no cobra 2.00")
    @CsvSource({"SOSPECHA, 1.0310, 1.30", "NO_EVIDENCIADA, 1.0100, 1.30", "CONFIRMADA, 1.0310, 0.0"})
    @DisplayName("La marca Excelencia no salva una muestra con incidencia")
    void laMarcaExcelenciaNoSalvaUnaMuestraConIncidencia(AguaAnadida agua, double densidad, double precioEsperado) {
        Decision d = servicio.decidir(
                new Muestra(agua, ResultadoLugol.NEGATIVA, ResultadoCalidad.ACEPTADA, densidad), true, PARAMS);

        assertThat(d.precioPorLitro()).isEqualTo(precioEsperado);
    }

    /**
     * Una tarifa anterior a la regla 7.4 no tiene precio de Excelencia. Caer a la tarifa normal es
     * lo correcto; un NullPointerException aqui detendria la liquidacion de toda la semana.
     */
    @Test
    @DisplayName("Tarifa sin precio de Excelencia: el proveedor marcado cobra el normal")
    void sinPrecioDeExcelenciaSeCobraElNormal() {
        ParametrosDecision sinExcelencia = new ParametrosDecision(1.0296, 1.0340, 1.70, 1.30, null);

        Decision d = servicio.decidir(normal(1.0310), true, sinExcelencia);

        assertThat(d.precioPorLitro()).isEqualTo(1.70);
        assertThat(d.motivo()).isEqualTo("Apta");
    }

    // ------------------------------------------------------- La calidad declarada solo empeora

    /**
     * Lo que el operador declara se tiene en cuenta para empeorar la decision, nunca para mejorarla:
     * declarar "apta" una muestra con agua confirmada no la vuelve apta. Es el sentido de que la
     * calidad la decida el sistema.
     */
    @ParameterizedTest(name = "declarada {0} sobre muestra adulterada")
    @EnumSource(ResultadoCalidad.class)
    @DisplayName("Ninguna calidad declarada salva a una muestra adulterada")
    void laCalidadDeclaradaNoPuedeSalvarUnaMuestraAdulterada(ResultadoCalidad declarada) {
        Decision d = decidir(new Muestra(AguaAnadida.CONFIRMADA, ResultadoLugol.NEGATIVA, declarada, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
    }

    @Test
    @DisplayName("Declarar observada una muestra limpia la deja observada")
    void laCalidadDeclaradaSiPuedeEmpeorar() {
        Decision d = decidir(new Muestra(
                AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.OBSERVADA, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.OBSERVADA);
        assertThat(d.precioPorLitro()).isEqualTo(1.30);
    }

    @Test
    @DisplayName("Declarar rechazada una muestra limpia la rechaza")
    void declararRechazadaLaRechaza() {
        Decision d = decidir(new Muestra(
                AguaAnadida.NO_EVIDENCIADA, ResultadoLugol.NEGATIVA, ResultadoCalidad.RECHAZADA, 1.0310));

        assertThat(d.resultado()).isEqualTo(ResultadoCalidad.RECHAZADA);
        assertThat(d.precioPorLitro()).isZero();
    }

    // -------------------------------------------------------------------------- Invariantes

    @Test
    @DisplayName("Una recepcion rechazada nunca se paga")
    void ningunaRecepcionRechazadaSePaga() {
        for (AguaAnadida agua : AguaAnadida.values()) {
            for (ResultadoLugol lugol : ResultadoLugol.values()) {
                for (ResultadoCalidad declarada : ResultadoCalidad.values()) {
                    for (double densidad : new double[] {0, 1.0100, 1.0296, 1.0310, 1.0340, 1.0500}) {
                        Decision d = servicio.decidir(
                                new Muestra(agua, lugol, declarada, densidad), true, PARAMS);
                        if (!d.aceptada()) {
                            assertThat(d.precioPorLitro())
                                    .as("%s/%s/%s/%s", agua, lugol, declarada, densidad)
                                    .isZero();
                        }
                        assertThat(d.precioPorLitro()).isNotNegative();
                    }
                }
            }
        }
    }
}
