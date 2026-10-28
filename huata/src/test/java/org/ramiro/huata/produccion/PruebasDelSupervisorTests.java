package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRequest;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.quesos.QueseriaService;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Lo que intentaria un ingeniero que revisa el sistema: datos que no deben entrar, nombres casi
 * iguales ("sal", "zal", "zall"), cantidades absurdas, ids que no existen, vender lo vencido o mas de
 * lo que hay. Va contra la API (no contra el servicio) para que cuenten tambien las validaciones y
 * la seguridad, y en todos los casos se exige un rechazo limpio: 4xx con mensaje, nunca un 500.
 */
@SpringBootTest
@Transactional
@WithMockUser(
        username = "admin@huata.pe",
        authorities = {
            Permiso.INVENTARIO_READ, Permiso.INVENTARIO_WRITE, Permiso.RECETAS_READ, Permiso.RECETAS_WRITE,
            Permiso.PRODUCCION_READ, Permiso.PRODUCCION_WRITE, Permiso.QUESOS_READ, Permiso.QUESOS_WRITE,
            Permiso.VENTAS_REGISTRAR, Permiso.COMPRAS_READ, Permiso.COMPRAS_WRITE
        })
class PruebasDelSupervisorTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired private WebApplicationContext contexto;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private RecetaQuesoRepository recetaRepository;
    @Autowired private InventarioService inventarioService;
    @Autowired private QueseriaService queseriaService;

    private MockMvc mvc;
    private String sufijo;
    private Producto producto;
    private RecetaQueso receta;
    private MateriaPrima insumo;

    @BeforeEach
    void escenario() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
        // Letras y no números: los nombres con números distintos nunca se consideran parecidos.
        sufijo = Long.toString(System.nanoTime(), 36).replaceAll("\\d", "q");
        insumo = new MateriaPrima();
        insumo.setNombre("Fermento " + sufijo);
        insumo.setUnidadMedida(UnidadMedida.KG);
        insumo.setCostoUnitarioReferencial(BigDecimal.ONE);
        insumo = materiaPrimaRepository.save(insumo);
        inventarioService.registrarEntradaCompra(insumo, new BigDecimal("10"), HOY.minusDays(3), 0L);

        producto = new Producto();
        producto.setNombre("Producto " + sufijo);
        producto.setUnidadVenta("unidad");
        producto.setVidaUtilDias(15);
        producto = productoRepository.save(producto);

        receta = new RecetaQueso();
        receta.setProducto(producto);
        receta.setVigenteDesde(HOY.minusYears(1));
        RecetaItem item = new RecetaItem();
        item.setReceta(receta);
        item.setMateriaPrima(insumo);
        item.setCantidadPorUnidad(new BigDecimal("0.5"));
        receta.getItems().add(item);
        receta = recetaRepository.save(receta);
    }

    // ------------------------------------------------------------------ utilidades

    private MvcResult enviar(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    /**
     * Rechazo limpio por los datos: 4xx, nunca 500 ni 2xx. Tampoco 401/403: esos serían falta de
     * permiso y harían pasar la prueba sin haber llegado a validar nada.
     */
    private String rechaza(String url, String json) throws Exception {
        MvcResult r = enviar(url, json);
        assertThat(r.getResponse().getStatus())
                .as("%s %s -> %s", url, json, r.getResponse().getContentAsString())
                .isBetween(400, 499)
                .isNotIn(401, 403);
        return r.getResponse().getContentAsString();
    }

    private void acepta(String url, String json) throws Exception {
        MvcResult r = enviar(url, json);
        assertThat(r.getResponse().getStatus())
                .as("%s %s -> %s", url, json, r.getResponse().getContentAsString())
                .isBetween(200, 299);
    }

    private String insumoJson(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"unidadMedida\":\"KG\",\"costoUnitarioReferencial\":2}";
    }

    private String productoJson(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"unidadVenta\":\"unidad\",\"vidaUtilDias\":10}";
    }

    private LoteQueso lote(LocalDate fecha, LocalDate vence, int unidades) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                fecha, producto.getId(), OrigenLeche.ACOPIO_DEL_DIA, 1.0, unidades, "Turno A", null, receta.getId(),
                unidades, List.of(), null, null, null, vence, null));
    }

    // ------------------------------------------------------------------ insumos: "sal", "zal", "zall"

    /**
     * Nombre corto y raro a propósito (no existe en los datos de demostración) y sin el sufijo de la
     * prueba: con un nombre largo la tolerancia sube a 2 letras y la prueba no distinguiría nada.
     * «Kiborr» está a 3 letras de «Quíbor», pero suena igual. El caso «sal / zal / zall» va en
     * NombresParecidosTest.
     */
    @ParameterizedTest
    @ValueSource(strings = {"Quíbor", " QUIBOR ", "quibor", "Kibor", "Kiborr", "Quivor", "Qibor"})
    void unInsumoQueSeEscribeOSuenaComoOtroNoEntra(String intento) throws Exception {
        acepta("/api/v1/inventario/materias-primas", insumoJson("Quíbor"));
        String cuerpo = rechaza("/api/v1/inventario/materias-primas", insumoJson(intento));
        assertThat(cuerpo).contains("Quíbor");
    }

    @Test
    void nombresVaciosLargosOConCostoNegativoNoEntran() throws Exception {
        rechaza("/api/v1/inventario/materias-primas", insumoJson(""));
        rechaza("/api/v1/inventario/materias-primas", insumoJson("   "));
        rechaza("/api/v1/inventario/materias-primas", insumoJson("x".repeat(121)));
        rechaza("/api/v1/inventario/materias-primas",
                "{\"nombre\":\"Cuajo " + sufijo + "\",\"unidadMedida\":\"KG\",\"costoUnitarioReferencial\":-1}");
        rechaza("/api/v1/inventario/materias-primas",
                "{\"nombre\":\"Cuajo " + sufijo + "\",\"unidadMedida\":\"TONELADA\",\"costoUnitarioReferencial\":1}");
    }

    @Test
    void unSinonimoNoPuedeSerElNombreDeOtroInsumo() throws Exception {
        rechaza("/api/v1/inventario/materias-primas",
                "{\"nombre\":\"Sal fina " + sufijo + "\",\"unidadMedida\":\"KG\",\"costoUnitarioReferencial\":1,"
                        + "\"sinonimos\":\"Fermento " + sufijo + "\"}");
    }

    // ------------------------------------------------------------------ productos

    @Test
    void unProductoRepetidoOCasiIgualNoEntra() throws Exception {
        String yogurt = "Yogurt " + sufijo;
        acepta("/api/v1/productos", productoJson(yogurt));
        for (String intento : new String[] {"YOGURT", "yogúrt", "Yogurd", "Yogur", "Llogurt"}) {
            rechaza("/api/v1/productos", productoJson(intento + " " + sufijo));
        }
    }

    @Test
    void unProductoSinUnidadOConVidaUtilAbsurdaNoEntra() throws Exception {
        rechaza("/api/v1/productos", "{\"nombre\":\"Manjar " + sufijo + "\",\"unidadVenta\":\"\"}");
        rechaza("/api/v1/productos", "{\"nombre\":\"Manjar " + sufijo + "\",\"unidadVenta\":\"unidad\",\"vidaUtilDias\":0}");
        rechaza("/api/v1/productos", "{\"nombre\":\"Manjar " + sufijo + "\",\"unidadVenta\":\"unidad\",\"vidaUtilDias\":-5}");
        rechaza("/api/v1/productos", "{\"nombre\":\"Manjar " + sufijo + "\",\"unidadVenta\":\"unidad\",\"vidaUtilDias\":99999}");
    }

    // ------------------------------------------------------------------ recetas

    @Test
    void unaRecetaSinInsumosConCantidadCeroONegativaOConIdsFalsosNoEntra() throws Exception {
        String url = "/api/v1/produccion/recetas";
        String inicio = "{\"productoId\":" + producto.getId() + ",\"vigenteDesde\":\"" + HOY + "\",\"items\":";
        rechaza(url, inicio + "[]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":" + insumo.getId() + ",\"cantidadPorUnidad\":0}]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":" + insumo.getId() + ",\"cantidadPorUnidad\":-2}]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":999999999,\"cantidadPorUnidad\":1}]}");
        rechaza(url, "{\"productoId\":999999999,\"vigenteDesde\":\"" + HOY + "\",\"items\":[{\"materiaPrimaId\":"
                + insumo.getId() + ",\"cantidadPorUnidad\":1}]}");
    }

    // ------------------------------------------------------------------ órdenes

    @Test
    void unaOrdenConCantidadAbsurdaOProductoFalsoNoEntra() throws Exception {
        String url = "/api/v1/produccion/ordenes";
        rechaza(url, "{\"productoId\":" + producto.getId() + ",\"cantidad\":0}");
        rechaza(url, "{\"productoId\":" + producto.getId() + ",\"cantidad\":-10}");
        rechaza(url, "{\"productoId\":" + producto.getId() + ",\"cantidad\":2000000}");
        rechaza(url, "{\"productoId\":999999999,\"cantidad\":5}");
        rechaza(url, "{\"cantidad\":5}");
    }

    @Test
    void anularUnaOrdenSinMotivoOUnaQueNoExisteNoPasa() throws Exception {
        rechaza("/api/v1/produccion/ordenes/999999999/anular", "{\"motivo\":\"prueba\"}");
        rechaza("/api/v1/produccion/ordenes/999999999/anular", "{\"motivo\":\"\"}");
    }

    // ------------------------------------------------------------------ producción

    private String loteJson(String fecha, Integer unidades, String responsable, String vence) {
        return "{\"fecha\":\"" + fecha + "\",\"productoId\":" + producto.getId() + ",\"origenLeche\":\"ACOPIO_DEL_DIA\","
                + "\"unidadesProducidas\":" + unidades + ",\"cantidadObjetivo\":" + unidades + ",\"responsable\":\""
                + responsable + "\",\"recetaId\":" + receta.getId()
                + (vence == null ? "" : ",\"fechaVencimiento\":\"" + vence + "\"") + "}";
    }

    @Test
    void unaProduccionImposibleNoEntra() throws Exception {
        String url = "/api/v1/quesos/lotes";
        rechaza(url, loteJson(HOY.toString(), 0, "Ana", null));
        rechaza(url, loteJson(HOY.toString(), -3, "Ana", null));
        rechaza(url, loteJson(HOY.toString(), 5, "", null));
        // 10 kg de fermento y 0.5 kg por unidad: alcanza para 20, no para 21.
        rechaza(url, loteJson(HOY.toString(), 21, "Ana", null));
        // Vence antes de producirse.
        rechaza(url, loteJson(HOY.toString(), 2, "Ana", HOY.minusDays(1).toString()));
        // Producido en el futuro.
        rechaza(url, loteJson(HOY.plusDays(5).toString(), 2, "Ana", null));
    }

    // ------------------------------------------------------------------ ventas

    private String ventaJson(int unidades, String cliente) {
        return "{\"productoId\":" + producto.getId() + ",\"cliente\":\"" + cliente + "\",\"unidades\":" + unidades
                + ",\"tipoComprador\":\"PUBLICO_GENERAL\",\"condicion\":\"CONTADO\",\"medioCobro\":\"EFECTIVO\"}";
    }

    @Test
    void noSeVendeMasDeLoQueHayNiCantidadesAbsurdasNiSinCliente() throws Exception {
        lote(HOY, null, 4);
        String url = "/api/v1/quesos/ventas/por-producto";
        rechaza(url, ventaJson(5, "Mercado"));
        rechaza(url, ventaJson(0, "Mercado"));
        rechaza(url, ventaJson(-1, "Mercado"));
        rechaza(url, ventaJson(1, ""));
    }

    @Test
    void loVencidoNoSeVende() throws Exception {
        lote(HOY.minusDays(10), HOY.minusDays(1), 6);
        String cuerpo = rechaza("/api/v1/quesos/ventas/por-producto", ventaJson(1, "Mercado"));
        assertThat(cuerpo).contains("sin vencer");
    }

    // ------------------------------------------------------------------ compras

    @Test
    void unaCompraConCantidadesAbsurdasOInsumoFalsoNoEntra() throws Exception {
        String url = "/api/v1/compras";
        String inicio = "{\"proveedorCompraId\":1,\"fecha\":\"" + HOY + "\",\"items\":";
        rechaza(url, inicio + "[]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":" + insumo.getId() + ",\"cantidad\":0,\"costoUnitario\":1}]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":" + insumo.getId() + ",\"cantidad\":2,\"costoUnitario\":-1}]}");
        rechaza(url, inicio + "[{\"materiaPrimaId\":999999999,\"cantidad\":2,\"costoUnitario\":1}]}");
        rechaza(url, "{\"proveedorCompraId\":999999999,\"fecha\":\"" + HOY + "\",\"items\":[{\"materiaPrimaId\":"
                + insumo.getId() + ",\"cantidad\":2,\"costoUnitario\":1}]}");
    }

    @Test
    void unJsonRotoONoEsperadoNoRompeElServidor() throws Exception {
        rechaza("/api/v1/produccion/ordenes", "{esto no es json");
        rechaza("/api/v1/produccion/ordenes", "{\"productoId\":\"abc\",\"cantidad\":\"diez\"}");
        rechaza("/api/v1/quesos/lotes", "[]");
    }

    // ------------------------------------------------------------------ permisos

    @Test
    @WithMockUser(username = "proveedor.demo@huata.pe", roles = "PROVEEDOR")
    void unProveedorDeLecheNoPuedeTocarProduccionNiCatalogo() throws Exception {
        assertThat(enviar("/api/v1/productos", productoJson("Hack " + sufijo)).getResponse().getStatus()).isEqualTo(403);
        assertThat(enviar("/api/v1/produccion/ordenes", "{\"productoId\":" + producto.getId() + ",\"cantidad\":1}")
                        .getResponse().getStatus()).isEqualTo(403);
        assertThat(enviar("/api/v1/quesos/ventas/por-producto", ventaJson(1, "x")).getResponse().getStatus())
                .isEqualTo(403);
    }
}
