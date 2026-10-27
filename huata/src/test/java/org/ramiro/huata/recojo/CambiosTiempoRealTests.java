package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.security.JwtService;
import org.ramiro.huata.tiemporeal.CambiosWebSocketHandler;
import org.ramiro.huata.tiemporeal.ModulosDeCambio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Canal de cambios: lo que se guarda en cualquier modulo llega en vivo a quien puede verlo.
 *
 * <p><b>Sin {@code @Transactional} a proposito</b>, igual que {@code AcopioTiempoRealTests}: el aviso va
 * despues del commit y una transaccion de prueba que siempre revierte nunca lo dispararia.
 */
@SpringBootTest
@WithMockUser(username = "calidad.demo@huata.pe")
class CambiosTiempoRealTests {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate DIA = LocalDate.of(2036, 3, 12);

    @Autowired private CambiosWebSocketHandler canal;
    @Autowired private JwtService jwtService;
    @Autowired private RecojoService recojoService;
    @Autowired private RecojoRepository recojoRepository;
    @Autowired private RutaRepository rutaRepository;
    @Autowired private ProductorRepository productorRepository;
    @Autowired private AcopiadorRepository acopiadorRepository;
    @Autowired private TransactionTemplate transacciones;

    private final List<Long> recojosCreados = new ArrayList<>();
    private final List<Sesion> abiertas = new ArrayList<>();

    @AfterEach
    void limpiar() {
        abiertas.forEach(s -> canal.afterConnectionClosed(s.proxy, CloseStatus.NORMAL));
        abiertas.clear();
        recojosCreados.forEach(recojoRepository::deleteById);
        recojosCreados.clear();
    }

    @Test
    void elPersonalConPermisoSeEnteraDelRecojoSinRecargar() throws Exception {
        Sesion planta = conectar("calidad.demo@huata.pe");

        Recojo r = acopiar(otroProveedor(), 21.0);

        assertThat(planta.recibidos).anySatisfy(json -> assertThat(json)
                .contains("\"type\":\"CAMBIOS\"")
                .contains("\"modulo\":\"acopio\"")
                .contains("\"entidad\":\"Recojo\"")
                .contains("\"id\":" + r.getId()));
    }

    /** El aviso no lleva datos de negocio: la pantalla los vuelve a pedir por la API con sus permisos. */
    @Test
    void elAvisoNoLlevaLitrosNiDatosDelProveedor() throws Exception {
        Sesion planta = conectar("calidad.demo@huata.pe");
        Productor p = otroProveedor();

        acopiar(p, 33.5);

        assertThat(planta.recibidos).isNotEmpty().allSatisfy(json -> assertThat(json)
                .doesNotContain("33.5")
                .doesNotContain(p.getNombres())
                .doesNotContain("litros"));
    }

    @Test
    void elProveedorSeEnteraDeLoSuyoAunqueNoTengaAcopioRead() throws Exception {
        Sesion app = conectar("proveedor.demo@huata.pe");

        Recojo r = acopiar(proveedorDemo(), 12.0);

        assertThat(app.cerradaCon).isNull();
        assertThat(app.recibidos).anySatisfy(json -> assertThat(json).contains("\"id\":" + r.getId()));
    }

    @Test
    void elProveedorNoSeEnteraDeLosRecojosDeOtros() throws Exception {
        Sesion app = conectar("proveedor.demo@huata.pe");

        acopiar(otroProveedor(), 15.0);

        assertThat(app.recibidos).noneSatisfy(json -> assertThat(json).contains("\"entidad\":\"Recojo\""));
    }

    @Test
    void siLaTransaccionSeRevierteNoSeAvisaNada() throws Exception {
        Sesion planta = conectar("calidad.demo@huata.pe");
        Productor p = otroProveedor();

        transacciones.executeWithoutResult(estado -> {
            acopiarSinLimpiar(p, 8.0);
            estado.setRollbackOnly();
        });

        assertThat(planta.recibidos).noneSatisfy(json -> assertThat(json).contains("\"entidad\":\"Recojo\""));
    }

    @Test
    void sinTokenValidoSeCierraElCanal() throws Exception {
        Sesion intruso = new Sesion();
        abiertas.add(intruso);
        canal.handleMessage(intruso.proxy, new TextMessage("{\"type\":\"AUTH\",\"token\":\"no-es-un-jwt\"}"));

        assertThat(intruso.cerradaCon).isEqualTo(CloseStatus.POLICY_VIOLATION);
        acopiar(otroProveedor(), 5.0);
        assertThat(intruso.recibidos).isEmpty();
    }

    @Test
    void respondeAlLatido() throws Exception {
        Sesion planta = conectar("calidad.demo@huata.pe");
        canal.handleMessage(planta.proxy, new TextMessage("{\"type\":\"PING\"}"));
        assertThat(planta.recibidos).contains("{\"type\":\"PONG\"}");
    }

    @Test
    void losPuntosGpsYLosPaquetesSinModuloNoSeDifunden() {
        assertThat(ModulosDeCambio.de(org.ramiro.huata.rutas.TrazadoAcopiadorPunto.class)).isEmpty();
        assertThat(ModulosDeCambio.de(org.ramiro.huata.verificacion.CodigoVerificacion.class)).isEmpty();
        assertThat(ModulosDeCambio.de(Recojo.class)).get().extracting(ModulosDeCambio.Modulo::nombre).isEqualTo("acopio");
    }

    // ---------------------------------------------------------------- escenario

    private Sesion conectar(String username) throws Exception {
        Sesion sesion = new Sesion();
        abiertas.add(sesion);
        String token = jwtService.generateToken(username, Map.of());
        canal.handleMessage(sesion.proxy, new TextMessage("{\"type\":\"AUTH\",\"token\":\"" + token + "\"}"));
        assertThat(sesion.recibidos).contains("{\"type\":\"LISTO\"}");
        sesion.recibidos.clear();
        return sesion;
    }

    private Productor proveedorDemo() {
        return productorRepository.findByUsuario_UsernameIgnoreCase("proveedor.demo@huata.pe").orElseThrow();
    }

    private Productor otroProveedor() {
        Long propio = proveedorDemo().getId();
        return productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(p -> !p.getId().equals(propio))
                .filter(p -> recojoRepository.findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                                p.getId(), DIA.atStartOfDay(LIMA).toInstant(),
                                DIA.plusDays(1).atStartOfDay(LIMA).toInstant(),
                                org.ramiro.huata.recojo.EstadoRecojo.CANCELLED)
                        .isEmpty())
                .findFirst()
                .orElseThrow();
    }

    private Recojo acopiar(Productor p, double litros) {
        Recojo r = acopiarSinLimpiar(p, litros);
        recojosCreados.add(r.getId());
        return r;
    }

    private Recojo acopiarSinLimpiar(Productor p, double litros) {
        Ruta ruta = rutaRepository.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        Long acopiador = acopiadorRepository.findByActivoTrueOrderByNombresAsc().get(0).getId();
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), p.getId(), acopiador, ruta.getId(),
                DIA.atTime(6, 0).atZone(LIMA).toInstant(), litros, 6.0, FuenteRecojo.RUTA,
                null, null, null, null, EstadoVisita.ACOPIADO, null, null, null, null));
    }

    /** WebSocketSession de mentira (un proxy): anota lo que le envian y como la cierran. */
    private static final class Sesion {
        final String id = UUID.randomUUID().toString();
        final List<String> recibidos = new ArrayList<>();
        boolean abierta = true;
        CloseStatus cerradaCon;
        final WebSocketSession proxy = (WebSocketSession) Proxy.newProxyInstance(
                WebSocketSession.class.getClassLoader(), new Class<?>[] {WebSocketSession.class},
                (p, metodo, args) -> switch (metodo.getName()) {
                    case "getId" -> id;
                    case "isOpen" -> abierta;
                    case "sendMessage" -> {
                        recibidos.add(String.valueOf(((TextMessage) args[0]).getPayload()));
                        yield null;
                    }
                    case "close" -> {
                        abierta = false;
                        cerradaCon = args == null || args.length == 0 ? CloseStatus.NORMAL : (CloseStatus) args[0];
                        yield null;
                    }
                    case "getAttributes" -> Map.of();
                    case "hashCode" -> id.hashCode();
                    case "equals" -> p == args[0];
                    case "toString" -> "Sesion " + id;
                    default -> metodo.getReturnType() == int.class ? 0 : null;
                });
    }
}
