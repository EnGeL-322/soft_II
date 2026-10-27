package org.ramiro.huata.productores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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
 * Ubicacion y solicitud de cambio de zona llevan el id del proveedor en la URL. Antes bastaba con
 * estar autenticado, asi que un proveedor podia escribir sobre la ficha de otro cambiando el id.
 *
 * <p>Los cuerpos van validos a proposito: @Valid se evalua antes que @PreAuthorize, y un 400 haria
 * pasar la prueba sin haber llegado a la autorizacion.
 */
@SpringBootTest
@Transactional
class FichaPropiaDelProveedorTests {

    private static final String UBICACION = "{\"latitud\":-15.84,\"longitud\":-70.02,\"tipo\":\"ACTUAL\"}";
    private static final String SOLICITUD = "{\"zonaSolicitada\":\"COLLANA I\",\"motivo\":\"prueba\"}";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ProductorRepository productorRepository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private Long fichaDe(String username) {
        return productorRepository.findByUsuario_UsernameIgnoreCase(username).orElseThrow().getId();
    }

    private Long fichaAjena(String username) {
        Long propia = fichaDe(username);
        return productorRepository.findAll().stream()
                .map(Productor::getId)
                .filter(id -> !id.equals(propia))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @WithMockUser(username = "proveedor.demo@huata.pe")
    void unProveedorNoPuedeRegistrarLaUbicacionDeOtro() throws Exception {
        mockMvc().perform(post("/api/v1/productores/" + fichaAjena("proveedor.demo@huata.pe") + "/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "proveedor.demo@huata.pe")
    void unProveedorNoPuedePedirCambioDeZonaParaOtro() throws Exception {
        mockMvc().perform(post("/api/v1/productores/" + fichaAjena("proveedor.demo@huata.pe") + "/solicitudes-zona")
                        .contentType(MediaType.APPLICATION_JSON).content(SOLICITUD))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "proveedor.demo@huata.pe")
    void elProveedorVeLasZonasQuePuedePedirSoloEnSuFicha() throws Exception {
        mockMvc().perform(get("/api/v1/productores/" + fichaDe("proveedor.demo@huata.pe") + "/zonas-solicitables"))
                .andExpect(status().isOk());
        mockMvc().perform(get("/api/v1/productores/" + fichaAjena("proveedor.demo@huata.pe") + "/zonas-solicitables"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "proveedor.demo@huata.pe")
    void elProveedorSiPuedeRegistrarSuPropiaUbicacion() throws Exception {
        mockMvc().perform(post("/api/v1/productores/" + fichaDe("proveedor.demo@huata.pe") + "/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin.pruebas@huata.pe", authorities = {Permiso.PROVEEDORES_WRITE})
    void quienEditaProveedoresPuedeRegistrarLaUbicacionDeCualquiera() throws Exception {
        mockMvc().perform(post("/api/v1/productores/" + fichaAjena("proveedor.demo@huata.pe") + "/ubicaciones")
                        .contentType(MediaType.APPLICATION_JSON).content(UBICACION))
                .andExpect(status().isCreated());
    }
}
