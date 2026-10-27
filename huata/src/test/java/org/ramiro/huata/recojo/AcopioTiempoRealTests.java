package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.tiemporeal.AcopioTiempoRealHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * El acopio registrado desde la app movil llega a "Conciliacion planta" sin recargar la pantalla.
 *
 * <p><b>Sin {@code @Transactional} a proposito.</b> La difusion va en {@code AFTER_COMMIT}: dentro de
 * una transaccion de prueba que siempre revierte nunca se dispararia, y la prueba pasaria sin probar
 * nada. Por eso cada caso limpia lo que escribio en {@link #limpiar()}.
 */
@SpringBootTest
@WithMockUser(username = "calidad.demo@huata.pe")
class AcopioTiempoRealTests {

    // Un recojo en ruta exige el acopiador que lo hizo (RecojoService, 2026-10-14).
    @org.springframework.beans.factory.annotation.Autowired
    private org.ramiro.huata.acopiadores.AcopiadorRepository acopiadoresDePrueba;

    private Long acopiadorDePrueba() {
        return acopiadoresDePrueba.findByActivoTrueOrderByNombresAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    var a = new org.ramiro.huata.acopiadores.Acopiador();
                    a.setNombres("Acopiador");
                    a.setApellidos("De prueba");
                    return acopiadoresDePrueba.save(a);
                })
                .getId();
    }


    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA = LocalDate.of(2035, 8, 14);

    @Autowired
    private RecojoService recojoService;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ConciliacionRutaService conciliacionService;

    @Autowired
    private ConciliacionRutaRepository conciliacionRepository;

    @Autowired
    private AcopioTiempoRealHandler canal;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private ProductorRepository productorRepository;

    private Ruta ruta;
    private Productor proveedor;
    private final List<Long> recojosCreados = new ArrayList<>();
    private final List<SesionFalsa> abiertas = new ArrayList<>();

    @BeforeEach
    void escenario() {
        ruta = rutaRepository.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        proveedor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(p -> recojoRepository
                        .findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                                p.getId(),
                                DIA.atStartOfDay(LIMA).toInstant(),
                                DIA.plusDays(1).atStartOfDay(LIMA).toInstant(),
                                EstadoRecojo.CANCELLED)
                        .isEmpty())
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void limpiar() {
        recojosCreados.forEach(recojoRepository::deleteById);
        recojosCreados.clear();
        conciliacionRepository.findByFechaAndRuta_Id(DIA, ruta.getId()).ifPresent(conciliacionRepository::delete);
        abiertas.forEach(s -> canal.afterConnectionClosed(s, CloseStatus.NORMAL));
        abiertas.clear();
    }

    /** Un navegador que se presenta con el JWT de una cuenta con acopio.read. */
    private SesionFalsa oyenteAutenticado(String username) throws Exception {
        SesionFalsa sesion = new SesionFalsa();
        abiertas.add(sesion);
        String token = jwtService.generateToken(username, Map.of());
        canal.handleMessage(sesion, new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
        return sesion;
    }

    private Recojo acopiar(double litros) {
        Recojo r = recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(),
                proveedor.getId(),
                acopiadorDePrueba(),
                ruta.getId(),
                DIA.atTime(5, 30).atZone(LIMA).toInstant(),
                litros,
                6.2,
                FuenteRecojo.RUTA,
                null,
                null,
                null,
                null,
                EstadoVisita.ACOPIADO,
                null,
                null,
                null,
                null));
        recojosCreados.add(r.getId());
        return r;
    }

    /** El caso del pedido: el acopiador registra en el camino y la web se enteran sin recargar. */
    @Test
    void elAcopioRegistradoEnRutaLlegaAlCanalConLaFilaRecalculada() throws Exception {
        SesionFalsa oyente = oyenteAutenticado("calidad.demo@huata.pe");

        acopiar(18.5);

        assertThat(oyente.recibidos).hasSize(1);
        assertThat(oyente.recibidos.get(0))
                .contains("\"tipo\":\"RECOJO\"")
                .contains("\"rutaId\":" + ruta.getId())
                .contains("\"litrosEnRuta\":18.5")
                .contains(proveedor.getCodigo());
    }

    /**
     * Las horas viajan como texto ISO-8601 y no como numero de epoch.
     *
     * <p>No es cosmetico: la pantalla las formatea con el pipe {@code date} de Angular, que con un
     * numero lo interpreta como milisegundos. Si Jackson pasara a escribir el {@code Instant} como
     * marca de tiempo (segundos con decimales), la columna "ultimo acopio" y las horas del detalle
     * mostrarian 1970 sin que fallara nada. Se comprueba sobre el ObjectMapper real de la aplicacion,
     * que es el mismo que usa el canal.
     */
    @Test
    void lasHorasDelMensajeVanEnIso8601YNoComoNumero() throws Exception {
        SesionFalsa oyente = oyenteAutenticado("calidad.demo@huata.pe");

        acopiar(11.0);

        String json = oyente.recibidos.get(0);
        // 05:30 de Lima son las 10:30Z; basta con que sea texto con forma de fecha, no un numero.
        assertThat(json).containsPattern("\"fechaHora\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\"");
        assertThat(json).containsPattern("\"ultimoAcopio\":\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\"");
    }

    /** El total que anota planta tambien viaja: es la otra mitad de la comparacion. */
    @Test
    void elTotalRecibidoEnPlantaTambienSeDifunde() throws Exception {
        acopiar(50.0);
        SesionFalsa oyente = oyenteAutenticado("calidad.demo@huata.pe");

        conciliacionService.registrar(new ConciliacionRutaRequest(DIA, ruta.getId(), null, 48.0, null));

        assertThat(oyente.recibidos).hasSize(1);
        assertThat(oyente.recibidos.get(0))
                .contains("\"tipo\":\"PLANTA\"")
                .contains("\"litrosRecibidosPlanta\":48.0")
                .contains("\"diferencia\":2.0");
    }

    /**
     * El canal es de salida: quien no presenta un token valido con {@code acopio.read} se queda
     * fuera. Ocultar la pantalla en Angular no impide abrir un WebSocket a mano.
     */
    @Test
    void sinTokenValidoNoSeEscuchaNada() throws Exception {
        SesionFalsa intruso = new SesionFalsa();
        abiertas.add(intruso);
        canal.handleMessage(intruso, new TextMessage("{\"type\":\"AUTH\",\"token\":\"no-es-un-jwt\"}"));

        assertThat(intruso.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);

        acopiar(12.0);
        assertThat(intruso.recibidos).isEmpty();
    }

    /**
     * PROVEEDOR no tiene ningun permiso (su superficie es el portal publico), asi que no puede
     * escuchar el litraje de las rutas aunque su token sea valido.
     */
    @Test
    void unaCuentaSinAcopioReadNoEscuchaElCanal() throws Exception {
        SesionFalsa proveedorApp = oyenteAutenticado("proveedor.demo@huata.pe");

        assertThat(proveedorApp.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);

        acopiar(9.0);
        assertThat(proveedorApp.recibidos).isEmpty();
    }

    /** Una sesion ya cerrada se descarta sola; no deja al canal intentando escribir en ella. */
    @Test
    void laSesionCerradaSeDescartaAlPublicar() throws Exception {
        SesionFalsa oyente = oyenteAutenticado("calidad.demo@huata.pe");
        assertThat(canal.oyentes()).isPositive();

        oyente.abierta = false;
        acopiar(7.0);

        assertThat(oyente.recibidos).isEmpty();
        assertThat(canal.oyentes()).isZero();
    }

    /** Sesion de mentira que solo anota lo que le mandan. Mas barata que abrir un socket real. */
    private static final class SesionFalsa implements WebSocketSession {
        private final String id = UUID.randomUUID().toString();
        private final List<String> recibidos = new ArrayList<>();
        private boolean abierta = true;
        private CloseStatus cerradaCon;

        @Override
        public void sendMessage(WebSocketMessage<?> message) {
            recibidos.add(String.valueOf(message.getPayload()));
        }

        @Override
        public boolean isOpen() {
            return abierta;
        }

        @Override
        public void close() {
            close(CloseStatus.NORMAL);
        }

        @Override
        public void close(CloseStatus status) {
            abierta = false;
            cerradaCon = status;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/api/ws/acopio");
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return new HttpHeaders();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {}

        @Override
        public int getTextMessageSizeLimit() {
            return 0;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {}

        @Override
        public int getBinaryMessageSizeLimit() {
            return 0;
        }

        @Override
        public List<WebSocketExtension> getExtensions() {
            return List.of();
        }
    }
}
