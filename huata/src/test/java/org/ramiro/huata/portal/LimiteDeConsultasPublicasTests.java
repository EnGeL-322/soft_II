package org.ramiro.huata.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.ramiro.huata.common.LimitadorDeConsultas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Limite de consultas del portal publico: que el padron no se pueda recorrer a fuerza de intentos.
 *
 * <p><b>Por que importa ahora.</b> {@code /public/productores/{codigo}/calidad} responde 200 si el
 * codigo existe y 404 si no, asi que probar codigos revela cuales estan registrados. Desde que el
 * codigo del proveedor es su DNI (seccion 36.14), lo que se podria recolectar son documentos de
 * personas.
 *
 * <p>El limite se baja a 3 por minuto con {@code @TestPropertySource} para no tener que hacer 20
 * peticiones en cada caso; la regla es la misma, solo cambia el numero.
 */
@SpringBootTest
@TestPropertySource(properties = "huata.portal.consultas-por-minuto=3")
class LimiteDeConsultasPublicasTests {

    private static final String RUTA = "/api/v1/public/productores/NO-EXISTE/calidad";

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                            .springSecurity())
                    .build();
        }
        return mockMvc;
    }

    /** Consulta desde una IP concreta, como la ve el backend detras de nginx. */
    private int consultarDesde(String ip) throws Exception {
        return mockMvc().perform(get(RUTA).header("X-Real-IP", ip)).andReturn().getResponse().getStatus();
    }

    @Test
    void lasPrimerasConsultasPasanYLaQueSobraRecibe429() throws Exception {
        String ip = "203.0.113.10";

        // Las tres del cupo llegan al controlador: 404 porque el codigo no existe, no 429.
        IntStream.rangeClosed(1, 3).forEach(i -> {
            try {
                assertThat(consultarDesde(ip)).isEqualTo(404);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        assertThat(consultarDesde(ip)).isEqualTo(429);
    }

    /**
     * El detalle que decide si esto sirve o estorba: el cupo es <b>por origen</b>. Contando por
     * {@code getRemoteAddr()} —que detras de nginx es siempre la IP del proxy— un solo abusador
     * dejaria sin portal a todos los demas, y el limitador seria la forma de tumbar el servicio.
     */
    @Test
    void elCupoDeUnOrigenNoAfectaAOtro() throws Exception {
        String abusador = "203.0.113.20";
        for (int i = 0; i < 4; i++) {
            consultarDesde(abusador);
        }
        assertThat(consultarDesde(abusador)).isEqualTo(429);

        // Otro proveedor, consultando lo suyo desde otra conexion, sigue entrando.
        assertThat(consultarDesde("203.0.113.21")).isEqualTo(404);
    }

    /** Si no hay X-Real-IP se cae a X-Forwarded-For, tomando el cliente original y no los proxies. */
    @Test
    void sinXRealIpSeUsaElPrimeroDeXForwardedFor() throws Exception {
        String cabecera = "198.51.100.7, 10.0.0.1, 10.0.0.2";
        for (int i = 0; i < 4; i++) {
            mockMvc().perform(get(RUTA).header("X-Forwarded-For", cabecera));
        }

        assertThat(mockMvc().perform(get(RUTA).header("X-Forwarded-For", cabecera))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(429);

        // Otro cliente detras de los mismos proxies conserva su cupo.
        assertThat(mockMvc().perform(get(RUTA).header("X-Forwarded-For", "198.51.100.8, 10.0.0.1"))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    /** El 429 dice cuanto esperar; sin eso el cliente solo sabe que fallo. */
    @Test
    void elRechazoIndicaCuandoReintentar() throws Exception {
        String ip = "203.0.113.30";
        for (int i = 0; i < 4; i++) {
            consultarDesde(ip);
        }

        mockMvc().perform(get(RUTA).header("X-Real-IP", ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * El resumen del dia NO se limita: lo consulta la app del proveedor cada vez que se abre (RF-49) y
     * devuelve lo mismo pregunte quien pregunte, asi que repetirlo no revela nada nuevo.
     */
    @Test
    void elResumenDelDiaNoSeLimita() throws Exception {
        String ip = "203.0.113.40";
        for (int i = 0; i < 10; i++) {
            mockMvc().perform(get("/api/v1/public/incidencias/hoy").header("X-Real-IP", ip))
                    .andExpect(status().isOk());
        }
    }

    /** Lo mismo para el mapa de calidad por zona (RF-48). */
    @Test
    void lasZonasDelDiaNoSeLimitan() throws Exception {
        String ip = "203.0.113.41";
        for (int i = 0; i < 10; i++) {
            mockMvc().perform(get("/api/v1/public/calidad/zonas").header("X-Real-IP", ip))
                    .andExpect(status().isOk());
        }
    }
}

/** El contador por si solo, sin servidor: la ventana, el cupo y el reinicio. */
class LimitadorDeConsultasTests {

    private static final Instant T0 = Instant.parse("2026-09-23T10:00:00Z");

    /** Reloj que avanza a mano, para no depender de esperas reales. */
    private static final class RelojFijo extends Clock {
        private Instant ahora = T0;

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        void avanzar(Duration cuanto) {
            ahora = ahora.plus(cuanto);
        }
    }

    @Test
    void permiteHastaElCupoYRechazaElSiguiente() {
        LimitadorDeConsultas limitador = new LimitadorDeConsultas(3, new RelojFijo());

        assertThat(limitador.permitir("a")).isTrue();
        assertThat(limitador.permitir("a")).isTrue();
        assertThat(limitador.permitir("a")).isTrue();
        assertThat(limitador.permitir("a")).isFalse();
    }

    @Test
    void alPasarLaVentanaElCupoVuelveAEmpezar() {
        RelojFijo reloj = new RelojFijo();
        LimitadorDeConsultas limitador = new LimitadorDeConsultas(2, reloj);
        limitador.permitir("a");
        limitador.permitir("a");
        assertThat(limitador.permitir("a")).isFalse();

        reloj.avanzar(Duration.ofMinutes(1).plusSeconds(1));

        assertThat(limitador.permitir("a")).isTrue();
    }

    @Test
    void cadaOrigenLlevaSuPropioConteo() {
        LimitadorDeConsultas limitador = new LimitadorDeConsultas(1, new RelojFijo());

        assertThat(limitador.permitir("a")).isTrue();
        assertThat(limitador.permitir("a")).isFalse();
        assertThat(limitador.permitir("b")).isTrue();
    }

    /** Un cupo de cero o menos desactiva el limite; sirve para apagarlo por configuracion. */
    @Test
    void unCupoNoPositivoDesactivaElLimite() {
        LimitadorDeConsultas limitador = new LimitadorDeConsultas(0, new RelojFijo());

        for (int i = 0; i < 100; i++) {
            assertThat(limitador.permitir("a")).isTrue();
        }
    }

    @Test
    void elTiempoDeEsperaNuncaEsCeroCuandoYaSeConsumioElCupo() {
        LimitadorDeConsultas limitador = new LimitadorDeConsultas(1, new RelojFijo());
        limitador.permitir("a");

        assertThat(limitador.segundosParaReintentar("a")).isPositive();
    }

    @Test
    void unOrigenDesconocidoNoTieneQueEsperar() {
        assertThat(new LimitadorDeConsultas(1, new RelojFijo()).segundosParaReintentar("nadie"))
                .isZero();
    }
}
