package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRequest;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.quesos.QueseriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Produccion calculada con la receta y el inventario (pedido del cliente, 2026-09-30):
 *
 * <ul>
 *   <li>Cuanto se puede producir: por cada insumo, stock / lo que lleva una unidad; el maximo es el
 *       menor y ese insumo es el limitante.
 *   <li>Al guardar se descuenta <b>exactamente</b> receta x lo que se puso a producir, y los litros
 *       del lote son la leche descontada.
 * </ul>
 *
 * <p>Escenario: 1 queso lleva 10 L de leche, 0.05 kg de sal y 1 ml de cuajo. Hay 0.75 kg de sal,
 * asi que la sal alcanza para 15 y es la que limita (la leche y el cuajo alcanzan para mucho mas).
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ProduccionSegunRecetaTests {

    private static final LocalDate AYER = LocalDate.now().minusDays(1);

    @Autowired
    private RecetaService recetaService;

    @Autowired
    private QueseriaService queseriaService;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private RecetaQuesoRepository recetaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Producto queso;
    private MateriaPrima leche;
    private MateriaPrima sal;
    private MateriaPrima cuajo;
    private RecetaQueso receta;

    @BeforeEach
    void escenario() {
        queso = producto("Queso capacidad");
        // La leche es la del acopio (la que siembra la base), no una copia: es la que entra desde planta.
        leche = materiaPrimaRepository.findFirstByLecheAcopioTrue().orElseThrow();
        sal = materia("Sal capacidad", UnidadMedida.KG, "0.75");
        cuajo = materia("Cuajo capacidad", UnidadMedida.ML, "500");
        inventarioService.registrarEntradaCompra(leche, new BigDecimal("10000"), AYER.minusDays(1), 0L);

        receta = receta(queso, new Object[] {leche, "10"}, new Object[] {sal, "0.05"}, new Object[] {cuajo, "1"});
    }

    private Producto producto(String nombre) {
        Producto p = new Producto();
        p.setNombre(nombre + " " + System.nanoTime());
        p.setUnidadVenta("unidad");
        p.setVidaUtilDias(30);
        return productoRepository.save(p);
    }

    private MateriaPrima materia(String nombre, UnidadMedida unidad, String stock) {
        MateriaPrima m = new MateriaPrima();
        m.setNombre(nombre + " " + System.nanoTime());
        m.setUnidadMedida(unidad);
        m.setCostoUnitarioReferencial(BigDecimal.ONE);
        m = materiaPrimaRepository.save(m);
        if (new BigDecimal(stock).signum() > 0) {
            inventarioService.registrarEntradaCompra(m, new BigDecimal(stock), AYER.minusDays(1), 0L);
        }
        return m;
    }

    private RecetaQueso receta(Producto producto, Object[]... items) {
        RecetaQueso r = new RecetaQueso();
        r.setProducto(producto);
        r.setVigenteDesde(AYER.minusYears(1));
        for (Object[] i : items) {
            RecetaItem item = new RecetaItem();
            item.setReceta(r);
            item.setMateriaPrima((MateriaPrima) i[0]);
            item.setCantidadPorUnidad(new BigDecimal((String) i[1]));
            r.getItems().add(item);
        }
        return recetaRepository.save(r);
    }

    private BigDecimal saldo(MateriaPrima m) {
        return inventarioService.saldoMateriaPrima(m.getId());
    }

    private LoteQueso producir(int aProducir, int salieron) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                AYER, queso.getId(), OrigenLeche.ACOPIO_DEL_DIA, null, salieron, "Turno A", null,
                receta.getId(), aProducir, List.of(), null, null, null));
    }

    // ---------------------------------------------------------------- Cuanto se puede producir

    @Test
    void elMaximoEsElDelInsumoQueMenosAlcanzaYEseEsElLimitante() {
        CapacidadProduccionResponse c = recetaService.capacidad(queso.getId(), AYER);

        assertThat(c.tieneReceta()).isTrue();
        assertThat(c.maximoProducible()).isEqualTo(15);
        assertThat(c.limitanteId()).isEqualTo(sal.getId());
        assertThat(c.limitanteNombre()).isEqualTo(sal.getNombre());
        assertThat(c.items()).filteredOn(CapacidadItemResponse::limitante)
                .singleElement()
                .satisfies(i -> {
                    assertThat(i.materiaPrimaId()).isEqualTo(sal.getId());
                    assertThat(i.alcanzaPara()).isEqualTo(15);
                });
        assertThat(c.items()).filteredOn(i -> i.materiaPrimaId().equals(cuajo.getId()))
                .singleElement()
                .satisfies(i -> assertThat(i.alcanzaPara()).isEqualTo(500));
    }

    @Test
    void laLecheDisponibleEsElStockDeLaLecheDelAcopio() {
        CapacidadProduccionResponse c = recetaService.capacidad(queso.getId(), AYER);

        assertThat(c.recetaLlevaLeche()).isTrue();
        assertThat(c.lecheDisponible()).isEqualByComparingTo(saldo(leche));
        assertThat(c.items()).filteredOn(CapacidadItemResponse::esLeche).singleElement()
                .satisfies(i -> assertThat(i.materiaPrimaId()).isEqualTo(leche.getId()));
    }

    @Test
    void sinProductoElegidoSoloTraeLaLecheDisponible() {
        CapacidadProduccionResponse c = recetaService.capacidad(null, AYER);

        assertThat(c.lecheDisponible()).isEqualByComparingTo(saldo(leche));
        assertThat(c.tieneReceta()).isFalse();
        assertThat(c.items()).isEmpty();
    }

    /** Un insumo que no esta en el almacen deja el maximo en cero y es el que limita. */
    @Test
    void unInsumoSinStockDejaElMaximoEnCero() {
        MateriaPrima fermento = materia("Fermento sin stock", UnidadMedida.GRAMO, "0");
        Producto yogur = producto("Yogur capacidad");
        receta(yogur, new Object[] {leche, "1"}, new Object[] {fermento, "2"});

        CapacidadProduccionResponse c = recetaService.capacidad(yogur.getId(), AYER);

        assertThat(c.maximoProducible()).isZero();
        assertThat(c.limitanteId()).isEqualTo(fermento.getId());
    }

    /** Sin receta no se inventa un maximo: la pantalla avisa que falta la receta. */
    @Test
    void unProductoSinRecetaNoTieneMaximo() {
        Producto sinReceta = producto("Manjar sin receta");

        CapacidadProduccionResponse c = recetaService.capacidad(sinReceta.getId(), AYER);

        assertThat(c.tieneReceta()).isFalse();
        assertThat(c.maximoProducible()).isNull();
        assertThat(c.items()).isEmpty();
    }

    @Test
    void unaRecetaSinLecheSeDetecta() {
        Producto mantequilla = producto("Sin leche");
        receta(mantequilla, new Object[] {sal, "0.01"});

        assertThat(recetaService.capacidad(mantequilla.getId(), AYER).recetaLlevaLeche()).isFalse();
    }

    // ---------------------------------------------------------------- Lo que se descuenta al guardar

    @Test
    void alGuardarCadaInsumoBajaExactamenteLoQuePideLaReceta() {
        BigDecimal lecheAntes = saldo(leche);
        BigDecimal salAntes = saldo(sal);
        BigDecimal cuajoAntes = saldo(cuajo);

        producir(10, 10);

        assertThat(lecheAntes.subtract(saldo(leche))).isEqualByComparingTo("100");
        assertThat(salAntes.subtract(saldo(sal))).isEqualByComparingTo("0.50");
        assertThat(cuajoAntes.subtract(saldo(cuajo))).isEqualByComparingTo("10");
    }

    @Test
    void losLitrosDelLoteSonLaLecheDescontada() {
        LoteQueso lote = producir(12, 12);

        assertThat(lote.getLitrosDestinados()).isEqualTo(120.0);
    }

    /**
     * Se consume sobre lo que se puso a producir; si salieron menos, eso es lo que entra a "Guardado
     * para vender" y la diferencia queda como merma.
     */
    @Test
    void siSalenMenosSeConsumeLoPlaneadoYSeGuardaLoQueSalio() {
        BigDecimal salAntes = saldo(sal);

        LoteQueso lote = producir(10, 9);

        assertThat(salAntes.subtract(saldo(sal))).isEqualByComparingTo("0.50");
        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("9");
        assertThat(lote.merma()).isEqualTo(1);
    }

    @Test
    void pedirMasDelMaximoSeRechazaNombrandoElInsumoQueNoAlcanza() {
        assertThatThrownBy(() -> producir(16, 16))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("stock suficiente")
                .hasMessageContaining(sal.getNombre());
    }

    /** Justo el maximo si se puede: la pantalla y el servidor hacen la misma cuenta. */
    @Test
    void producirJustoElMaximoDejaElLimitanteEnCero() {
        producir(15, 15);

        assertThat(saldo(sal)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(recetaService.capacidad(queso.getId(), AYER).maximoProducible()).isZero();
    }

    @Test
    void unaRecetaSinLecheYSinLitrosPideCompletarLaReceta() {
        Producto sinLeche = producto("Sin leche guardar");
        RecetaQueso r = receta(sinLeche, new Object[] {cuajo, "1"});

        assertThatThrownBy(() -> queseriaService.crearLote(new LoteQuesoRequest(
                        AYER, sinLeche.getId(), OrigenLeche.ACOPIO_DEL_DIA, null, 5, "Turno A", null,
                        r.getId(), 5, List.of(), null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no dice cuánta leche");
    }

    /**
     * Una APK instalada antes de este cambio todavia manda "consumos": no debe fallar por eso. El
     * campo se ignora y manda la receta.
     */
    @Test
    void unaPeticionViejaConConsumosSigueSiendoValida() throws Exception {
        String json = "{\"fecha\":\"" + AYER + "\",\"productoId\":" + queso.getId()
                + ",\"origenLeche\":\"ACOPIO_DEL_DIA\",\"litrosDestinados\":999,\"unidadesProducidas\":4,"
                + "\"responsable\":\"APK vieja\",\"recetaId\":" + receta.getId() + ",\"cantidadObjetivo\":4,"
                + "\"consumos\":[{\"materiaPrimaId\":" + sal.getId() + ",\"cantidadConsumida\":7}]}";
        BigDecimal salAntes = saldo(sal);

        LoteQueso lote = queseriaService.crearLote(objectMapper.readValue(json, LoteQuesoRequest.class));

        // Se descuenta la receta (4 x 0.05), no los 7 kg que mandaba la app; y los litros son la leche.
        assertThat(salAntes.subtract(saldo(sal))).isEqualByComparingTo("0.20");
        assertThat(lote.getLitrosDestinados()).isEqualTo(40.0);
    }
}
