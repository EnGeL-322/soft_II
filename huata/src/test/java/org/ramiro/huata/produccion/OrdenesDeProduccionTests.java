package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.compras.CompraItemRequest;
import org.ramiro.huata.compras.CompraRequest;
import org.ramiro.huata.compras.CompraService;
import org.ramiro.huata.compras.ProveedorCompra;
import org.ramiro.huata.compras.ProveedorCompraRepository;
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

/**
 * Ordenes de produccion (V61, prototipo aprobado por el cliente).
 *
 * <p>Escenario: 1 yogurt lleva 0.5 kg de fruta y 2 envases. Hay 3 kg de fruta y 100 envases: la
 * fruta alcanza para 6.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "produccion@huata.pe")
class OrdenesDeProduccionTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired private OrdenProduccionService ordenService;
    @Autowired private QueseriaService queseriaService;
    @Autowired private CompraService compraService;
    @Autowired private InventarioService inventarioService;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private RecetaQuesoRepository recetaRepository;
    @Autowired private ProveedorCompraRepository proveedorRepository;

    private Producto yogurt;
    private RecetaQueso receta;
    private MateriaPrima fruta;
    private MateriaPrima envase;

    @BeforeEach
    void escenario() {
        yogurt = producto("Yogurt OP");
        fruta = materia("Fruta OP", UnidadMedida.KG, "3");
        envase = materia("Envase OP", UnidadMedida.UNIDAD, "100");
        receta = receta(yogurt, fruta, "0.5", envase, "2");
    }

    private Producto producto(String nombre) {
        Producto p = new Producto();
        p.setNombre(nombre + " " + System.nanoTime());
        p.setUnidadVenta("unidad");
        p.setVidaUtilDias(10);
        return productoRepository.save(p);
    }

    private MateriaPrima materia(String nombre, UnidadMedida unidad, String stock) {
        MateriaPrima m = new MateriaPrima();
        m.setNombre(nombre + " " + System.nanoTime());
        m.setUnidadMedida(unidad);
        m.setCostoUnitarioReferencial(BigDecimal.ONE);
        m = materiaPrimaRepository.save(m);
        inventarioService.registrarEntradaCompra(m, new BigDecimal(stock), HOY.minusDays(2), 0L);
        return m;
    }

    private RecetaQueso receta(Producto p, MateriaPrima a, String ca, MateriaPrima b, String cb) {
        RecetaQueso r = new RecetaQueso();
        r.setProducto(p);
        r.setVigenteDesde(HOY.minusYears(1));
        for (Object[] i : new Object[][] {{a, ca}, {b, cb}}) {
            RecetaItem item = new RecetaItem();
            item.setReceta(r);
            item.setMateriaPrima((MateriaPrima) i[0]);
            item.setCantidadPorUnidad(new BigDecimal((String) i[1]));
            r.getItems().add(item);
        }
        return recetaRepository.save(r);
    }

    private OrdenProduccionResponse planificar(int cantidad) {
        return ordenService.crear(new OrdenProduccionRequest(yogurt.getId(), cantidad, HOY, null));
    }

    private OrdenProduccionResponse ver(Long id) {
        return ordenService.listar().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    private LoteQueso producir(int cantidad, Long ordenId) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                HOY, yogurt.getId(), OrigenLeche.ACOPIO_DEL_DIA, 1.0, cantidad, "Turno A", null, receta.getId(), cantidad,
                List.of(), null, null, null, null, ordenId));
    }

    @Test
    void seplanificaAunqueFalteStockYDiceQueFalta() {
        OrdenProduccionResponse op = planificar(10);

        assertThat(op.codigo()).startsWith("OP" + HOY.getYear() + "-");
        assertThat(op.estado()).isEqualTo(OrdenProduccion.Estado.PENDIENTE);
        assertThat(op.lista()).isFalse();
        assertThat(op.registradoPor()).isEqualTo("produccion@huata.pe");
        assertThat(op.lineas()).filteredOn(l -> l.materiaPrimaId().equals(fruta.getId())).singleElement()
                .satisfies(l -> {
                    assertThat(l.necesita()).isEqualByComparingTo("5");
                    assertThat(l.falta()).isEqualByComparingTo("2");
                });
    }

    @Test
    void dosOrdenesNoCuentanElMismoStock_laPrimeraReservaYLaSegundaEspera() {
        OrdenProduccionResponse primera = planificar(6); // usa los 3 kg de fruta
        OrdenProduccionResponse segunda = planificar(2);

        assertThat(ver(primera.id()).lista()).isTrue();
        assertThat(ver(segunda.id()).lista()).isFalse();
        assertThat(ver(segunda.id()).lineas()).filteredOn(l -> l.materiaPrimaId().equals(fruta.getId())).singleElement()
                .satisfies(l -> assertThat(l.disponible()).isEqualByComparingTo("0"));
    }

    @Test
    void lasNecesidadesRestanElAlmacenYLoYaCompradoQueNoLlega() {
        planificar(10); // necesita 5 kg de fruta; hay 3
        ProveedorCompra proveedor = new ProveedorCompra();
        proveedor.setCodigo("PC-OP");
        proveedor.setNombre("Frutas OP");
        proveedor = proveedorRepository.save(proveedor);
        compraService.registrar(new CompraRequest(proveedor.getId(), HOY, null, null,
                List.of(new CompraItemRequest(fruta.getId(), new BigDecimal("0.5"), BigDecimal.ONE)), false));

        assertThat(ordenService.necesidades()).filteredOn(n -> n.materiaPrimaId().equals(fruta.getId())).singleElement()
                .satisfies(n -> {
                    assertThat(n.requerido()).isEqualByComparingTo("5");
                    assertThat(n.disponible()).isEqualByComparingTo("3");
                    assertThat(n.enCamino()).isEqualByComparingTo("0.5");
                    assertThat(n.aComprar()).isEqualByComparingTo("1.5");
                });
        // Los envases alcanzan (20 de 100): no se sugiere comprarlos.
        assertThat(ordenService.necesidades()).noneMatch(n -> n.materiaPrimaId().equals(envase.getId()));
    }

    @Test
    void elLoteCumpleLaOrdenYAnularloLaReabre() {
        OrdenProduccionResponse op = planificar(4);

        LoteQueso lote = producir(4, op.id());

        assertThat(ver(op.id())).satisfies(o -> {
            assertThat(o.estado()).isEqualTo(OrdenProduccion.Estado.TERMINADA);
            assertThat(o.loteCodigo()).isEqualTo(lote.getCodigo());
            assertThat(o.lista()).isNull();
        });
        assertThatThrownBy(() -> producir(4, op.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya no está pendiente");

        queseriaService.anularLote(lote.getId(), "Se registró por error");

        assertThat(ver(op.id()).estado()).isEqualTo(OrdenProduccion.Estado.PENDIENTE);
    }

    @Test
    void unLoteNoCumpleLaOrdenDeOtroProducto() {
        Producto otro = producto("Manjar OP");
        recetaRepository.save(receta(otro, fruta, "0.1", envase, "1"));
        OrdenProduccionResponse deOtro = ordenService.crear(new OrdenProduccionRequest(otro.getId(), 1, HOY, null));

        assertThatThrownBy(() -> producir(1, deOtro.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("es de " + otro.getNombre());
    }

    @Test
    void anularUnaOrdenExigeMotivoYUnaTerminadaSeDeshaceAnulandoSuLote() {
        OrdenProduccionResponse op = planificar(1);
        assertThatThrownBy(() -> ordenService.anular(op.id(), "")).isInstanceOf(BusinessRuleException.class);

        assertThat(ordenService.anular(op.id(), "Cambio de plan").estado()).isEqualTo(OrdenProduccion.Estado.ANULADA);

        OrdenProduccionResponse otra = planificar(1);
        producir(1, otra.id());
        assertThatThrownBy(() -> ordenService.anular(otra.id(), "Cambio de plan"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anula su lote");
    }

    @Test
    void sinRecetaNoSePlanifica() {
        Producto sinReceta = producto("Sin receta OP");
        assertThatThrownBy(() -> ordenService.crear(new OrdenProduccionRequest(sinReceta.getId(), 1, HOY, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene receta vigente");
    }
}
