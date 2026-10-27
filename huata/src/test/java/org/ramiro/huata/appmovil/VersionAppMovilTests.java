package org.ramiro.huata.appmovil;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** La app consulta su versión antes del login: tiene que responder sin token. */
@SpringBootTest
@TestPropertySource(properties = {
    "huata.app-movil.version-actual=1.3.0",
    "huata.app-movil.version-minima=1.1.0",
    "huata.app-movil.url-descarga=https://huata.example/descargas/huata.apk",
    "huata.app-movil.notas="
})
class VersionAppMovilTests {

    @Autowired
    private WebApplicationContext contexto;

    @Test
    void respondeSinAutenticacionYOmiteLoQueNoSeConfiguro() throws Exception {
        MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build()
                .perform(get("/api/v1/public/app-movil/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionActual").value("1.3.0"))
                .andExpect(jsonPath("$.versionMinima").value("1.1.0"))
                .andExpect(jsonPath("$.urlDescarga").value("https://huata.example/descargas/huata.apk"))
                .andExpect(jsonPath("$.notas").doesNotExist());
    }
}
