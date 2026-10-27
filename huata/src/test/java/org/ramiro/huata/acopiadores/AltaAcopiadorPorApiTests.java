package org.ramiro.huata.acopiadores;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * El alta de acopiador por el API, con el cuerpo tal como lo manda el formulario: los campos que se
 * dejan vacios llegan como "" y no como null. Una prueba del servicio no lo veia, porque la
 * validacion de forma (@Pattern) vive en el controlador: el telefono vacio se rechazaba ahi.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe", authorities = {Permiso.ACOPIADORES_READ, Permiso.ACOPIADORES_WRITE})
class AltaAcopiadorPorApiTests {

    @Autowired WebApplicationContext context;
    @Autowired RutaRepository rutaRepository;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Long rutaConZona() {
        return rutaRepository.findAll().stream()
                .filter(r -> r.getZona() != null && !r.getZona().isBlank())
                .map(Ruta::getId)
                .findFirst()
                .orElseThrow();
    }

    private String cuerpo(String dni, String telefono, String username) {
        return """
                {"nombres":"Rosa","apellidos":"Quispe","documentoIdentidad":"%s","telefono":"%s",
                 "rutaId":%d,"username":"%s","password":"Huata2026*"}
                """.formatted(dni, telefono, rutaConZona(), username);
    }

    @Test
    void dniYTelefonoVaciosSonValidos() throws Exception {
        mvc().perform(post("/api/v1/acopiadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("", "", "rosa.api@huata.pe")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentoIdentidad").doesNotExist())
                .andExpect(jsonPath("$.telefono").doesNotExist());
    }

    @Test
    void unTelefonoEscritoTieneQueSerValido() throws Exception {
        mvc().perform(post("/api/v1/acopiadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("", "12", "rosa.api2@huata.pe")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unDniEscritoTieneQueTenerOchoDigitos() throws Exception {
        mvc().perform(post("/api/v1/acopiadores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("1234567", "", "rosa.api3@huata.pe")))
                .andExpect(status().isBadRequest());
    }
}
