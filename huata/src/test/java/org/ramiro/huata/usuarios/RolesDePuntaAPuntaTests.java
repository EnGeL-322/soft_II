package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Los roles de punta a punta: usuario creado como lo crea la pantalla de Usuarios, login real con
 * JWT y llamadas a los endpoints. {@link AutorizacionPorPermisoTests} prueba que cada endpoint exija
 * su permiso; aqui se prueba que lo que se asigna en la pantalla (rol, rol personalizado, extra o
 * quitado) es lo que de verdad deja hacer el backend.
 */
@SpringBootTest
@Transactional
class RolesDePuntaAPuntaTests {

    private static final String CLAVE = "Clave2026*";

    private static final String VENTA_VALIDA = "{\"loteId\":999999,\"cliente\":\"Cliente prueba\",\"unidades\":1,"
            + "\"tipoComprador\":\"PUBLICO_GENERAL\",\"condicion\":\"CONTADO\",\"medioCobro\":\"EFECTIVO\"}";

    /** Una sonda por permiso: la peticion y todo lo que exige (la clase y el metodo). */
    private record Sonda(String nombre, Set<String> exige, Supplier<MockHttpServletRequestBuilder> peticion) {}

    private static final List<Sonda> SONDAS = List.of(
            new Sonda("ver recojos", Set.of(Permiso.ACOPIO_READ), () -> get("/api/v1/recojos")),
            new Sonda("ver incidencias de calidad", Set.of(Permiso.CALIDAD_READ), () -> get("/api/v1/calidad/incidencias")),
            new Sonda("ver rutas", Set.of(Permiso.RUTAS_READ), () -> get("/api/v1/rutas")),
            new Sonda("ver zonas", Set.of(Permiso.RUTAS_READ, Permiso.ZONAS_READ), () -> get("/api/v1/zonas/asignaciones")),
            new Sonda("ver acopiadores", Set.of(Permiso.ACOPIADORES_READ), () -> get("/api/v1/acopiadores")),
            new Sonda("ver usuarios", Set.of(Permiso.USUARIOS_READ), () -> get("/api/v1/usuarios")),
            new Sonda("ver auditoria", Set.of(Permiso.AUDITORIA_READ), () -> get("/api/v1/auditoria")),
            new Sonda("ver plan de cuentas", Set.of(Permiso.CONTABILIDAD_READ), () -> get("/api/v1/contabilidad/plan-cuentas")),
            new Sonda("ver compras", Set.of(Permiso.COMPRAS_READ), () -> get("/api/v1/compras")),
            new Sonda("ver materias primas", Set.of(Permiso.INVENTARIO_READ), () -> get("/api/v1/inventario/materias-primas")),
            new Sonda("ver recetas", Set.of(Permiso.RECETAS_READ), () -> get("/api/v1/produccion/recetas")),
            new Sonda("ver trabajadores de produccion", Set.of(Permiso.PRODUCCION_READ), () -> get("/api/v1/produccion/trabajadores")),
            new Sonda("ver ventas", Set.of(Permiso.QUESOS_READ), () -> get("/api/v1/quesos/ventas")),
            new Sonda("registrar venta", Set.of(Permiso.QUESOS_READ, Permiso.VENTAS_REGISTRAR),
                    () -> post("/api/v1/quesos/ventas").contentType(MediaType.APPLICATION_JSON).content(VENTA_VALIDA)),
            new Sonda("cobrar venta", Set.of(Permiso.QUESOS_READ, Permiso.VENTAS_COBRAR),
                    () -> patch("/api/v1/quesos/ventas/999999/cobrada")),
            new Sonda("anular venta", Set.of(Permiso.QUESOS_READ, Permiso.VENTAS_ANULAR),
                    () -> post("/api/v1/quesos/ventas/999999/anular")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"prueba\"}")));

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private RolService rolService;

    private final ObjectMapper json = new ObjectMapper();

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

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- ayudas

    /** Las altas las hace un administrador; las peticiones luego van solo con el token. */
    private <T> T comoAdministrador(Supplier<T> accion) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin.pruebas@huata.pe", null, List.of()));
        try {
            return accion.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String crearUsuario(List<String> roles, List<String> conceder, List<String> quitar) {
        String username = "e2e" + System.nanoTime() + "@huata.pe";
        comoAdministrador(() -> usuarioService.crearDesdeAdministracion(
                new UsuarioRequest("Persona de prueba", username, username, "951000111", CLAVE, roles, conceder, quitar)));
        return username;
    }

    private JsonNode login(String username) throws Exception {
        String cuerpo = mockMvc().perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("username", username, "password", CLAVE))))
                .andReturn().getResponse().getContentAsString();
        JsonNode respuesta = json.readTree(cuerpo);
        assertThat(respuesta.hasNonNull("token")).as("login de %s: %s", username, cuerpo).isTrue();
        return respuesta;
    }

    private static Set<String> permisos(JsonNode login) {
        Set<String> permisos = new TreeSet<>();
        login.get("permisos").forEach(p -> permisos.add(p.asText()));
        return permisos;
    }

    private int estado(String token, Sonda sonda) throws Exception {
        return mockMvc().perform(sonda.peticion().get().header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    private static boolean pasa(int estado) {
        return estado != 401 && estado != 403;
    }

    private Sonda sonda(String nombre) {
        return SONDAS.stream().filter(s -> s.nombre().equals(nombre)).findFirst().orElseThrow();
    }

    // ---------------------------------------------------------------- roles predefinidos

    /**
     * Cada rol asignable desde Usuarios: el login devuelve exactamente los permisos estandar del rol, y
     * cada sonda pasa si y solo si el rol tiene lo que exige. Se juntan todos los desajustes en un solo
     * fallo para ver el panorama completo de una vez.
     */
    @Test
    void cadaRolPredefinidoHaceExactamenteLoQueDiceSuMatriz() throws Exception {
        List<String> desajustes = new ArrayList<>();
        List<String> roles = rolService.asignablesEnUsuarios().stream()
                .filter(r -> MatrizPermisos.POR_ROL.containsKey(r))
                .toList();
        assertThat(roles).contains(Rol.ADMINISTRADOR, Rol.VENTAS, Rol.SECRETARIA, Rol.GERENCIA, Rol.CALIDAD);

        for (String rol : roles) {
            Set<String> esperados = MatrizPermisos.POR_ROL.get(rol);
            JsonNode sesion = login(crearUsuario(List.of(rol), null, null));
            if (!permisos(sesion).equals(new TreeSet<>(esperados))) {
                desajustes.add(rol + ": login devolvio " + permisos(sesion) + " y la matriz dice " + new TreeSet<>(esperados));
            }
            String token = sesion.get("token").asText();
            for (Sonda s : SONDAS) {
                boolean debe = esperados.containsAll(s.exige());
                int estado = estado(token, s);
                if (pasa(estado) != debe) {
                    desajustes.add(rol + " / " + s.nombre() + ": esperaba " + (debe ? "pasar" : "403") + " y dio " + estado);
                }
            }
        }
        assertThat(desajustes).as("desajustes rol/endpoint").isEmpty();
    }

    // ---------------------------------------------------------------- accesos dinamicos

    @Test
    void unRolPersonalizadoConSoloCobrarSoloCobra() throws Exception {
        RolResponse cobrador = comoAdministrador(() -> rolService.crear(new RolRequest(
                "Cobrador " + System.nanoTime(), "Solo confirma cobros", List.of(Permiso.QUESOS_READ, Permiso.VENTAS_COBRAR))));
        String token = login(crearUsuario(List.of(cobrador.nombre()), null, null)).get("token").asText();

        assertThat(pasa(estado(token, sonda("cobrar venta")))).isTrue();
        assertThat(estado(token, sonda("registrar venta"))).isEqualTo(403);
        assertThat(estado(token, sonda("anular venta"))).isEqualTo(403);
        assertThat(estado(token, sonda("ver recojos"))).isEqualTo(403);
    }

    @Test
    void unPermisoExtraLeDaAUnaPersonaLoQueSuRolNoTiene() throws Exception {
        // Gerencia es solo lectura; a esta persona se le concede vender.
        String token = login(crearUsuario(List.of(Rol.GERENCIA), List.of(Permiso.VENTAS_REGISTRAR), null))
                .get("token").asText();

        assertThat(pasa(estado(token, sonda("registrar venta")))).isTrue();
        assertThat(estado(token, sonda("anular venta"))).isEqualTo(403);
    }

    @Test
    void unPermisoQuitadoLeImpideAUnaPersonaLoQueSuRolSiTiene() throws Exception {
        String token = login(crearUsuario(List.of(Rol.VENTAS), null, List.of(Permiso.VENTAS_ANULAR)))
                .get("token").asText();

        assertThat(estado(token, sonda("anular venta"))).isEqualTo(403);
        assertThat(pasa(estado(token, sonda("registrar venta")))).isTrue();
        assertThat(pasa(estado(token, sonda("cobrar venta")))).isTrue();
    }

    /** Los permisos no viajan en el JWT: editar el rol surte efecto sin volver a iniciar sesion. */
    @Test
    void editarElRolSurteEfectoConElMismoToken() throws Exception {
        String etiqueta = "Caja ventas " + System.nanoTime();
        RolResponse rol = comoAdministrador(() -> rolService.crear(new RolRequest(
                etiqueta, null, List.of(Permiso.QUESOS_READ, Permiso.VENTAS_REGISTRAR))));
        String token = login(crearUsuario(List.of(rol.nombre()), null, null)).get("token").asText();
        assertThat(estado(token, sonda("anular venta"))).isEqualTo(403);

        comoAdministrador(() -> rolService.actualizar(rol.id(), new RolRequest(
                etiqueta, null, List.of(Permiso.QUESOS_READ, Permiso.VENTAS_REGISTRAR, Permiso.VENTAS_ANULAR))));
        assertThat(pasa(estado(token, sonda("anular venta")))).isTrue();

        comoAdministrador(() -> rolService.actualizar(rol.id(), new RolRequest(
                etiqueta, null, List.of(Permiso.QUESOS_READ))));
        assertThat(estado(token, sonda("registrar venta"))).isEqualTo(403);
        assertThat(estado(token, sonda("anular venta"))).isEqualTo(403);
    }
}
