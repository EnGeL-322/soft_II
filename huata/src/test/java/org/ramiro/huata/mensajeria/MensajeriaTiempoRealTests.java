package org.ramiro.huata.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mensajeria en vivo: lo que escribe la planta le llega al proveedor sin recargar (y al reves), los
 * reintentos de la cola offline no duplican, y quien se reconecta recupera lo que se perdio.
 *
 * <p><b>Sin {@code @Transactional} a proposito</b>, igual que {@code AcopioTiempoRealTests}: la
 * difusion va en {@code AFTER_COMMIT} y dentro de una transaccion de prueba que siempre revierte nunca
 * se dispararia. Cada caso borra lo que escribio en {@link #limpiar()}.
 */
@SpringBootTest
@WithMockUser(username = "secretaria.demo@huata.pe")
class MensajeriaTiempoRealTests {

    private static final String PROVEEDOR = "proveedor.demo@huata.pe";
    private static final String SECRETARIA = "secretaria.demo@huata.pe";
    private static final String ADMIN = "admin@huata.pe";

    @Autowired private MensajeriaService service;
    @Autowired private MensajeriaWebSocketHandler canal;
    @Autowired private MensajeProveedorRepository mensajes;
    @Autowired private ConversacionProveedorRepository conversaciones;
    @Autowired private ProductorRepository productores;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private Productor proveedor;
    private Productor otroProveedor;
    private boolean otraConversacionExistia;
    private Long ultimoIdAntes;
    private final List<SesionFalsa> abiertas = new ArrayList<>();

    @BeforeEach
    void escenario() {
        proveedor = productores.findByUsuario_UsernameIgnoreCase(PROVEEDOR).orElseThrow();
        otroProveedor = productores.findByActivoTrueOrderByNombresAscApellidosAsc().stream()
                .filter(p -> !p.getId().equals(proveedor.getId()))
                .findFirst()
                .orElseThrow();
        otraConversacionExistia = conversaciones.findByProductor_Id(otroProveedor.getId()).isPresent();
        ultimoIdAntes = mensajes.findAll().stream().map(MensajeProveedor::getId).max(Long::compare).orElse(0L);
    }

    @AfterEach
    void limpiar() {
        abiertas.forEach(s -> canal.afterConnectionClosed(s, CloseStatus.NORMAL));
        abiertas.clear();
        mensajes.findAll().stream().filter(m -> m.getId() > ultimoIdAntes).forEach(mensajes::delete);
        if (!otraConversacionExistia) {
            conversaciones.findByProductor_Id(otroProveedor.getId()).ifPresent(conversaciones::delete);
        }
    }

    // ------------------------------------------------------------------------------------ ayudas

    private SesionFalsa conectar(String username) throws Exception {
        SesionFalsa sesion = new SesionFalsa();
        abiertas.add(sesion);
        String token = jwtService.generateToken(username, Map.of());
        canal.handleMessage(sesion, new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
        return sesion;
    }

    private void mandar(SesionFalsa sesion, String json) throws Exception {
        canal.handleMessage(sesion, new TextMessage(json));
    }

    private MensajeriaDtos.MensajeResponse plantaEscribe(String texto) {
        return service.enviarAProveedor(proveedor.getId(), new MensajeriaDtos.EnviarMensajeRequest(texto), SECRETARIA);
    }

    private MensajeriaDtos.MensajeResponse proveedorEscribe(String texto, String uuid) {
        return service.enviarComoProveedor(new MensajeriaDtos.EnviarMensajeRequest(texto, uuid), PROVEEDOR);
    }

    /** Lo que recibio la sesion, ya parseado y sin el LISTO del apreton de manos. */
    private List<JsonNode> eventos(SesionFalsa sesion, String tipo) {
        return sesion.recibidos.stream()
                .map(objectMapper::readTree)
                .filter(n -> tipo.equals(n.get("type").asString()))
                .toList();
    }

    // ----------------------------------------------------------------------------------- pruebas

    @Test
    void alAutenticarseRecibeListoConSuLado() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);
        SesionFalsa web = conectar(SECRETARIA);

        JsonNode listoApp = eventos(app, "LISTO").get(0);
        assertThat(listoApp.get("planta").asBoolean()).isFalse();
        assertThat(listoApp.get("productorId").asLong()).isEqualTo(proveedor.getId());
        assertThat(eventos(web, "LISTO").get(0).get("planta").asBoolean()).isTrue();
    }

    /** El caso del pedido: la secretaria escribe y el telefono del proveedor lo recibe al instante. */
    @Test
    void elMensajeDeLaPlantaLlegaEnVivoAlProveedorYATodaLaBandeja() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);
        SesionFalsa secretaria = conectar(SECRETARIA);
        SesionFalsa admin = conectar(ADMIN);

        MensajeriaDtos.MensajeResponse enviado = plantaEscribe("Mañana el recojo es a las 6.");

        JsonNode enApp = eventos(app, "MENSAJE").get(0).get("mensaje");
        assertThat(enApp.get("id").asLong()).isEqualTo(enviado.id());
        assertThat(enApp.get("cuerpo").asString()).isEqualTo("Mañana el recojo es a las 6.");
        assertThat(enApp.get("mio").asBoolean()).isFalse();
        assertThat(enApp.get("estado").asString()).isEqualTo("ENVIADO");
        // Bandeja compartida: la llegan quien lo escribio (otra pestaña) y el resto de la planta.
        assertThat(eventos(secretaria, "MENSAJE").get(0).get("mensaje").get("mio").asBoolean()).isTrue();
        assertThat(eventos(admin, "MENSAJE").get(0).get("mensaje").get("mio").asBoolean()).isFalse();
    }

    @Test
    void elMensajeDelProveedorLlegaEnVivoALaPlanta() throws Exception {
        SesionFalsa web = conectar(SECRETARIA);

        proveedorEscribe("¿Cuándo pagan?", UUID.randomUUID().toString());

        JsonNode mensaje = eventos(web, "MENSAJE").get(0).get("mensaje");
        assertThat(mensaje.get("productorId").asLong()).isEqualTo(proveedor.getId());
        assertThat(mensaje.get("mio").asBoolean()).isFalse();
    }

    /** Un proveedor no escucha conversaciones ajenas, aunque este conectado al mismo canal. */
    @Test
    void elProveedorNoRecibeLaConversacionDeOtro() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);
        SesionFalsa web = conectar(SECRETARIA);

        service.enviarAProveedor(otroProveedor.getId(), new MensajeriaDtos.EnviarMensajeRequest("Solo para el otro"), SECRETARIA);

        assertThat(eventos(app, "MENSAJE")).isEmpty();
        assertThat(eventos(web, "MENSAJE")).hasSize(1);
    }

    /**
     * La cola offline reintenta si el envio se corta a medio camino. El mismo clientUuid es el mismo
     * mensaje: no se guarda dos veces ni se difunde dos veces.
     */
    @Test
    void elMismoClientUuidNoDuplica() throws Exception {
        SesionFalsa web = conectar(SECRETARIA);
        String uuid = UUID.randomUUID().toString();

        MensajeriaDtos.MensajeResponse primero = proveedorEscribe("Llevo 40 litros", uuid);
        MensajeriaDtos.MensajeResponse reintento = proveedorEscribe("Llevo 40 litros", uuid);

        assertThat(reintento.id()).isEqualTo(primero.id());
        assertThat(reintento.clientUuid()).isEqualTo(uuid);
        assertThat(mensajes.findAll().stream().filter(m -> uuid.equals(m.getClientUuid()))).hasSize(1);
        assertThat(eventos(web, "MENSAJE")).hasSize(1);
    }

    /** Reusar el uuid de un mensaje ajeno no devuelve ese mensaje: seria leer lo que no le toca. */
    @Test
    void unClientUuidAjenoSeRechaza() {
        String uuid = UUID.randomUUID().toString();
        proveedorEscribe("Mio", uuid);

        assertThatThrownBy(() -> service.enviarAProveedor(proveedor.getId(),
                new MensajeriaDtos.EnviarMensajeRequest("Otro", uuid), SECRETARIA))
                .isInstanceOf(BusinessRuleException.class);
    }

    /** Quien estuvo sin conexion pide lo posterior a su ultimo id y recibe solo eso, en orden. */
    @Test
    void alReconectarRecibeLoQueSePerdioSinDuplicados() throws Exception {
        MensajeriaDtos.MensajeResponse visto = plantaEscribe("Este ya lo tenia");
        MensajeriaDtos.MensajeResponse perdido1 = plantaEscribe("Perdido 1");
        MensajeriaDtos.MensajeResponse perdido2 = plantaEscribe("Perdido 2");
        service.enviarAProveedor(otroProveedor.getId(), new MensajeriaDtos.EnviarMensajeRequest("Ajeno"), SECRETARIA);

        SesionFalsa app = conectar(PROVEEDOR);
        mandar(app, "{\"type\":\"SYNC\",\"desdeId\":" + visto.id() + "}");

        JsonNode sync = eventos(app, "SYNC").get(0);
        JsonNode faltantes = sync.get("mensajes");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < faltantes.size(); i++) ids.add(faltantes.get(i).get("id").asLong());
        assertThat(ids).containsExactly(perdido1.id(), perdido2.id());
        assertThat(sync.get("completo").asBoolean()).isTrue();

        mandar(app, "{\"type\":\"SYNC\",\"desdeId\":" + perdido2.id() + "}");
        assertThat(eventos(app, "SYNC").get(1).get("mensajes").size()).isZero();
    }

    /** Los checks: entregado cuando llega al otro lado, leido cuando lo abre. El autor lo ve en vivo. */
    @Test
    void entregadoYLeidoLeLleganAlAutor() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);
        SesionFalsa web = conectar(SECRETARIA);
        MensajeriaDtos.MensajeResponse enviado = proveedorEscribe("Hola planta", UUID.randomUUID().toString());

        mandar(web, "{\"type\":\"ENTREGADO\",\"ids\":[" + enviado.id() + "]}");

        JsonNode entregado = eventos(app, "ESTADO").get(0);
        assertThat(entregado.get("estado").asString()).isEqualTo("ENTREGADO");
        assertThat(entregado.get("ids").get(0).asLong()).isEqualTo(enviado.id());

        service.conversacionDeProveedor(proveedor.getId(), SECRETARIA);

        JsonNode leido = eventos(app, "ESTADO").get(1);
        assertThat(leido.get("estado").asString()).isEqualTo("LEIDO");
        assertThat(mensajes.findById(enviado.id()).orElseThrow().estado()).isEqualTo(EstadoMensaje.LEIDO);
    }

    /** El autor no puede marcar como entregado su propio mensaje, ni nadie uno de otra conversacion. */
    @Test
    void elAcuseIgnoraMensajesPropiosYAjenos() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);
        MensajeriaDtos.MensajeResponse propio = proveedorEscribe("Mio", UUID.randomUUID().toString());
        MensajeriaDtos.MensajeResponse ajeno = service.enviarAProveedor(otroProveedor.getId(),
                new MensajeriaDtos.EnviarMensajeRequest("Ajeno"), SECRETARIA);

        mandar(app, "{\"type\":\"ENTREGADO\",\"ids\":[" + propio.id() + "," + ajeno.id() + "]}");

        assertThat(eventos(app, "ESTADO")).isEmpty();
        assertThat(mensajes.findById(propio.id()).orElseThrow().getEntregadoAPlantaAt()).isNull();
        assertThat(mensajes.findById(ajeno.id()).orElseThrow().getEntregadoAProveedorAt()).isNull();
    }

    @Test
    void elLatidoRespondePong() throws Exception {
        SesionFalsa app = conectar(PROVEEDOR);

        mandar(app, "{\"type\":\"PING\"}");

        assertThat(eventos(app, "PONG")).hasSize(1);
    }

    /** /api/ws/** es permitAll: el AUTH es la unica puerta. Sin token valido no se escucha nada. */
    @Test
    void sinTokenValidoSeCierraYNoEscucha() throws Exception {
        SesionFalsa intruso = new SesionFalsa();
        abiertas.add(intruso);
        canal.handleMessage(intruso, new TextMessage("{\"type\":\"AUTH\",\"token\":\"no-es-un-jwt\"}"));

        assertThat(intruso.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);
        plantaEscribe("No deberia verlo");
        assertThat(intruso.recibidos).isEmpty();
    }

    /** Pedir un SYNC sin haberse autenticado tampoco sirve: el primer mensaje tiene que ser AUTH. */
    @Test
    void sinAutenticarNoSePuedeSincronizar() throws Exception {
        SesionFalsa intruso = new SesionFalsa();
        abiertas.add(intruso);

        mandar(intruso, "{\"type\":\"SYNC\",\"desdeId\":0}");

        assertThat(intruso.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);
        assertThat(intruso.recibidos).isEmpty();
    }

    /** Calidad no tiene mensajeria.read: su token es valido pero no le da la bandeja. */
    @Test
    void unaCuentaSinMensajeriaNoEscucha() throws Exception {
        SesionFalsa calidad = conectar("calidad.demo@huata.pe");

        assertThat(calidad.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);
        plantaEscribe("No deberia verlo");
        assertThat(calidad.recibidos).isEmpty();
    }

    /** Sesion de mentira que solo anota lo que le mandan. Mas barata que abrir un socket real. */
    private static final class SesionFalsa implements WebSocketSession {
        private final String id = UUID.randomUUID().toString();
        private final List<String> recibidos = new ArrayList<>();
        private boolean abierta = true;
        private CloseStatus cerradaCon;

        @Override public void sendMessage(WebSocketMessage<?> message) { recibidos.add(String.valueOf(message.getPayload())); }
        @Override public boolean isOpen() { return abierta; }
        @Override public void close() { close(CloseStatus.NORMAL); }
        @Override public void close(CloseStatus status) { abierta = false; cerradaCon = status; }
        @Override public String getId() { return id; }
        @Override public URI getUri() { return URI.create("ws://localhost/api/ws/mensajeria"); }
        @Override public HttpHeaders getHandshakeHeaders() { return new HttpHeaders(); }
        @Override public Map<String, Object> getAttributes() { return Map.of(); }
        @Override public Principal getPrincipal() { return null; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public String getAcceptedProtocol() { return null; }
        @Override public void setTextMessageSizeLimit(int messageSizeLimit) {}
        @Override public int getTextMessageSizeLimit() { return 0; }
        @Override public void setBinaryMessageSizeLimit(int messageSizeLimit) {}
        @Override public int getBinaryMessageSizeLimit() { return 0; }
        @Override public List<WebSocketExtension> getExtensions() { return List.of(); }
    }
}
