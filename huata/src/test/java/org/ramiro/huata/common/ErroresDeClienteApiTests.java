package org.ramiro.huata.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Una peticion mal formada tiene que responder 4xx, no 500.
 *
 * <p>Toda esta familia de errores la lanza Spring ANTES de entrar al controlador y caia en el
 * catch-all de {@link ApiExceptionHandler}, asi que la API respondia 500 a un id con letras, a un
 * metodo no permitido o a una ruta que no existe. Dos consecuencias: el cliente no podia distinguir
 * "enviaste algo mal" de "el servidor se rompio", y cada peticion equivocada quedaba escrita como
 * ERROR en el log, tapando los fallos reales (RNF-14).
 *
 * <p>Se prueba con un usuario con todas las autoridades para que lo que falle sea la forma de la
 * peticion y no la autorizacion.
 */
@SpringBootTest
@WithMockUser(
        username = "admin@huata.pe",
        authorities = {"rutas.read", "rutas.write"})
class ErroresDeClienteApiTests {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mockMvc;

    // Se construye a mano, igual que AutorizacionPorPermisoTests, para no depender de
    // @AutoConfigureMockMvc (cambio de paquete en Spring Boot 4).
    private MockMvc mockMvc() {
        if (mockMvc == null) {
            mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                            .springSecurity())
                    .build();
        }
        return mockMvc;
    }

    @Test
    void unaRutaDeApiQueNoExisteDevuelve404YNo500() throws Exception {
        mockMvc().perform(get("/api/v1/esto-no-existe-jamas"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"));
    }

    @Test
    void unMetodoNoPermitidoDevuelve405YNo500() throws Exception {
        mockMvc().perform(delete("/api/v1/rutas"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unIdDeTipoEquivocadoDevuelve400YNombraElParametro() throws Exception {
        mockMvc().perform(get("/api/v1/rutas/abc/puntos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Valor invalido para el parametro: id"));
    }

    @Test
    void unCuerpoJsonIlegibleDevuelve400YNo500() throws Exception {
        mockMvc().perform(post("/api/v1/rutas").contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void faltarUnParametroObligatorioDevuelve400YDiceCual() throws Exception {
        mockMvc().perform(get("/api/v1/asignaciones-ruta"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parametro obligatorio: fecha"));
    }

    @Test
    void elErrorConserva_elFormatoUnicoDeLaApi() throws Exception {
        mockMvc().perform(get("/api/v1/esto-tampoco-existe"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/esto-tampoco-existe"));
    }

    /**
     * El catch-all sigue existiendo para lo que si es un fallo del servidor: esta prueba solo fija
     * que el arreglo de arriba no convirtio TODO en 4xx. Una ruta valida con un id inexistente
     * sigue siendo 404 de dominio (NotFoundException), no 400.
     */
    @Test
    void unIdInexistenteEnUnaRutaValidaSigueSiendo404DeDominio() throws Exception {
        mockMvc().perform(get("/api/v1/rutas/99999999/puntos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Ruta")));
    }
}
