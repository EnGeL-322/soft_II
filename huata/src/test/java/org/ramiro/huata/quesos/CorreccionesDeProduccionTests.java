package org.ramiro.huata.quesos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.compras.Compra;
import org.ramiro.huata.compras.CompraItemRequest;
import org.ramiro.huata.compras.CompraRequest;
import org.ramiro.huata.compras.CompraService;
import org.ramiro.huata.compras.ProveedorCompra;
import org.ramiro.huata.compras.ProveedorCompraRepository;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.AjusteInventario;
import org.ramiro.huata.inventario.AjusteInventarioRequestFixture;
import org.ramiro.huata.inventario.AjusteInventarioService;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.produccion.Producto;
import org.ramiro.huata.produccion.ProductoRepository;
import org.ramiro.huata.produccion.RecetaItem;
import org.ramiro.huata.produccion.RecetaQueso;
import org.ramiro.huata.produccion.RecetaQuesoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * El flujo compra → almacén → lote → venta se puede corregir sin editar nada ya escrito (V59): ajuste
 * de inventario, anulación de un lote y anulación de una compra. Cada corrección deja su movimiento
 * en el kardex y su asiento de reversión, y se niega cuando dejaría el stock negativo.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class CorreccionesDeProduccionTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired private QueseriaService queseriaService;
    @Autowired private CompraService compraService;
    @Autowired private AjusteInventarioService ajusteService;
    @Autowired private InventarioService inventarioService;
    @Autowired private MateriaPrimaRepository materiaPrimaRepository;
    @Autowired private RecetaQuesoRepository recetaRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProveedorCompraRepository proveedorRepository;
    @Autowired private TarifaQuesoRepository tarifaRepository;
    @Autowired private AsientoContableService asientoContableService;

    private MateriaPrima sal;
    private Producto paria;
    private RecetaQueso receta;
    private ProveedorCompra proveedor;

    @BeforeEach
    void escenario() {
        paria = productoRepository.findByNombreIgnoreCase("Queso paria").orElseThrow();
        paria.setVidaUtilDias(15);
        paria = productoRepository.save(paria);
        tarifaRepository.deleteAll();
        queseriaService.guardarTarifa(new TarifaQuesoRequest(paria.getId(), 20.0, 15.0, 13.0, HOY.minusYears(1), null, null, null));

        sal = new MateriaPrima();
        sal.setNombre("Sal de prueba " + System.nanoTime());
        sal.setUnidadMedida(UnidadMedida.KG);
        sal.setCostoUnitarioReferencial(BigDecimal.valueOf(2));
        sal = materiaPrimaRepository.save(sal);

        receta = new RecetaQueso();
        receta.setProducto(paria);
        receta.setVigenteDesde(HOY.minusYears(1));
        RecetaItem item = new RecetaItem();
        item.setReceta(receta);
        item.setMateriaPrima(sal);
        item.setCantidadPorUnidad(BigDecimal.valueOf(2));
        receta.getItems().add(item);
        receta = recetaRepository.save(receta);

        proveedor = new ProveedorCompra();
        proveedor.setCodigo("PC-CORR");
        proveedor.setNombre("Insumos del Altiplano");
        proveedor = proveedorRepository.save(proveedor);
    }

    private Compra comprar(int kg) {
        return compraService.registrar(new CompraRequest(
                proveedor.getId(), HOY, "F001-1", null,
                List.of(new CompraItemRequest(sal.getId(), BigDecimal.valueOf(kg), BigDecimal.valueOf(2)))));
    }

    private LoteQueso producir(int unidades) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                HOY, paria.getId(), OrigenLeche.ACOPIO_DEL_DIA, 100.0, unidades, "Turno A", null,
                receta.getId(), unidades, List.of(), null, null, null));
    }

    private BigDecimal stockSal() {
        return inventarioService.saldoMateriaPrima(sal.getId());
    }

    // ---- Ajuste de inventario ----

    @Test
    void unaMermaBajaElStockConSuMotivoYSuAsiento() {
        comprar(50);

        ajusteService.registrar(AjusteInventarioRequestFixture.salida(sal.getId(), 3, "Merma por humedad"));

        assertThat(stockSal()).isEqualByComparingTo("47");
        assertThat(asientoContableService.libroDiario(HOY, HOY))
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.AJUSTE_INVENTARIO);
                    assertThat(a.totalDebe()).isEqualByComparingTo("6.00");
                });
    }

    @Test
    void unConteoFisicoMayorSubeElStock() {
        AjusteInventario a = ajusteService.registrar(AjusteInventarioRequestFixture.entrada(sal.getId(), 4, "Conteo físico"));

        assertThat(a.getId()).isNotNull();
        assertThat(stockSal()).isEqualByComparingTo("4");
    }

    @Test
    void noSeSacaMasDeLoQueHayNiSinMotivo() {
        comprar(5);

        assertThatThrownBy(() -> ajusteService.registrar(AjusteInventarioRequestFixture.salida(sal.getId(), 6, "Merma")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("en el almacén hay 5");
        assertThatThrownBy(() -> ajusteService.registrar(AjusteInventarioRequestFixture.salida(sal.getId(), 1, "  ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("motivo");
        assertThat(stockSal()).isEqualByComparingTo("5");
    }

    // ---- Anular un lote ----

    @Test
    void anularUnLoteDevuelveLosInsumosYSacaElQuesoDeCamara() {
        comprar(50);
        LoteQueso lote = producir(10); // la receta lleva 2 kg por unidad: salen 20 kg
        assertThat(stockSal()).isEqualByComparingTo("30");

        queseriaService.anularLote(lote.getId(), "Se registró dos veces");

        assertThat(stockSal()).isEqualByComparingTo("50");
        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("0");
        assertThat(queseriaService.listarLotes(null, null)).noneMatch(l -> l.lote().getId().equals(lote.getId()));
        assertThat(asientoContableService.libroDiario(HOY, HOY))
                .anySatisfy(a -> assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.ANULACION_PRODUCCION));
    }

    @Test
    void noSeAnulaUnLoteDelQueYaSeVendio() {
        comprar(50);
        LoteQueso lote = producir(10);
        queseriaService.registrarVenta(new VentaQuesoRequest(
                lote.getId(), "Mercado", 2, TipoComprador.PUBLICO_GENERAL, CondicionVenta.CONTADO, MedioCobro.EFECTIVO));

        assertThatThrownBy(() -> queseriaService.anularLote(lote.getId(), "Error"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya salieron 2");
        assertThat(stockSal()).isEqualByComparingTo("30");
    }

    @Test
    void unLoteNoSeAnulaDosVeces() {
        comprar(50);
        LoteQueso lote = producir(5);
        queseriaService.anularLote(lote.getId(), "Error de captura");

        assertThatThrownBy(() -> queseriaService.anularLote(lote.getId(), "Otra vez"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está anulado");
    }

    // ---- Vencimiento obligatorio y venta FEFO (2026-10-27) ----

    @Test
    void cadaLoteNaceConVencimientoSegunLosDiasDelProducto() {
        comprar(50);
        assertThat(producir(5).getFechaVencimiento()).isEqualTo(HOY.plusDays(15));
    }

    @Test
    void sinDiasDeVencimientoConfiguradosNoSeProduce() {
        comprar(50);
        paria.setVidaUtilDias(null);
        productoRepository.save(paria);

        assertThatThrownBy(() -> producir(5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Configuración");
    }

    private LoteQueso producirQueVence(int unidades, LocalDate vence) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                HOY, paria.getId(), OrigenLeche.ACOPIO_DEL_DIA, 100.0, unidades, "Turno A", null,
                receta.getId(), unidades, List.of(), null, null, null, vence));
    }

    @Test
    void venderPorProductoSacaPrimeroLoQueVenceAntesYCompletaConElSiguiente() {
        comprar(100);
        LoteQueso vaLejos = producirQueVence(10, HOY.plusDays(10));
        LoteQueso vencePronto = producirQueVence(5, HOY.plusDays(3));

        List<VentaQueso> ventas = queseriaService.venderPorProducto(new VentaPorProductoRequest(
                paria.getId(), "Mercado", 7, TipoComprador.PUBLICO_GENERAL, CondicionVenta.CONTADO, MedioCobro.EFECTIVO));

        assertThat(ventas).hasSize(2);
        assertThat(ventas.get(0).getLote().getId()).isEqualTo(vencePronto.getId());
        assertThat(ventas.get(0).getUnidades()).isEqualTo(5);
        assertThat(ventas.get(1).getLote().getId()).isEqualTo(vaLejos.getId());
        assertThat(ventas.get(1).getUnidades()).isEqualTo(2);
        assertThat(inventarioService.saldoLoteQueso(vencePronto.getId())).isEqualByComparingTo("0");
        assertThat(inventarioService.saldoLoteQueso(vaLejos.getId())).isEqualByComparingTo("8");
    }

    @Test
    void noSeVendeMasDeLoQueHayDelProducto() {
        comprar(50);
        producir(4);

        assertThatThrownBy(() -> queseriaService.venderPorProducto(new VentaPorProductoRequest(
                        paria.getId(), "Mercado", 9, TipoComprador.PUBLICO_GENERAL, CondicionVenta.CONTADO, MedioCobro.EFECTIVO)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hay 4");
    }

    // ---- Anular una compra ----

    @Test
    void anularUnaCompraSacaLoQueEntroYRevierteSuAsiento() {
        Compra compra = comprar(20);

        Compra anulada = compraService.anular(compra.getId(), "Factura duplicada");

        assertThat(anulada.isActivo()).isFalse();
        assertThat(anulada.getMotivoAnulacion()).isEqualTo("Factura duplicada");
        assertThat(stockSal()).isEqualByComparingTo("0");
        assertThat(asientoContableService.libroDiario(HOY, HOY))
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.ANULACION_COMPRA);
                    assertThat(a.totalDebe()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void noSeAnulaUnaCompraCuyoInsumoYaSeUsoEnProduccion() {
        Compra compra = comprar(20);
        producir(5); // consume 10 kg

        assertThatThrownBy(() -> compraService.anular(compra.getId(), "Error"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya se usó en producción");
        assertThat(stockSal()).isEqualByComparingTo("10");
    }
}
