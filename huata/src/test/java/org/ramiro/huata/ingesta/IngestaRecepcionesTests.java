package org.ramiro.huata.ingesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Ingesta automatica de recepciones desde el equipo de domotica.
 *
 * <p>Se prueba por HTTP y no llamando al servicio, porque la mitad de lo que importa aqui es la
 * seguridad: que sin la cabecera correcta no se registre nada. Un test que invoque el metodo
 * directamente se saltaria justo eso.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "huata.security.ingesta.api-key=" + IngestaRecepcionesTests.CLAVE)
class IngestaRecepcionesTests {

    static final String CLAVE = "clave-de-pruebas-de-ingesta";
    private static final String URL = "/api/v1/ingesta/recepciones";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private RecojoRepository recojos;

    @Autowired
    private ProductorRepository productores;

    @Autowired
    private MedicionInstrumentoRepository mediciones;

    @Autowired
    private ControlCalidadRepository controles;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(contexto)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                            .springSecurity())
                    .build();
        }
        return mvc;
    }

    private String cuerpo(String uuid, double litros) {
        String codigo = productores.findAll().get(0).getCodigo();
        // Mismo ticket que W1.jpeg, para que la prueba use datos reales del aparato.
        return """
                {"clientUuid":"%s","productorCodigo":"%s","fechaHora":"2026-09-09T13:30:00Z",
                 "litros":%s,"fuente":"PLANTA",
                 "medicion":{"analizadorSn":"48359","modo":"1","temperatura":12.2,"grasaPct":4.05,
                   "solidosNoGrasosPct":8.95,"densidadLactometrica":31.4,"proteinaPct":3.40,
                   "lactosaPct":4.87,"salesPct":0.7,"solidosTotalesPct":13.0,"aguaAnadidaPct":0.0,
                   "puntoCongelacion":-0.562,"ph":11.1}}
                """
                .formatted(uuid, codigo, litros);
    }

    @Test
    void registraLaRecepcionYGuardaLasLecturasDelInstrumento() throws Exception {
        // Arrange · Act
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-ok", 42.5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yaExistia").value(false));

        // Assert
        var recojo = recojos.findByClientUuid("ingesta-ok").orElseThrow();
        assertThat(recojo.getLitros()).isEqualTo(42.5);
        assertThat(recojo.getTemperatura()).isEqualTo(12.2);

        // El ticket completo queda en su propia tabla, no colgando del recojo.
        var medicion = mediciones.findByRecojoId(recojo.getId()).orElseThrow();
        assertThat(medicion.getAnalizadorSn()).isEqualTo("48359");
        assertThat(medicion.getDensidadLactometrica()).isEqualTo(31.4);
        assertThat(medicion.getPuntoCongelacion()).isEqualTo(-0.562);
        assertThat(medicion.getPh()).isEqualTo(11.1);
        assertThat(medicion.getAguaAnadidaPct()).isEqualTo(0.0);
    }

    @Test
    void noDictaminaCalidad() throws Exception {
        // Arrange: el aparato mide, la persona confirma. La ingesta no debe crear ningun control.

        // Act
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-sin-dictamen", 20.0)))
                .andExpect(status().isCreated());

        // Assert: hay medicion, pero nadie ha decidido si la entrega es apta.
        var recojo = recojos.findByClientUuid("ingesta-sin-dictamen").orElseThrow();
        assertThat(mediciones.findByRecojoId(recojo.getId())).isPresent();
        assertThat(controles.findByRecojoId(recojo.getId())).isEmpty();
    }

    @Test
    void unReintentoConElMismoUuidNoDuplicaLaEntrega() throws Exception {
        // Arrange: primer envio.
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-repetida", 30.0)))
                .andExpect(status().isCreated());
        long antes = recojos.count();

        // Act: el aparato reintenta tras un corte de red.
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-repetida", 30.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaExistia").value(true));

        // Assert: sin litros fantasma.
        assertThat(recojos.count()).isEqualTo(antes);
    }

    @Test
    void sinCabeceraNoRegistraNada() throws Exception {
        // Arrange
        long antes = recojos.count();

        // Act · Assert
        mvc().perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(cuerpo("ingesta-sin-clave", 10.0)))
                .andExpect(status().isUnauthorized());
        assertThat(recojos.count()).isEqualTo(antes);
    }

    @Test
    void conClaveIncorrectaNoRegistraNada() throws Exception {
        // Arrange
        long antes = recojos.count();

        // Act · Assert
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE + "-alterada")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-clave-mala", 10.0)))
                .andExpect(status().isUnauthorized());
        assertThat(recojos.count()).isEqualTo(antes);
    }

    @Test
    void rechazaUnProveedorQueNoExiste() throws Exception {
        // Arrange · Act · Assert
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"clientUuid":"ingesta-sin-proveedor","productorCodigo":"NO-EXISTE",
                                 "fechaHora":"2026-09-09T13:30:00Z","litros":10}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaLitrosNoPositivos() throws Exception {
        // Arrange · Act · Assert
        mvc().perform(post(URL)
                        .header("X-Api-Key", CLAVE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("ingesta-litros-negativos", -5.0)))
                .andExpect(status().isBadRequest());
    }
}
