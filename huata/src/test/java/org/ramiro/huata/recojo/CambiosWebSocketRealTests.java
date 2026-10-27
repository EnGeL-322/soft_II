package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * El canal de cambios de punta a punta, con un WebSocket de verdad contra el servidor levantado: la
 * ruta {@code /api/ws/cambios}, la seguridad ({@code permitAll} + AUTH) y el aviso por la red.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@WithMockUser(username = "calidad.demo@huata.pe")
class CambiosWebSocketRealTests {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @LocalServerPort private int puerto;
    @Autowired private JwtService jwtService;
    @Autowired private RecojoService recojoService;
    @Autowired private RecojoRepository recojoRepository;
    @Autowired private RutaRepository rutaRepository;
    @Autowired private ProductorRepository productorRepository;
    @Autowired private AcopiadorRepository acopiadorRepository;

    @Test
    void unNavegadorConectadoRecibeElAvisoAlGuardarseUnRecojo() throws Exception {
        BlockingQueue<String> llegados = new LinkedBlockingQueue<>();
        WebSocketSession sesion = new StandardWebSocketClient()
                .execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                        llegados.add(m.getPayload());
                    }
                }, new WebSocketHttpHeaders(), URI.create("ws://localhost:" + puerto + "/api/ws/cambios"))
                .get(5, TimeUnit.SECONDS);
        Long recojo = null;
        try {
            String token = jwtService.generateToken("calidad.demo@huata.pe", Map.of());
            sesion.sendMessage(new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
            assertThat(llegados.poll(5, TimeUnit.SECONDS)).isEqualTo("{\"type\":\"LISTO\"}");

            recojo = acopiar().getId();

            String aviso = llegados.poll(5, TimeUnit.SECONDS);
            assertThat(aviso).isNotNull()
                    .contains("\"type\":\"CAMBIOS\"")
                    .contains("\"entidad\":\"Recojo\"")
                    .contains("\"id\":" + recojo);
        } finally {
            sesion.close();
            if (recojo != null) recojoRepository.deleteById(recojo);
        }
    }

    private Recojo acopiar() {
        LocalDate dia = LocalDate.of(2037, 5, 20);
        Ruta ruta = rutaRepository.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        Productor p = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(x -> recojoRepository.findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                                x.getId(), dia.atStartOfDay(LIMA).toInstant(),
                                dia.plusDays(1).atStartOfDay(LIMA).toInstant(), EstadoRecojo.CANCELLED)
                        .isEmpty())
                .findFirst()
                .orElseThrow();
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), p.getId(),
                acopiadorRepository.findByActivoTrueOrderByNombresAsc().get(0).getId(), ruta.getId(),
                dia.atTime(6, 0).atZone(LIMA).toInstant(), 10.0, 6.0, FuenteRecojo.RUTA,
                null, null, null, null, EstadoVisita.ACOPIADO, null, null, null, null));
    }
}
