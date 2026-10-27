package org.ramiro.huata.cuenta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.RolRepository;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * "Mi cuenta" contra la cadena de seguridad real: cada persona cambia lo suyo y nada más.
 *
 * <p>Cada prueba crea su propia cuenta (usuario al azar) para no depender del orden ni de las
 * cuentas demo, y para que el límite de intentos por cuenta no se cruce entre pruebas.
 */
@SpringBootTest
class CuentaTests {

    private static final String CLAVE = "ClaveVieja1";

    @Autowired private WebApplicationContext contexto;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private RolRepository roles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        }
        return mvc;
    }

    private Usuario crear(String rol) {
        Usuario u = new Usuario();
        u.setNombreCompleto("Persona Prueba");
        u.setUsername("cuenta-" + UUID.randomUUID().toString().substring(0, 8));
        u.setEmail(u.getUsername());
        u.setPassword(passwordEncoder.encode(CLAVE));
        u.setRoles(new java.util.LinkedHashSet<>(Set.of(roles.findByNombre(rol).orElseThrow())));
        return usuarios.saveAndFlush(u);
    }

    private String token(Usuario u) {
        return "Bearer " + jwtService.generateToken(u.getUsername(), Map.of());
    }

    private ResultActions enviar(String ruta, Usuario u, Object cuerpo) throws Exception {
        return mvc().perform(put("/api/v1/cuenta" + ruta)
                .header("Authorization", token(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    @Test
    void sinTokenNoHayCuenta() throws Exception {
        mvc().perform(get("/api/v1/cuenta")).andExpect(status().isUnauthorized());
    }

    @Test
    void devuelveLaCuentaDelTokenYQueCamposPuedeEditar() throws Exception {
        Usuario u = crear(Rol.PROVEEDOR);
        mvc().perform(get("/api/v1/cuenta").header("Authorization", token(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(u.getUsername()))
                .andExpect(jsonPath("$.puedeEditarNombre").value(false));
    }

    @Test
    void cambiaLaClaveSoloConLaActualCorrecta() throws Exception {
        Usuario u = crear(Rol.CALIDAD);
        enviar("/clave", u, Map.of("actual", "otra-cosa", "nueva", "ClaveNueva22"))
                .andExpect(status().isUnprocessableEntity());
        enviar("/clave", u, Map.of("actual", CLAVE, "nueva", "corta"))
                .andExpect(status().isUnprocessableEntity());
        enviar("/clave", u, Map.of("actual", CLAVE, "nueva", "ClaveNueva22")).andExpect(status().isNoContent());

        String guardada = usuarios.findById(u.getId()).orElseThrow().getPassword();
        assertThat(passwordEncoder.matches("ClaveNueva22", guardada)).isTrue();
    }

    @Test
    void demasiadosIntentosConLaClaveEquivocadaSeFrenan() throws Exception {
        Usuario u = crear(Rol.CALIDAD);
        for (int i = 0; i < 5; i++) {
            enviar("/clave", u, Map.of("actual", "mal" + i, "nueva", "ClaveNueva22"));
        }
        enviar("/clave", u, Map.of("actual", CLAVE, "nueva", "ClaveNueva22")).andExpect(status().isTooManyRequests());
    }

    @Test
    void cambiarElUsuarioEntregaUnTokenNuevoYElViejoDejaDeValer() throws Exception {
        Usuario u = crear(Rol.ACOPIADOR);
        String viejo = token(u);
        String nuevoUsuario = "nuevo-" + UUID.randomUUID().toString().substring(0, 8);

        String respuesta = enviar("/usuario", u, Map.of("clave", CLAVE, "nuevoUsuario", nuevoUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(nuevoUsuario))
                .andReturn().getResponse().getContentAsString();
        String tokenNuevo = objectMapper.readTree(respuesta).get("token").asText();

        mvc().perform(get("/api/v1/cuenta").header("Authorization", "Bearer " + tokenNuevo))
                .andExpect(status().isOk())
                // No es un correo: se muestra vacío, pero en la base sigue al usuario como al crearse.
                .andExpect(jsonPath("$.email").doesNotExist());
        assertThat(usuarios.findById(u.getId()).orElseThrow().getEmail()).isEqualTo(nuevoUsuario);
        mvc().perform(get("/api/v1/cuenta").header("Authorization", viejo)).andExpect(status().isUnauthorized());
    }

    @Test
    void noSePuedeTomarElUsuarioDeOtraPersona() throws Exception {
        Usuario u = crear(Rol.CALIDAD);
        Usuario otro = crear(Rol.CALIDAD);
        enviar("/usuario", u, Map.of("clave", CLAVE, "nuevoUsuario", otro.getUsername().toUpperCase()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void elProveedorCambiaSuTelefonoPeroNoSuNombre() throws Exception {
        Usuario u = crear(Rol.PROVEEDOR);
        enviar("/datos", u, Map.of("nombreCompleto", "Otro Nombre", "telefono", "987654321"))
                .andExpect(status().isUnprocessableEntity());
        enviar("/datos", u, Map.of("nombreCompleto", "Persona Prueba", "telefono", "987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("987654321"));
    }

    @Test
    void elPersonalInternoCambiaSuNombreYSeValidaElTelefono() throws Exception {
        Usuario u = crear(Rol.CALIDAD);
        enviar("/datos", u, Map.of("nombreCompleto", "Ana Quispe", "telefono", "12"))
                .andExpect(status().isUnprocessableEntity());
        enviar("/datos", u, Map.of("nombreCompleto", "Ana Quispe", "email", "ana@huata.pe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCompleto").value("Ana Quispe"))
                .andExpect(jsonPath("$.email").value("ana@huata.pe"));
    }
}
