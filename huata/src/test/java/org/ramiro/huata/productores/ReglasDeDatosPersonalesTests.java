package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.DatosPersonales;
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
 * Reglas de forma de los datos personales del proveedor: el <b>codigo es el DNI</b>, el DNI son 8
 * digitos y un nombre no lleva numeros.
 *
 * <p><b>Por que hacia falta.</b> Antes no habia ninguna regla, y se nota en la base de desarrollo:
 * codigos como {@code "12"} y {@code "11222"}, documentos de 9 digitos, el mismo DNI repetido en
 * cuatro acopiadores y proveedores llamados {@code "r"}. Nada de eso lo rechazaba el sistema.
 *
 * <p>Se prueba en dos niveles a proposito, porque son dos barreras distintas: por HTTP (la anotacion
 * del request, que es lo que da el 400 con el mensaje al formulario) y por servicio (la regla de
 * negocio, que sigue aplicando si alguien llama al servicio desde una importacion o desde otro
 * servicio sin pasar por el controlador). Validar solo en el formulario no es validar — es la misma
 * idea que la seccion 0 aplica a la autorizacion.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe", authorities = {Permiso.PROVEEDORES_READ, Permiso.PROVEEDORES_WRITE})
class ReglasDeDatosPersonalesTests {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ProductorService productorService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RutaRepository rutaRepository;

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

    private Long rutaId() {
        return rutaRepository.findAll().get(0).getId();
    }

    /** El alta exige cuenta movil, asi que la solicitud la trae; el usuario se deriva del DNI. */
    private ProductorRequest solicitud(String dni, String nombres, String apellidos) {
        return new ProductorRequest(
                dni, nombres, apellidos, "951000111", rutaId(), null, null, null, null,
                "prov" + dni.trim() + "@huata.pe", "Huata2026*");
    }

    private String json(String dni, String nombres, String apellidos) {
        return "{\"documentoIdentidad\":\"" + dni + "\",\"nombres\":\"" + nombres + "\",\"apellidos\":\"" + apellidos
                + "\",\"rutaId\":" + rutaId() + ",\"username\":\"prov.http@huata.pe\",\"password\":\"Huata2026*\"}";
    }

    private void esperarRechazoHttp(String dni, String nombres, String apellidos) throws Exception {
        mockMvc().perform(post("/api/v1/productores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(dni, nombres, apellidos)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------- El codigo del proveedor es su DNI

    /** DNI opcional (pedido del cliente, 2026-10-24): sin el, el codigo se genera y no se repite. */
    @Test
    void sinDniSeCreaConCodigoGenerado() {
        Productor uno = productorService.crear(new ProductorRequest("", "Elsa", "Ramos", null, rutaId(), null, null,
                null, null, "sin.dni.uno@huata.pe", "Huata2026*"));
        Productor dos = productorService.crear(new ProductorRequest(null, "Rosa", "Mamani", null, rutaId(), null, null,
                null, null, "sin.dni.dos@huata.pe", "Huata2026*"));

        assertThat(uno.getDocumentoIdentidad()).isNull();
        assertThat(uno.getCodigo()).startsWith("PRV-");
        assertThat(dos.getCodigo()).startsWith("PRV-").isNotEqualTo(uno.getCodigo());
    }

    /** Sin DNI nace activo; y si despues se le escribe, el DNI pasa a ser su codigo. */
    @Test
    void sinDniNaceActivoYAlAgregarloPasaASerSuCodigo() {
        Productor p = productorService.crear(new ProductorRequest(null, "Elsa", "Ramos", null, rutaId(), null, null,
                null, null, "sin.dni.aprobar@huata.pe", "Huata2026*"));

        Productor conDni = productorService.actualizar(p.getId(), new ProductorRequest("40404049", "Elsa", "Ramos", null,
                rutaId(), null, null, null, null, "sin.dni.aprobar@huata.pe", null));

        assertThat(productorRepository.findById(p.getId()).orElseThrow().getEstado()).isEqualTo(EstadoProductor.ACTIVO);
        assertThat(conDni.getCodigo()).isEqualTo("40404049");
    }

    /** Lo pedido: el codigo no se escribe, se deriva del documento. Los dos quedan iguales. */
    @Test
    void elCodigoDelProveedorEsSuDni() {
        Productor creado = productorService.crear(solicitud("40404041", "Elsa", "Ramos Calsin"));

        assertThat(creado.getCodigo()).isEqualTo("40404041");
        assertThat(creado.getDocumentoIdentidad()).isEqualTo("40404041");
    }

    /**
     * Y al corregir el DNI se corrige el codigo: son el mismo dato. Si no, una ficha acabaria con el
     * codigo de un documento y el documento de otro, que es como aparecieron los codigos sueltos que
     * hay hoy en la base.
     */
    @Test
    void corregirElDniCorrigeTambienElCodigo() {
        Productor creado = productorService.crear(solicitud("40404042", "Elsa", "Ramos"));

        Productor actualizado = productorService.actualizar(
                creado.getId(), solicitud("40404043", "Elsa", "Ramos Calsin"));

        assertThat(actualizado.getCodigo()).isEqualTo("40404043");
        assertThat(actualizado.getDocumentoIdentidad()).isEqualTo("40404043");
    }

    /** Dos fichas para la misma persona parten su liquidacion en dos; el DNI no puede repetirse. */
    @Test
    void noSePuedeRegistrarDosVecesElMismoDni() {
        productorService.crear(solicitud("40404044", "Elsa", "Ramos"));

        assertThatThrownBy(() -> productorService.crear(solicitud("40404044", "Otra", "Persona")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("40404044");
    }

    /** Guardar una ficha sin cambiarle el DNI no puede chocar consigo misma. */
    @Test
    void guardarSinCambiarElDniNoChocaConsigoMismo() {
        Productor creado = productorService.crear(solicitud("40404045", "Elsa", "Ramos"));

        Productor actualizado = productorService.actualizar(
                creado.getId(), solicitud("40404045", "Elsa Maria", "Ramos"));

        assertThat(actualizado.getNombres()).isEqualTo("Elsa Maria");
    }

    /** Pero si no puede tomar el DNI de otra ficha que ya existe. */
    @Test
    void noSePuedeCambiarElDniAlDeOtroProveedor() {
        productorService.crear(solicitud("40404046", "Ana", "Quispe"));
        Productor otro = productorService.crear(solicitud("40404047", "Luis", "Mamani"));

        assertThatThrownBy(() -> productorService.actualizar(otro.getId(), solicitud("40404046", "Luis", "Mamani")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("40404046");
    }

    // ---------------------------------------------------------------- Reglas del DNI

    /**
     * 8 digitos exactos: ni 7 ni 9, ni letras, ni con guiones o espacios en medio. Vacio SI vale desde
     * 2026-10-24 (el DNI es opcional, ver sinDniSeCreaConCodigoGenerado); solo espacios no.
     */
    @ParameterizedTest(name = "DNI invalido: \"{0}\"")
    @ValueSource(strings = {"1234567", "123456789", "4040404a", "4040 404", "404-4040", "        "})
    void elDniInvalidoSeRechazaPorHttp(String dni) throws Exception {
        esperarRechazoHttp(dni, "Elsa", "Ramos");
    }

    /**
     * Y tambien en el servicio, no solo en el formulario: la regla tiene que aguantar una llamada que
     * no venga por HTTP.
     */
    @ParameterizedTest(name = "DNI invalido en servicio: \"{0}\"")
    @ValueSource(strings = {"1234567", "123456789", "4040404a"})
    void elDniInvalidoSeRechazaTambienEnElServicio(String dni) {
        assertThatThrownBy(() -> productorService.crear(solicitud(dni, "Elsa", "Ramos")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("8 dígitos");
    }

    @Test
    void elDniConEspaciosAlrededorSeGuardaLimpio() {
        Productor creado = productorService.crear(solicitud("  40404048  ", "Elsa", "Ramos"));

        assertThat(creado.getDocumentoIdentidad()).isEqualTo("40404048");
        assertThat(creado.getCodigo()).isEqualTo("40404048");
    }

    // ------------------------------------------------------------- Reglas del nombre

    /** Lo pedido: en el nombre no van numeros. Tampoco simbolos ni una sola letra. */
    @ParameterizedTest(name = "nombre invalido: \"{0}\"")
    @ValueSource(strings = {"Elsa2", "123", "r", "Elsa@", "Elsa_Ramos", "#Elsa", "4"})
    void unNombreConNumerosOSimbolosSeRechaza(String nombre) throws Exception {
        esperarRechazoHttp("40404049", nombre, "Ramos");
    }

    @ParameterizedTest(name = "apellido invalido: \"{0}\"")
    @ValueSource(strings = {"Ramos3", "9", "Ramos/Calsin", "Ramos%"})
    void unApellidoConNumerosOSimbolosSeRechaza(String apellido) throws Exception {
        esperarRechazoHttp("40404050", "Elsa", apellido);
    }

    /**
     * Los nombres reales de la zona tienen que seguir entrando: tildes, ñ, apellidos compuestos con
     * guion y apostrofo. Una regla demasiado estricta seria tan inservible como ninguna.
     */
    @ParameterizedTest(name = "nombre valido: \"{0}\"")
    @ValueSource(strings = {
        "Elsa", "José María", "Ñawpa", "Quispe Mamani", "Quispe-Mamani", "D'Angelo", "RAMOS CALSIN", "Ana Sofía"
    })
    void losNombresRealesDeLaZonaSiguenSiendoValidos(String nombre) {
        Productor creado = productorService.crear(solicitud("4040" + (5100 + nombre.length()), nombre, "Ramos"));

        assertThat(creado.getNombres()).isEqualTo(nombre);
    }

    @Test
    void elNombreSeGuardaSinEspaciosSobrantes() {
        Productor creado = productorService.crear(solicitud("40404060", "  Elsa  ", "  Ramos Calsin  "));

        assertThat(creado.getNombres()).isEqualTo("Elsa");
        assertThat(creado.getApellidos()).isEqualTo("Ramos Calsin");
    }

    // ------------------------------------------------------------- Regla del telefono

    @ParameterizedTest(name = "telefono invalido: \"{0}\"")
    @ValueSource(strings = {"12345", "abcdefghi", "951-000-11a"})
    void unTelefonoInvalidoSeRechaza(String telefono) throws Exception {
        mockMvc().perform(post("/api/v1/productores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentoIdentidad\":\"40404061\",\"nombres\":\"Elsa\",\"apellidos\":\"Ramos\","
                                + "\"telefono\":\"" + telefono + "\",\"rutaId\":" + rutaId()
                                + ",\"username\":\"prov.tel@huata.pe\",\"password\":\"Huata2026*\"}"))
                .andExpect(status().isBadRequest());
    }

    /** El telefono es opcional: muchos proveedores de la zona no tienen uno propio. */
    @Test
    void elTelefonoPuedeVenirVacio() {
        Productor creado = productorService.crear(new ProductorRequest(
                "40404062", "Elsa", "Ramos", null, rutaId(), null, null, null, null,
                "prov40404062@huata.pe", "Huata2026*"));

        assertThat(creado.getTelefono()).isNull();
        assertThat(productorRepository.findById(creado.getId())).isPresent();
    }

    // ------------------------------------------------- El enmascarado que protege el DNI

    /**
     * Consecuencia directa de que el codigo sea el DNI: el panel publico publica el codigo del
     * proveedor con incidencia (RF-49), y la seccion 30 y RNF-12 prohiben exponer el DNI sin login. El
     * enmascarado es lo que hace compatibles las dos cosas.
     */
    @Test
    void elDniSePublicaEnmascarado() {
        assertThat(DatosPersonales.enmascarar("40404040")).isEqualTo("****4040");
    }

    @Test
    void elEnmascaradoNoRevelaNadaDeUnValorCorto() {
        assertThat(DatosPersonales.enmascarar("12")).isEqualTo("**");
        assertThat(DatosPersonales.enmascarar(null)).isNull();
    }

    /** Un codigo heredado que no es un DNI tambien se enmascara: no se asume la forma del dato. */
    @Test
    void unCodigoHeredadoTambienSeEnmascara() {
        assertThat(DatosPersonales.enmascarar("DEMO-001")).isEqualTo("****-001");
    }
}
