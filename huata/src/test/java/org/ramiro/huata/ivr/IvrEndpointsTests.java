package org.ramiro.huata.ivr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.liquidaciones.LiquidacionService;
import org.ramiro.huata.mensajeria.MensajeProveedorRepository;
import org.ramiro.huata.mensajeria.MensajeriaDtos;
import org.ramiro.huata.mensajeria.MensajeriaService;
import org.ramiro.huata.notificaciones.NotificacionRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.EstadoSolicitudZona;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.SolicitudCambioZonaService;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.EstadoVisita;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Contrato del IVR telefonico (ivr/README.md, seccion 5) contra la cadena de seguridad real: lo que
 * responde cada endpoint y, sobre todo, quien puede llamarlos.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = "huata.security.ivr.api-key=" + IvrEndpointsTests.CLAVE)
class IvrEndpointsTests {

    static final String CLAVE = "clave-ivr-de-prueba";

    @Autowired private WebApplicationContext contexto;
    @Autowired private ProductorRepository productores;
    @Autowired private RutaRepository rutas;
    @Autowired private RecojoRepository recojos;
    @Autowired private MensajeriaService mensajeria;
    @Autowired private MensajeProveedorRepository mensajes;
    @Autowired private SolicitudCambioZonaService solicitudes;
    @Autowired private NotificacionRepository notificacionesRepo;

    private MockMvc mvc;
    private Ruta rutaA;
    private Productor rosa;

    @BeforeEach
    void escenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(SecurityMockMvcConfigurers.springSecurity()).build();
        rutaA = ruta("Ruta IVR A", "ZONA IVR A");
        ruta("Ruta IVR B", "ZONA IVR B");
        rosa = proveedor("IVR-001", "Rosa Elena", "999 111 222", rutaA, EstadoProductor.ACTIVO);
    }

    private Ruta ruta(String nombre, String zona) {
        Ruta r = new Ruta();
        r.setNombre(nombre);
        r.setZona(zona);
        return rutas.save(r);
    }

    private Productor proveedor(String codigo, String nombres, String telefono, Ruta ruta, EstadoProductor estado) {
        Productor p = new Productor();
        p.setCodigo(codigo);
        p.setNombres(nombres);
        p.setApellidos("Mamani");
        p.setTelefono(telefono);
        p.setRuta(ruta);
        p.setZonaPrincipal(ruta.getZona());
        p.setEstado(estado);
        return productores.save(p);
    }

    private Recojo entrega(Productor p, double litros, EstadoVisita visita, EstadoRecojo estado) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(p);
        r.setFechaHora(Instant.now());
        r.setLitros(litros);
        r.setEstadoVisita(visita);
        r.setEstado(estado);
        return recojos.save(r);
    }

    private static MockHttpServletRequestBuilder conClave(MockHttpServletRequestBuilder peticion) {
        return peticion.header("X-Api-Key", CLAVE);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    // ---------------------------------------------------------------------------------- Acceso

    @Test
    void sinClaveOClaveIncorrectaResponde401() throws Exception {
        mvc.perform(get("/api/v1/ivr/salud")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ivr/salud").header("X-Api-Key", "otra")).andExpect(status().isUnauthorized());
        mvc.perform(conClave(get("/api/v1/ivr/salud"))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("OK"));
    }

    /** Ni un administrador con sesion: estos endpoints entregan datos de cualquier proveedor por su telefono. */
    @Test
    void unUsuarioConSesionWebNoPuedeUsarElIvr() throws Exception {
        mvc.perform(get("/api/v1/ivr/zonas").with(SecurityMockMvcRequestPostProcessors.user("admin@huata.pe")
                        .authorities(new SimpleGrantedAuthority(Permiso.PROVEEDORES_READ), new SimpleGrantedAuthority(Permiso.PAGOS_READ))))
                .andExpect(status().isForbidden());
    }

    /** La clave del IVR no abre nada fuera de /api/v1/ivr/**. */
    @Test
    void laClaveDelIvrNoAbreElRestoDeLaApi() throws Exception {
        mvc.perform(conClave(get("/api/v1/productores"))).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------------------ Identificar

    @Test
    void identificaPorLosUltimosNueveDigitosSinImportarElFormato() throws Exception {
        for (String numero : new String[] {"+51999111222", "51999111222", "999111222"}) {
            mvc.perform(conClave(json(post("/api/v1/ivr/identificar"), "{\"telefono\":\"" + numero + "\",\"canal\":\"ASTERISK\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.encontrado").value(true))
                    .andExpect(jsonPath("$.proveedorId").value(rosa.getId()))
                    .andExpect(jsonPath("$.nombre").value("Rosa"))
                    .andExpect(jsonPath("$.zonaActual").value("ZONA IVR A"))
                    .andExpect(jsonPath("$.puedeSolicitarZona").value(true))
                    .andExpect(jsonPath("$.texto").value("Hola, Rosa. Bienvenido a la línea de Huata Lácteos."));
        }
    }

    @Test
    void unNumeroDesconocidoNoSeIdentifica() throws Exception {
        mvc.perform(conClave(json(post("/api/v1/ivr/identificar"), "{\"telefono\":\"+51900000001\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.motivo").value("NO_REGISTRADO"));
    }

    /** Si el numero esta en dos fichas no se adivina: responder con los datos de otro seria peor. */
    @Test
    void unNumeroEnDosFichasNoSeIdentifica() throws Exception {
        proveedor("IVR-002", "Ana", "999 333 444", rutaA, EstadoProductor.ACTIVO);
        proveedor("IVR-003", "Bea", "999333444", rutaA, EstadoProductor.ACTIVO);

        mvc.perform(conClave(json(post("/api/v1/ivr/identificar"), "{\"telefono\":\"+51999333444\"}")))
                .andExpect(jsonPath("$.encontrado").value(false))
                .andExpect(jsonPath("$.motivo").value("VARIOS_PROVEEDORES"));
    }

    // ------------------------------------------------------------------------------- Litros hoy

    @Test
    void sumaSoloLaLecheRealmenteEntregadaHoy() throws Exception {
        entrega(rosa, 20.0, EstadoVisita.ACOPIADO, EstadoRecojo.SYNCED);
        entrega(rosa, 22.5, EstadoVisita.ACOPIADO, EstadoRecojo.SYNCED);
        entrega(rosa, 0.0, EstadoVisita.NO_ENTREGADO, EstadoRecojo.SYNCED);
        entrega(rosa, 30.0, EstadoVisita.ACOPIADO, EstadoRecojo.CANCELLED);

        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/litros-hoy")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.litros").value(42.5))
                .andExpect(jsonPath("$.entregas").value(2))
                .andExpect(jsonPath("$.texto").value("Hoy entregó 42 litros y medio."));
    }

    @Test
    void sinEntregasLoDiceClaro() throws Exception {
        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/litros-hoy")))
                .andExpect(jsonPath("$.litros").value(0))
                .andExpect(jsonPath("$.texto").value("Hoy todavía no tiene entregas registradas."));
    }

    @Test
    void unProveedorInexistenteResponde404() throws Exception {
        mvc.perform(conClave(get("/api/v1/ivr/proveedores/999999/litros-hoy"))).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------------------------ Cobro

    /** Ciclo jueves-miercoles y pago el viernes siguiente (RN-04, RN-05). */
    @Test
    void elCobroDiceElCicloYElViernesDePago() throws Exception {
        LocalDate[] ciclo = LiquidacionService.cicloActual();

        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/cobro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cicloDesde").value(ciclo[0].toString()))
                .andExpect(jsonPath("$.cicloHasta").value(ciclo[1].toString()))
                .andExpect(jsonPath("$.fechaPago").value(ciclo[1].plusDays(2).toString()))
                .andExpect(jsonPath("$.moneda").value("PEN"))
                .andExpect(jsonPath("$.estado").value("SIN_ENTREGAS"))
                .andExpect(jsonPath("$.texto").value("Esta semana todavía no tiene monto por cobrar."));
        assertThat(ciclo[0].getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        assertThat(ciclo[1].plusDays(2).getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
    }

    @Test
    void conEntregasElCobroEsUnEstimadoConSuMonto() throws Exception {
        entrega(rosa, 40.0, EstadoVisita.ACOPIADO, EstadoRecojo.SYNCED);

        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/cobro")))
                .andExpect(jsonPath("$.estado").value("ESTIMADO"))
                .andExpect(jsonPath("$.litros").value(40.0))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("Por esta semana lleva ")))
                .andExpect(jsonPath("$.texto").value(org.hamcrest.Matchers.containsString("Se paga el viernes")));
    }

    // ------------------------------------------------------------------------------- Comunicados

    @Test
    void losAvisosDeLaPlantaSeListanYAlEscucharlosQuedanLeidos() throws Exception {
        var aviso1 = mensajeria.enviarAProveedor(rosa.getId(), new MensajeriaDtos.EnviarMensajeRequest("Mañana no hay recojo."), "secretaria.demo@huata.pe");
        var aviso2 = mensajeria.enviarAProveedor(rosa.getId(), new MensajeriaDtos.EnviarMensajeRequest("El pago sale el viernes."), "secretaria.demo@huata.pe");

        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/comunicados")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.texto").value("Tiene 2 avisos."))
                .andExpect(jsonPath("$.comunicados[0].leido").value(false));

        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/comunicados/leidos"),
                        "{\"ids\":[" + aviso1.id() + "," + aviso2.id() + "],\"llamadaId\":\"llamada-1\"}")))
                .andExpect(status().isNoContent());

        assertThat(mensajes.findById(aviso1.id()).orElseThrow().getLeidoPorProveedorAt()).isNotNull();
        assertThat(mensajes.findById(aviso2.id()).orElseThrow().getLeidoPorProveedorAt()).isNotNull();
    }

    /** Un id de otra conversacion no se marca: el IVR solo da por leido lo de quien llama. */
    @Test
    void noMarcaLeidosLosAvisosDeOtroProveedor() throws Exception {
        Productor otro = proveedor("IVR-004", "Carla", "999 777 888", rutaA, EstadoProductor.ACTIVO);
        var ajeno = mensajeria.enviarAProveedor(otro.getId(), new MensajeriaDtos.EnviarMensajeRequest("Solo para Carla."), "secretaria.demo@huata.pe");

        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/comunicados/leidos"), "{\"ids\":[" + ajeno.id() + "]}")))
                .andExpect(status().isNoContent());

        assertThat(mensajes.findById(ajeno.id()).orElseThrow().getLeidoPorProveedorAt()).isNull();
    }

    @Test
    void sinAvisosLoDice() throws Exception {
        mvc.perform(conClave(get("/api/v1/ivr/proveedores/" + rosa.getId() + "/comunicados")))
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.texto").value("No tiene avisos nuevos."));
    }

    // ------------------------------------------------------------------------- Zonas y solicitud

    @Test
    void lasZonasSeNumeranParaElTecladoConSuAudio() throws Exception {
        mvc.perform(conClave(get("/api/v1/ivr/zonas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zonas[?(@.zona == 'ZONA IVR B')].audio").value("zona_zona_ivr_b"))
                .andExpect(jsonPath("$.zonas[0].opcion").value(1));
    }

    @Test
    void pedirCambioDeZonaCreaLaSolicitudYRepetirloNoLaDuplica() throws Exception {
        String cuerpo = "{\"zona\":\"zona ivr b\",\"llamadaId\":\"llamada-7\"}";

        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.texto").value("Listo. Su pedido para pasar a ZONA IVR B quedó registrado. La planta le avisará."));
        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), cuerpo)))
                .andExpect(status().isOk());

        var pendientes = solicitudes.deProductor(rosa.getId()).stream().filter(s -> s.getEstado() == EstadoSolicitudZona.PENDIENTE).toList();
        assertThat(pendientes).singleElement().satisfies(s -> {
            assertThat(s.getZonaSolicitada()).isEqualTo("ZONA IVR B");
            assertThat(s.getMotivo()).contains("teléfono");
        });
    }

    @Test
    void conUnPedidoPendienteNoSePideOtraZona() throws Exception {
        ruta("Ruta IVR C", "ZONA IVR C");
        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), "{\"zona\":\"ZONA IVR B\"}")))
                .andExpect(status().isCreated());

        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), "{\"zona\":\"ZONA IVR C\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_PENDIENTE"));
    }

    @Test
    void noSePideLaZonaEnLaQueYaEsta() throws Exception {
        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), "{\"zona\":\"ZONA IVR A\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MISMA_ZONA"));
    }

    @Test
    void unaZonaSinRutasNoSePuedePedir() throws Exception {
        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/solicitud-zona"), "{\"zona\":\"ZONA INVENTADA\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ZONA_NO_DISPONIBLE"));
    }

    /** Un retirado consulta, pero no pide cambio de zona. */
    @Test
    void unProveedorRetiradoNoPideCambioDeZona() throws Exception {
        Productor retirado = proveedor("IVR-005", "Dora", "999 555 666", rutaA, EstadoProductor.RETIRADO);

        mvc.perform(conClave(json(post("/api/v1/ivr/identificar"), "{\"telefono\":\"999555666\"}")))
                .andExpect(jsonPath("$.encontrado").value(true))
                .andExpect(jsonPath("$.puedeSolicitarZona").value(false));
        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + retirado.getId() + "/solicitud-zona"), "{\"zona\":\"ZONA IVR B\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NO_PERMITIDO"));
    }

    // -------------------------------------------------------------------------- Pedido de llamada

    @Test
    void laTeclaCeroDejaUnAvisoEnLaCampanaDeLaPlanta() throws Exception {
        long antes = notificacionesRepo.count();

        mvc.perform(conClave(json(post("/api/v1/ivr/proveedores/" + rosa.getId() + "/pedido-llamada"), "{\"llamadaId\":\"llamada-9\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Listo. Pedimos a la planta que lo llame."));

        assertThat(notificacionesRepo.count()).isEqualTo(antes + 1);
        assertThat(notificacionesRepo.findAll()).anySatisfy(n -> {
            assertThat(n.getTitulo()).isEqualTo("Pidió que lo llamen");
            assertThat(n.getMensaje()).contains("Rosa Elena Mamani").contains("IVR-001");
            assertThat(n.getEntidadId()).isEqualTo(rosa.getId());
        });
    }
}
