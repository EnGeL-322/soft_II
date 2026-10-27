package org.ramiro.huata.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.security.SecurityUserDetailsService;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.ramiro.huata.usuarios.Usuarios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El login: {@code POST /api/v1/auth/login} y {@code GET /api/v1/auth/me}.
 *
 * <p><b>Por que existe.</b> Es la primera linea de "Pruebas minimas antes de produccion" (CLAUDE.md
 * seccion 31) y el paquete {@code auth} estaba al 0 % de cobertura: la autorizacion si estaba
 * probada ({@link org.ramiro.huata.usuarios.AutorizacionPorPermisoTests}), pero la puerta de entrada
 * no. Un fallo aqui no degrada una pantalla, deja a todo el mundo fuera — o peor, deja entrar a quien
 * no debe.
 *
 * <p>Va contra la cadena de filtros real (MockMvc sobre el contexto) y contra el
 * {@code PasswordEncoder} real, no contra mocks: lo que se verifica es que una credencial correcta
 * abra y una incorrecta no, y eso no significa nada si el que compara la contrasena es un doble.
 */
@SpringBootTest
class LoginTests {

    /** Contrasena con la que {@code DemoDataSeeder} crea las cuentas de prueba. */
    private static final String CLAVE_DEMO = "Huata2026*";

    private static final String ADMIN = "admin@huata.pe";
    private static final String CALIDAD = "calidad.demo@huata.pe";

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SecurityUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

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

    private static String cuerpo(String usuario, String clave) {
        return "{\"username\":\"" + usuario + "\",\"password\":\"" + clave + "\"}";
    }

    private JsonNode login(String usuario, String clave) throws Exception {
        MvcResult resultado = mockMvc()
                .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo(usuario, clave)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------------------ Entrar

    @Test
    void conCredencialesCorrectasDevuelveTokenIdentidadYPermisos() throws Exception {
        JsonNode respuesta = login(ADMIN, CLAVE_DEMO);

        assertThat(respuesta.get("token").asString()).isNotBlank();
        assertThat(respuesta.get("username").asString()).isEqualTo(ADMIN);
        assertThat(respuesta.get("nombreCompleto").asString()).isNotBlank();
        assertThat(respuesta.get("roles").isArray()).isTrue();
        assertThat(respuesta.get("permisos").isEmpty()).isFalse();
    }

    /** El token que sale del login tiene que servir de verdad: firmado, vigente y a nombre de quien entro. */
    @Test
    void elTokenEmitidoEsValidoYVaANombreDeQuienEntro() throws Exception {
        String token = login(ADMIN, CLAVE_DEMO).get("token").asString();

        assertThat(jwtService.extractUsername(token)).isEqualTo(ADMIN);
        assertThat(jwtService.isValid(token, ADMIN)).isTrue();
        // Y no sirve para hacerse pasar por otro, aunque ese otro exista.
        assertThat(jwtService.isValid(token, CALIDAD)).isFalse();
    }

    /**
     * El repositorio busca con {@code IgnoreCase}, asi que escribir el correo con mayusculas no
     * puede dejar a nadie fuera. Es el tipo de detalle que se rompe sin que ninguna pantalla avise.
     */
    @Test
    void elUsuarioNoDistingueMayusculasDeMinusculas() throws Exception {
        JsonNode respuesta = login(ADMIN.toUpperCase(), CLAVE_DEMO);

        assertThat(respuesta.get("username").asString()).isEqualTo(ADMIN);
    }

    // ------------------------------------------------------------------------------ No entrar

    @Test
    void conLaContrasenaEquivocadaNoEntraYNoSeFiltraNingunToken() throws Exception {
        MvcResult resultado = mockMvc()
                .perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(ADMIN, "otra-cosa")))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("token");
    }

    @Test
    void unUsuarioQueNoExisteRecibe401YNoUn500() throws Exception {
        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("nadie@huata.pe", CLAVE_DEMO)))
                .andExpect(status().isUnauthorized());
    }

    /** Dar de baja una cuenta tiene que cerrarle la puerta, no solo esconderle el menu. */
    @Test
    @Transactional
    void unaCuentaInactivaNoPuedeEntrar() throws Exception {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(CALIDAD).orElseThrow();
        usuario.setActivo(false);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(CALIDAD, CLAVE_DEMO)))
                .andExpect(status().isUnauthorized());
    }

    /** Sin cuerpo valido, 400 de validacion; no un 500 ni un 401 enganoso. */
    @Test
    void unaPeticionSinUsuarioNiContrasenaEsUnErrorDeValidacion() throws Exception {
        mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------- Las dos propiedades que sostienen el modelo

    /**
     * Los permisos que devuelve el login son <b>exactamente</b> las autoridades de recurso con las que
     * el backend autoriza. Es el invariante por el que existe {@link Usuarios#permisosDe}: si
     * divergieran, el menu mostraria botones que la API rechaza, o esconderia cosas que si se pueden
     * hacer.
     *
     * <p>Se descartan las autoridades {@code ROLE_*} a proposito: son la otra familia, la de identidad,
     * y no autorizan ningun endpoint (ver {@link org.ramiro.huata.security.SecurityUserDetailsService}
     * y la prueba de que ROLE_ADMINISTRADOR por si solo no abre nada).
     */
    @Test
    void losPermisosDelLoginSonLosMismosConLosQueAutorizaElBackend() throws Exception {
        List<String> delLogin = objectMapper.convertValue(
                login(CALIDAD, CLAVE_DEMO).get("permisos"), new tools.jackson.core.type.TypeReference<List<String>>() {});

        List<String> conLosQueAutoriza = userDetailsService.loadUserByUsername(CALIDAD).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> !autoridad.startsWith("ROLE_"))
                .sorted()
                .toList();

        assertThat(delLogin).isEqualTo(conLosQueAutoriza).isNotEmpty();
    }

    /**
     * Los permisos NO viajan dentro del JWT. Es una decision de seguridad documentada en
     * {@link AuthController}: se resuelven contra la base en cada peticion, para que quitarle un
     * permiso a alguien surta efecto de inmediato. Si un dia alguien los metiera como claim, la
     * revocacion dejaria de funcionar hasta que expirara el token y nadie se enteraria — por eso esto
     * se prueba y no solo se comenta.
     */
    @Test
    void elTokenNoLlevaLosPermisosDentro() throws Exception {
        String token = login(CALIDAD, CLAVE_DEMO).get("token").asString();

        String cargaUtil = new String(
                java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(cargaUtil).doesNotContain(Permiso.CALIDAD_WRITE).doesNotContain("permisos");
        // Lo que si lleva es identidad: sujeto, roles y nombre, que es lo que no cambia sin re-login.
        assertThat(cargaUtil).contains(Rol.CALIDAD).contains("nombre");
    }

    // ------------------------------------------------------------------------------ /me

    /**
     * {@code /me} sirve para que el frontend recupere la sesion al recargar. Devuelve la misma
     * identidad y permisos que el login pero <b>sin token</b>: si emitiera uno, seria una renovacion
     * silenciosa e indefinida de la sesion.
     */
    @Test
    void meDevuelveLaMismaIdentidadPeroSinEmitirUnTokenNuevo() throws Exception {
        JsonNode delLogin = login(CALIDAD, CLAVE_DEMO);

        MvcResult resultado = mockMvc()
                .perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + delLogin.get("token").asString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode yo = objectMapper.readTree(resultado.getResponse().getContentAsString());

        assertThat(yo.get("username").asString()).isEqualTo(CALIDAD);
        assertThat(yo.get("permisos")).isEqualTo(delLogin.get("permisos"));
        // Con default-property-inclusion=non_null, un token nulo no aparece en el JSON.
        assertThat(yo.has("token")).isFalse();
    }

    @Test
    void meSinSesionResponde401() throws Exception {
        mockMvc().perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    /** Un token manipulado no abre /me: la firma se verifica, no solo se lee el contenido. */
    @Test
    void meConUnTokenAlteradoResponde401() throws Exception {
        String token = login(ADMIN, CLAVE_DEMO).get("token").asString();

        mockMvc().perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------ Trazabilidad

    /**
     * La contrasena se guarda cifrada. Es lo minimo, y justamente por eso conviene que quede fijado:
     * si un dia alguien sembrara o migrara cuentas en claro, el login seguiria funcionando y nada
     * fallaria.
     */
    @Test
    void lasContrasenasNoSeGuardanEnClaro() {
        Usuario usuario = usuarioRepository.findByUsernameIgnoreCaseAndActivoTrue(ADMIN).orElseThrow();

        assertThat(usuario.getPassword()).isNotEqualTo(CLAVE_DEMO);
        assertThat(passwordEncoder.matches(CLAVE_DEMO, usuario.getPassword())).isTrue();
    }
}
