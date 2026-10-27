package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "logging.level.org.ramiro.huata.rutas.PuntosStompConfig=DEBUG")
class PuntosTiempoRealTests {
    private static final String USUARIO = "acopiador.demo@huata.pe";
    @LocalServerPort int puerto;
    @Autowired PuntoEstablecidoService puntos;
    @Autowired PuntoEstablecidoRepository repository;
    @Autowired AcopiadorRepository acopiadores;
    @Autowired JwtService jwt;
    @Autowired PuntosSocketAcceso acceso;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper json;
    @MockitoSpyBean PuntosTiempoRealPublisher publicador;

    private Long rutaId() {
        return acopiadores.findByUsuario_UsernameIgnoreCase(USUARIO).orElseThrow().getRuta().getId();
    }

    private PuntoEstablecidoRequest request(String nombre) {
        return new PuntoEstablecidoRequest(nombre, -15.65, -69.98, rutaId(), null, null, null);
    }

    @Test
    void publicaSoloDespuesDelCommitYNuncaTrasRollback() {
        var tx = new TransactionTemplate(transactionManager);
        clearInvocations(publicador);
        tx.executeWithoutResult(status -> {
            puntos.crear(request("Rollback WS"));
            verify(publicador, never()).publicar(any());
            status.setRollbackOnly();
        });
        verify(publicador, never()).publicar(any());
        var punto = tx.execute(status -> {
            var creado = puntos.crear(request("Commit WS"));
            verify(publicador, never()).publicar(any());
            return creado;
        });
        verify(publicador).publicar(any());
        repository.deleteById(punto.getId());
    }

    @Test
    void soloPermiteRutaPropiaYColaPrivadaSinComodines() {
        assertThat(acceso.permitido(USUARIO, "/topic/rutas/" + rutaId() + "/puntos")).isTrue();
        assertThat(acceso.permitido(USUARIO, "/user/queue/puntos")).isTrue();
        assertThat(acceso.permitido(USUARIO, "/topic/rutas/999999/puntos")).isFalse();
        assertThat(acceso.permitido(USUARIO, "/topic/rutas/*/puntos")).isFalse();
        assertThat(acceso.permitido(USUARIO, "/queue/puntos-otra-sesion")).isFalse();
        assertThat(acceso.permitido("no-existe", "/user/queue/puntos")).isFalse();
    }

    @Test
    void activarEditarDesactivarYEliminarLleganPorStompConVersionCreciente() throws Exception {
        var cliente = new WebSocketStompClient(new StandardWebSocketClient());
        var scheduler = new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.initialize();
        cliente.setTaskScheduler(scheduler);
        cliente.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(org.springframework.messaging.MessageHeaders headers) {
                return true; // Recibir el JSON como texto crudo para verificar el contrato del evento.
            }
        });
        var headers = new WebSocketHttpHeaders();
        headers.setBearerAuth(jwt.generateToken(USUARIO, Map.of()));
        StompSession sesion = null;
        Long puntoId = null;
        try {
            sesion = cliente.connectAsync("ws://localhost:" + puerto + "/api/ws/puntos", headers,
                    new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
            var eventos = new LinkedBlockingQueue<String>();
            var listo = new CompletableFuture<Void>();
            var subscribeHeaders = new StompHeaders();
            subscribeHeaders.setDestination("/topic/rutas/" + rutaId() + "/puntos");
            subscribeHeaders.setReceipt("ruta-lista");
            var capturar = new StompFrameHandler() {
                public Type getPayloadType(StompHeaders h) { return String.class; }
                public void handleFrame(StompHeaders h, Object payload) { eventos.add((String) payload); }
            };
            sesion.subscribe(subscribeHeaders, capturar).addReceiptTask(() -> listo.complete(null));
            listo.get(10, TimeUnit.SECONDS);
            var listoDirecto = new CompletableFuture<Void>();
            var privado = new StompHeaders();
            privado.setDestination("/user/queue/puntos");
            privado.setReceipt("directos-listos");
            sesion.subscribe(privado, capturar).addReceiptTask(() -> listoDirecto.complete(null));
            listoDirecto.get(10, TimeUnit.SECONDS);
            var creado = puntos.crear(request("Punto WS"));
            puntoId = creado.getId();
            long version = evento(eventos, "PUNTO_ACTIVADO");
            puntos.actualizar(puntoId, request("Nombre nuevo WS"));
            long editado = evento(eventos, "PUNTO_ACTUALIZADO");
            assertThat(editado).isGreaterThan(version);
            puntos.desactivar(puntoId);
            long desactivado = evento(eventos, "PUNTO_DESACTIVADO");
            assertThat(desactivado).isGreaterThan(editado);
            assertThat(puntos.deMiAcopiador(USUARIO)).extracting(PuntoEstablecidoResponse::id).doesNotContain(puntoId);
            assertThat(puntos.snapshotDeMiAcopiador(USUARIO).bajas())
                    .extracting(PuntoEstablecidoResponse::id).contains(puntoId);
            puntos.cambiarEstado(puntoId, true);
            long activado = evento(eventos, "PUNTO_ACTIVADO");
            assertThat(activado).isGreaterThan(desactivado);
            puntos.eliminar(puntoId);
            assertThat(evento(eventos, "PUNTO_ELIMINADO")).isGreaterThan(activado);
            final Long eliminadoId = puntoId;
            assertThatThrownBy(() -> puntos.cambiarEstado(eliminadoId, true))
                    .isInstanceOf(org.ramiro.huata.common.NotFoundException.class);
            repository.deleteById(puntoId);
            puntoId = null;
            var acopiador = acopiadores.findByUsuario_UsernameIgnoreCase(USUARIO).orElseThrow();
            Long otraRuta = acopiadores.findAll().stream().filter(a -> a.getRuta() != null)
                    .map(a -> a.getRuta().getId()).filter(id -> !id.equals(rutaId())).findFirst().orElseThrow();
            var directo = puntos.crear(new PuntoEstablecidoRequest("Punto directo WS", -15.65, -69.98,
                    otraRuta, acopiador.getId(), null, null));
            puntoId = directo.getId();
            evento(eventos, "PUNTO_ACTIVADO"); // Solo puede llegar por la cola privada.
        } finally {
            if (sesion != null && sesion.isConnected()) sesion.disconnect();
            cliente.stop();
            scheduler.shutdown();
            if (puntoId != null) repository.deleteById(puntoId);
        }
    }

    @Test
    void unaSuscripcionRealAOtraRutaEsRechazada() throws Exception {
        var cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setMessageConverter(new StringMessageConverter());
        var headers = new WebSocketHttpHeaders();
        headers.setBearerAuth(jwt.generateToken(USUARIO, Map.of()));
        var error = new CompletableFuture<String>();
        StompSession sesion = null;
        try {
            sesion = cliente.connectAsync("ws://localhost:" + puerto + "/api/ws/puntos", headers,
                    new StompSessionHandlerAdapter() {
                        @Override public Type getPayloadType(StompHeaders h) { return String.class; }
                        @Override public void handleFrame(StompHeaders h, Object payload) {
                            error.complete(h.getFirst("message"));
                        }
                    }).get(10, TimeUnit.SECONDS);
            sesion.subscribe("/topic/rutas/999999/puntos", new StompFrameHandler() {
                public Type getPayloadType(StompHeaders h) { return String.class; }
                public void handleFrame(StompHeaders h, Object payload) { throw new AssertionError("Ruta ajena recibida"); }
            });
            assertThat(error.get(10, TimeUnit.SECONDS)).isNotBlank();
        } finally {
            // Tras el ERROR el servidor cierra la conexion; isConnected() puede seguir en true un
            // instante y entonces disconnect() falla al escribir sobre un socket ya cerrado. Esa
            // carrera no es lo que se prueba aqui.
            try {
                if (sesion != null && sesion.isConnected()) sesion.disconnect();
            } catch (org.springframework.messaging.MessageDeliveryException ignorada) {
                // La conexion ya la cerro el servidor.
            }
            cliente.stop();
        }
    }

    private long evento(BlockingQueue<String> eventos, String tipo) throws Exception {
        String recibido = eventos.poll(10, TimeUnit.SECONDS);
        assertThat(recibido).as("Evento %s", tipo).isNotNull();
        var datos = json.readTree(recibido);
        assertThat(datos.get("tipo").asString()).isEqualTo(tipo);
        assertThat(datos.get("punto").has("rutaId")).isTrue();
        return datos.get("punto").get("version").asLong();
    }

    @Test
    void handshakeRechazaJwtInvalido() {
        var cliente = new WebSocketStompClient(new StandardWebSocketClient());
        var headers = new WebSocketHttpHeaders();
        headers.setBearerAuth("invalido");
        try {
            assertThatThrownBy(() -> cliente.connectAsync("ws://localhost:" + puerto + "/api/ws/puntos",
                    headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS)).isInstanceOf(Exception.class);
        } finally { cliente.stop(); }
    }
}
