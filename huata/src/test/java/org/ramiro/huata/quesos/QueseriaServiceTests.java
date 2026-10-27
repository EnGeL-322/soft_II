package org.ramiro.huata.quesos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.auditoria.AuditoriaRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableResponse;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.produccion.Producto;
import org.ramiro.huata.produccion.ProductoRepository;
import org.ramiro.huata.produccion.RecetaItem;
import org.ramiro.huata.produccion.RecetaQueso;
import org.ramiro.huata.produccion.RecetaQuesoRepository;
import org.ramiro.huata.produccion.TrabajadorProduccion;
import org.ramiro.huata.produccion.TrabajadorProduccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produccion y venta de queso por lote.
 *
 * <p>La regla que justifica las dos pantallas es que una venta no puede llevarse mas unidades de las
 * que quedan en camara. Lo que se comprueba aqui es que la impone el servidor, no el formulario: la
 * pantalla muestra el maximo para que nadie se equivoque, pero quien lo impide es esto.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class QueseriaServiceTests {

    private static final LocalDate HOY = LocalDate.now();
    private static final LocalDate AYER = HOY.minusDays(1);

    @Autowired
    private QueseriaService queseriaService;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private TarifaQuesoRepository tarifaRepository;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private RecetaQuesoRepository recetaQuesoRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private TrabajadorProduccionRepository trabajadorProduccionRepository;

    @Autowired
    private AsientoContableService asientoContableService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private LoteQuesoRepository loteRepository;

    private MateriaPrima cuajo;
    private Producto paria;
    private RecetaQueso recetaParia;

    /**
     * Los precios los pone la tarifa, asi que sin una vigente no se puede vender. En H2 las
     * migraciones no corren (perfil test, create-drop), de modo que la fila que V11 siembra en
     * Postgres hay que crearla aqui.
     *
     * <p>Desde V13 crear un lote exige una receta vigente y materia prima con stock suficiente: se
     * siembra una materia prima con stock amplio (1 000 000) para que ningun test de produccion o
     * venta quede bloqueado por falta de insumo, y una receta minima de 1 unidad de esa materia prima
     * por unidad de queso.
     */
    @BeforeEach
    void escenario() {
        auditoriaRepository.deleteAll();
        tarifaRepository.deleteAll();
        paria = productoRepository.findByNombreIgnoreCase("Queso paria").orElseThrow();
        // Todo lote nace con vencimiento (2026-10-27): sin los dias del producto no se produce.
        paria.setVidaUtilDias(30);
        paria = productoRepository.save(paria);
        tarifa(20.0, 15.0, 13.0, HOY.minusYears(1));

        cuajo = new MateriaPrima();
        cuajo.setNombre("Cuajo");
        cuajo.setUnidadMedida(UnidadMedida.ML);
        cuajo.setCostoUnitarioReferencial(BigDecimal.valueOf(2.50));
        cuajo = materiaPrimaRepository.save(cuajo);
        inventarioService.registrarEntradaCompra(cuajo, BigDecimal.valueOf(1_000_000), HOY.minusYears(1), 0L);

        recetaParia = new RecetaQueso();
        recetaParia.setProducto(paria);
        recetaParia.setVigenteDesde(HOY.minusYears(1));
        RecetaItem item = new RecetaItem();
        item.setReceta(recetaParia);
        item.setMateriaPrima(cuajo);
        item.setCantidadPorUnidad(BigDecimal.ONE);
        recetaParia.getItems().add(item);
        recetaParia = recetaQuesoRepository.save(recetaParia);
    }

    private TarifaQueso tarifa(double publico, double mayorista, double proveedor, LocalDate desde) {
        return tarifa(paria, publico, mayorista, proveedor, desde, null);
    }

    private TarifaQueso tarifa(
            Producto producto, double publico, double mayorista, double proveedor, LocalDate desde, LocalDate hasta) {
        // Sin codigo: el de verificacion lo exige el controlador, en el mismo sitio que el permiso.
        return queseriaService.guardarTarifa(new TarifaQuesoRequest(
                producto.getId(), publico, mayorista, proveedor, desde, hasta, null, null));
    }

    private Producto productoNuevo(String nombre) {
        Producto producto = new Producto();
        producto.setNombre(nombre + " " + System.nanoTime());
        producto.setUnidadVenta("botella 1 L");
        producto.setVidaUtilDias(30);
        return productoRepository.save(producto);
    }

    private LoteQueso lote(int unidades) {
        return lote(unidades, AYER);
    }

    private LoteQueso lote(int unidades, LocalDate fecha) {
        return queseriaService.crearLote(new LoteQuesoRequest(
                fecha,
                paria.getId(),
                OrigenLeche.ACOPIO_DEL_DIA,
                860.0,
                unidades,
                "Quesería turno A",
                null,
                recetaParia.getId(),
                unidades,
                List.of(),
                null,
                null,
                null));
    }

    private VentaQueso vender(LoteQueso lote, int unidades, CondicionVenta condicion) {
        return vender(lote, unidades, condicion, TipoComprador.PUBLICO_GENERAL);
    }

    private VentaQueso vender(LoteQueso lote, int unidades, CondicionVenta condicion, TipoComprador comprador) {
        return queseriaService.registrarVenta(new VentaQuesoRequest(
                lote.getId(), "Mercado Huata", unidades, comprador, condicion, MedioCobro.EFECTIVO));
    }

    private int stockDe(LoteQueso lote) {
        return queseriaService.listarLotes(null, null).stream()
                .filter(l -> l.lote().getId().equals(lote.getId()))
                .findFirst()
                .orElseThrow()
                .stock();
    }

    // ---- Produccion ----

    @Test
    void elLoteNaceConCodigoPropioYSuStockEsTodoLoProducido() {
        LoteQueso lote = lote(86);

        assertThat(lote.getCodigo()).isEqualTo("L" + AYER.getYear() + "-" + String.format("%04d", lote.getId()));
        assertThat(stockDe(lote)).isEqualTo(86);
        assertThat(lote.rendimientoLitrosPorUnidad()).isEqualTo(10.0);
    }

    @Test
    void dosLotesNoCompartenCodigo() {
        assertThat(lote(10).getCodigo()).isNotEqualTo(lote(10).getCodigo());
    }

    @Test
    void noSeRegistraProduccionConFechaFutura() {
        assertThatThrownBy(() -> lote(10, HOY.plusDays(1)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("fecha futura");
    }

    @Test
    void elAltaDelLoteQuedaEnLaBitacora() {
        LoteQueso lote = lote(86);

        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .filteredOn(r -> r.getAccion().equals("Producción de quesos"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getActor()).isEqualTo("secretaria.demo@huata.pe");
                    assertThat(r.getDetalle()).contains(lote.getCodigo()).contains("86 u");
                });
    }

    // ---- La regla del stock ----

    @Test
    void laVentaDescuentaElStockDelLote() {
        LoteQueso lote = lote(86);

        vender(lote, 20, CondicionVenta.CONTADO);

        assertThat(stockDe(lote)).isEqualTo(66);
    }

    @Test
    void nadieVendeMasUnidadesDeLasQueQuedanEnCamara() {
        LoteQueso lote = lote(30);
        vender(lote, 25, CondicionVenta.CONTADO);

        assertThatThrownBy(() -> vender(lote, 6, CondicionVenta.CONTADO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("5 unidad(es) en cámara");

        // Y la que si cabe se registra, para que el rechazo no sea "el lote esta bloqueado".
        vender(lote, 5, CondicionVenta.CONTADO);
        assertThat(stockDe(lote)).isZero();
    }

    @Test
    void unLoteAgotadoYaNoSeOfreceParaVender() {
        LoteQueso lote = lote(10);
        vender(lote, 10, CondicionVenta.CONTADO);

        assertThat(queseriaService.lotesConStock())
                .noneSatisfy(l -> assertThat(l.lote().getId()).isEqualTo(lote.getId()));
    }

    /**
     * La venta ya no recibe fecha del formulario (siempre es HOY segun el servidor), asi que ya no
     * hay forma de pedir, a traves de la API, una venta fechada antes que su lote: crear un lote no
     * admite fecha futura, y HOY nunca es anterior a esa fecha. El chequeo en el servicio queda como
     * defensa ante un lote con fecha corrupta (una migracion vieja, un dato importado): se prueba
     * forzando ese estado directo por repositorio, sin pasar por el formulario.
     */
    @Test
    void noSeVendeQuesoAntesDeProducirloAunqueElLoteQuedeConFechaCorrupta() {
        LoteQueso lote = lote(10, HOY);
        lote.setFecha(HOY.plusDays(3));
        lote = loteRepository.save(lote);
        LoteQueso loteFinal = lote;

        assertThatThrownBy(() -> vender(loteFinal, 2, CondicionVenta.CONTADO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("todavía no existía");
    }

    /** El stock de cada lote sale de sus propias ventas, no de la suma de todas. */
    @Test
    void elStockDeUnLoteNoSeMezclaConElDeOtro() {
        LoteQueso uno = lote(50);
        LoteQueso otro = lote(50);

        vender(uno, 30, CondicionVenta.CONTADO);

        assertThat(stockDe(uno)).isEqualTo(20);
        assertThat(stockDe(otro)).isEqualTo(50);
    }

    // ---- Cobro ----

    @Test
    void laCondicionDecideElEstadoDeCobroYNoElFormulario() {
        LoteQueso lote = lote(50);

        assertThat(vender(lote, 5, CondicionVenta.CONTADO).getEstado()).isEqualTo(EstadoCobro.COBRADO);
        assertThat(vender(lote, 5, CondicionVenta.CREDITO_7D).getEstado()).isEqualTo(EstadoCobro.POR_COBRAR);
    }

    @Test
    void unaVentaACreditoSeMarcaCobradaUnaSolaVez() {
        VentaQueso venta = vender(lote(50), 5, CondicionVenta.CREDITO_7D);

        assertThat(queseriaService.marcarCobrada(venta.getId()).getEstado()).isEqualTo(EstadoCobro.COBRADO);
        assertThatThrownBy(() -> queseriaService.marcarCobrada(venta.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya estaba cobrada");
    }

    // ---- Anulacion ----

    @Test
    void anularUnaVentaDevuelveSusUnidadesAlLote() {
        LoteQueso lote = lote(40);
        VentaQueso venta = vender(lote, 15, CondicionVenta.CONTADO);
        assertThat(stockDe(lote)).isEqualTo(25);

        queseriaService.anularVenta(venta.getId(), new AnularVentaRequest("Cliente devolvió el pedido"));

        assertThat(stockDe(lote)).isEqualTo(40);
        assertThat(queseriaService.listarVentas(null, null))
                .noneSatisfy(v -> assertThat(v.getId()).isEqualTo(venta.getId()));
    }

    @Test
    void unaVentaAnuladaYaNoAdmiteCambios() {
        VentaQueso venta = vender(lote(40), 5, CondicionVenta.CREDITO_7D);
        queseriaService.anularVenta(venta.getId(), new AnularVentaRequest("Error de captura"));

        assertThatThrownBy(() -> queseriaService.marcarCobrada(venta.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anulada");
        assertThatThrownBy(() -> queseriaService.anularVenta(venta.getId(), new AnularVentaRequest("Otra vez")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anulada");
    }

    @Test
    void laAnulacionExigeMotivoYNoValeUnEspacioEnBlanco() {
        VentaQueso venta = vender(lote(40), 5, CondicionVenta.CONTADO);

        assertThatThrownBy(() -> queseriaService.anularVenta(venta.getId(), new AnularVentaRequest("   ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("motivo");
    }

    // ---- KPI ----

    @Test
    void elResumenCuentaLoVendidoLoPendienteYLoQueQuedaEnCamara() {
        LoteQueso lote = lote(100);
        vender(lote, 10, CondicionVenta.CONTADO); // 10 x 20 = 200, cobrado
        vender(lote, 5, CondicionVenta.CREDITO_7D); // 5 x 20 = 100, por cobrar

        ResumenVentasResponse resumen = queseriaService.resumen(null, null);

        assertThat(resumen.operaciones()).isEqualTo(2);
        assertThat(resumen.ventaAcumulada()).isEqualTo(300.0);
        assertThat(resumen.porCobrar()).isEqualTo(100.0);
        assertThat(resumen.stockDisponible()).isEqualTo(85);
    }

    @Test
    void unLoteSinVentasCuentaEnteroComoStockDisponible() {
        lote(12);

        assertThat(queseriaService.resumen(null, null).stockDisponible()).isEqualTo(12);
    }

    // ---- El precio lo pone la tarifa ----

    @Test
    void cadaTipoDeCompradorPagaElPrecioDeLaTarifa() {
        LoteQueso lote = lote(60);

        assertThat(vender(lote, 1, CondicionVenta.CONTADO, TipoComprador.PUBLICO_GENERAL).getPrecioUnitario())
                .isEqualTo(20.0);
        assertThat(vender(lote, 1, CondicionVenta.CONTADO, TipoComprador.MAYORISTA).getPrecioUnitario())
                .isEqualTo(15.0);
        assertThat(vender(lote, 1, CondicionVenta.CONTADO, TipoComprador.PROVEEDOR).getPrecioUnitario())
                .isEqualTo(13.0);
    }

    @Test
    void elTotalSaleDelPrecioDeLaTarifaYNoDeLoQueMandeLaPantalla() {
        VentaQueso venta = vender(lote(60), 4, CondicionVenta.CONTADO, TipoComprador.MAYORISTA);

        assertThat(venta.total()).isEqualTo(60.0);
    }

    /**
     * La venta conserva el precio con el que se cobro: si leyera la tarifa al mostrarse, subir el
     * precio manana reescribiria las notas de venta de ayer.
     */
    @Test
    void cambiarLosPreciosNoRetocaLasVentasYaRegistradas() {
        VentaQueso venta = vender(lote(60), 2, CondicionVenta.CONTADO, TipoComprador.PUBLICO_GENERAL);

        tarifa(30.0, 25.0, 20.0, HOY);

        assertThat(venta.getPrecioUnitario()).isEqualTo(20.0);
        assertThat(venta.total()).isEqualTo(40.0);
    }

    @Test
    void laTarifaNuevaRigeParaLasVentasSiguientes() {
        tarifa(30.0, 25.0, 20.0, HOY);

        assertThat(vender(lote(60), 1, CondicionVenta.CONTADO, TipoComprador.PUBLICO_GENERAL).getPrecioUnitario())
                .isEqualTo(30.0);
    }

    @Test
    void sinPreciosConfiguradosNoSeVende() {
        LoteQueso lote = lote(10);
        tarifaRepository.deleteAll();

        assertThatThrownBy(() -> vender(lote, 1, CondicionVenta.CONTADO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay precios de Queso paria configurados");
    }

    // ---- Precios por producto y promociones (V28) ----

    /** Cada producto tiene su lista: el yogur no se cobra al precio del queso. */
    @Test
    void cadaProductoTieneSuPropioPrecio() {
        Producto yogur = productoNuevo("Yogur natural");
        tarifa(yogur, 8.0, 7.0, 6.5, HOY.minusYears(1), null);

        assertThat(queseriaService.tarifaVigente(yogur, HOY).getPrecioPublicoGeneral()).isEqualTo(8.0);
        assertThat(queseriaService.tarifaVigente(paria, HOY).getPrecioPublicoGeneral()).isEqualTo(20.0);
    }

    /** Un producto nuevo sin lista de precios no hereda el de otro: no se vende hasta configurarlo. */
    @Test
    void unProductoSinPreciosNoSeVendeAunqueOtrosTenganPrecio() {
        Producto yogur = productoNuevo("Yogur de fresa");

        assertThatThrownBy(() -> queseriaService.tarifaVigente(yogur, HOY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(yogur.getNombre());
    }

    /**
     * La promocion (periodo con fecha de fin) rige solo entre sus fechas, y al terminar vuelve el
     * precio normal sin que nadie tenga que reponerlo. Antes vigenteHasta era informativo y una
     * promocion se habria quedado para siempre.
     *
     * <p>Se prueba contra {@code tarifaVigente} directamente y no vendiendo en distintas fechas: la
     * venta ya no acepta una fecha del formulario (siempre es HOY), asi que la unica forma de
     * ejercitar el precio de una fecha pasada es llamando a la resolucion de precio en si.
     */
    @Test
    void laPromocionRigeSoloDuranteSusFechasYLuegoVuelveElPrecioNormal() {
        tarifa(paria, 15.0, 12.0, 11.0, HOY.minusDays(5), AYER);

        assertThat(queseriaService.tarifaVigente(paria, HOY.minusDays(7)).getPrecioPublicoGeneral())
                .as("antes de la promocion").isEqualTo(20.0);
        assertThat(queseriaService.tarifaVigente(paria, HOY.minusDays(3)).getPrecioPublicoGeneral())
                .as("durante la promocion").isEqualTo(15.0);
        assertThat(queseriaService.tarifaVigente(paria, AYER).getPrecioPublicoGeneral())
                .as("ultimo dia, inclusive").isEqualTo(15.0);
        assertThat(queseriaService.tarifaVigente(paria, HOY).getPrecioPublicoGeneral())
                .as("terminada la promocion").isEqualTo(20.0);
    }

    /** La promocion de un producto no toca el precio de los demas. */
    @Test
    void laPromocionDeUnProductoNoCambiaElPrecioDeOtro() {
        Producto yogur = productoNuevo("Yogur griego");
        tarifa(yogur, 9.0, 8.0, 7.0, HOY.minusYears(1), null);

        tarifa(paria, 15.0, 12.0, 11.0, HOY, HOY.plusDays(10));

        assertThat(queseriaService.tarifaVigente(paria, HOY).getPrecioPublicoGeneral()).isEqualTo(15.0);
        assertThat(queseriaService.tarifaVigente(yogur, HOY).getPrecioPublicoGeneral()).isEqualTo(9.0);
    }

    /** Un nuevo precio normal que empieza durante una promocion la reemplaza desde ese dia. */
    @Test
    void siDosPeriodosCubrenLaMismaFechaMandaElQueEmpezoDespues() {
        tarifa(paria, 15.0, 12.0, 11.0, HOY.minusDays(5), HOY.plusDays(5));
        tarifa(paria, 25.0, 20.0, 18.0, HOY, null);

        assertThat(queseriaService.tarifaVigente(paria, HOY.minusDays(1)).getPrecioPublicoGeneral()).isEqualTo(15.0);
        assertThat(queseriaService.tarifaVigente(paria, HOY).getPrecioPublicoGeneral()).isEqualTo(25.0);
    }

    @Test
    void unaPromocionQueTerminaAntesDeEmpezarSeRechaza() {
        assertThatThrownBy(() -> tarifa(paria, 15.0, 12.0, 11.0, HOY, AYER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("fecha de fin");
    }

    @Test
    void noSePonePrecioAUnProductoDadoDeBaja() {
        Producto manjar = productoNuevo("Manjar");
        manjar.setActivo(false);
        productoRepository.save(manjar);

        assertThatThrownBy(() -> tarifa(manjar, 10.0, 9.0, 8.0, HOY, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dado de baja");
    }

    @Test
    void laPromocionQuedaEnLaBitacoraConSusFechas() {
        auditoriaRepository.deleteAll();

        tarifa(paria, 15.0, 12.0, 11.0, HOY, HOY.plusDays(7));

        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> assertThat(r.getDetalle())
                        .contains("Queso paria")
                        .contains("hasta " + HOY.plusDays(7))
                        .contains("promoción"));
    }

    @Test
    void elCambioDePreciosQuedaEnLaBitacora() {
        auditoriaRepository.deleteAll();

        tarifa(30.0, 25.0, 20.0, HOY);

        assertThat(auditoriaRepository.findAllByOrderByFechaDesc(PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getAccion()).isEqualTo("Precios de queso");
                    assertThat(r.getDetalle()).contains("público S/ 30.0").contains("proveedor S/ 20.0");
                });
    }

    @Test
    void elListadoDeLotesFiltraPorFecha() {
        LoteQueso viejo = lote(10, HOY.minusMonths(6));
        LoteQueso reciente = lote(10, HOY);

        List<QueseriaService.LoteConStock> ultimaSemana =
                queseriaService.listarLotes(HOY.minusDays(7), HOY);

        assertThat(ultimaSemana).anySatisfy(l -> assertThat(l.lote().getId()).isEqualTo(reciente.getId()));
        assertThat(ultimaSemana).noneSatisfy(l -> assertThat(l.lote().getId()).isEqualTo(viejo.getId()));
    }

    // ---- Costeo (V13: receta, consumo real y mano de obra) ----

    @Test
    void elCostoDelLoteSumaMateriaPrimaYManoDeObra() {
        TrabajadorProduccion trabajador = new TrabajadorProduccion();
        trabajador.setNombre("Juana Perez");
        trabajador = trabajadorProduccionRepository.save(trabajador);

        LoteQueso lote = queseriaService.crearLote(new LoteQuesoRequest(
                AYER,
                paria.getId(),
                OrigenLeche.ACOPIO_DEL_DIA,
                100.0,
                10,
                "Quesería turno A",
                null,
                recetaParia.getId(),
                10,
                List.of(new ManoObraItemRequest(trabajador.getId(), BigDecimal.valueOf(50))),
                null,
                null,
                null));

        // 10 ML de cuajo a S/ 2.50 = 25.00 de materia prima, mas 50.00 de mano de obra.
        assertThat(lote.getCostoMateriaPrima()).isEqualByComparingTo("25.00");
        assertThat(lote.getCostoManoObra()).isEqualByComparingTo("50.00");
        assertThat(lote.getCostoTotal()).isEqualByComparingTo("75.00");
        assertThat(lote.getCostoUnitario()).isEqualByComparingTo("7.50");
    }

    @Test
    void unLoteSinManoDeObraNoTieneCostoDeManoDeObra() {
        LoteQueso lote = lote(20);

        assertThat(lote.getCostoManoObra()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(lote.getCostoTotal()).isEqualTo(lote.getCostoMateriaPrima());
    }

    @Test
    void laMermaEsLaDiferenciaEntreElObjetivoYLoRealmenteProducido() {
        LoteQueso lote = queseriaService.crearLote(new LoteQuesoRequest(
                AYER,
                paria.getId(),
                OrigenLeche.ACOPIO_DEL_DIA,
                100.0,
                8,
                "Quesería turno A",
                null,
                recetaParia.getId(),
                10,
                List.of(),
                null,
                null,
                null));

        assertThat(lote.merma()).isEqualTo(2);
    }

    @Test
    void unLoteSinCantidadObjetivoNoTieneMerma() {
        // El helper lote(...) siempre manda un objetivo (igual a lo producido); aqui se arma la
        // peticion a mano para probar el caso en que no se informa objetivo alguno.
        LoteQueso lote = queseriaService.crearLote(new LoteQuesoRequest(
                AYER,
                paria.getId(),
                OrigenLeche.ACOPIO_DEL_DIA,
                100.0,
                12,
                "Quesería turno A",
                null,
                recetaParia.getId(),
                null,
                List.of(),
                null,
                null,
                null));

        assertThat(lote.merma()).isNull();
    }

    @Test
    void noSePuedeCrearUnLoteSiFaltaStockDeMateriaPrima() {
        // La receta pide 1 ML de cuajo por unidad y hay 1 000 000: poner a producir 2 000 000 no alcanza.
        assertThatThrownBy(() -> queseriaService.crearLote(new LoteQuesoRequest(
                        AYER,
                        paria.getId(),
                        OrigenLeche.ACOPIO_DEL_DIA,
                        100.0,
                        5,
                        "Quesería turno A",
                        null,
                        recetaParia.getId(),
                        2_000_000,
                        List.of(),
                        null,
                        null,
                        null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("stock suficiente");
    }

    @Test
    void crearUnLoteDescuentaElConsumoDelKardexDeMateriaPrima() {
        BigDecimal antes = inventarioService.saldoMateriaPrima(cuajo.getId());

        lote(15);

        assertThat(inventarioService.saldoMateriaPrima(cuajo.getId())).isEqualByComparingTo(antes.subtract(BigDecimal.valueOf(15)));
    }

    @Test
    void laRecetaDebeCorresponderAlProductoDelLote() {
        RecetaQueso recetaFresco = new RecetaQueso();
        recetaFresco.setProducto(productoRepository.findByNombreIgnoreCase("Queso fresco").orElseThrow());
        recetaFresco.setVigenteDesde(HOY.minusYears(1));
        RecetaItem item = new RecetaItem();
        item.setReceta(recetaFresco);
        item.setMateriaPrima(cuajo);
        item.setCantidadPorUnidad(BigDecimal.ONE);
        recetaFresco.getItems().add(item);
        RecetaQueso recetaFrescoGuardada = recetaQuesoRepository.save(recetaFresco);

        assertThatThrownBy(() -> queseriaService.crearLote(new LoteQuesoRequest(
                        AYER,
                        paria.getId(),
                        OrigenLeche.ACOPIO_DEL_DIA,
                        100.0,
                        5,
                        "Quesería turno A",
                        null,
                        recetaFrescoGuardada.getId(),
                        null,
                        List.of(),
                        null,
                        null,
                        null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Queso fresco")
                .hasMessageContaining("Queso paria");
    }

    // ---- Contabilidad (V21) ----

    @Test
    void crearUnLoteGeneraLosAsientosDeConsumoManoDeObraYTerminado() {
        LoteQueso lote = lote(10); // 10 unidades, receta 1 ML cuajo/unidad a S/2.50 = S/25.00, sin mano de obra

        List<AsientoContableResponse> asientos = asientoContableService.libroDiario(AYER, AYER).stream()
                .filter(a -> lote.getId().equals(a.origenId()))
                .toList();

        assertThat(asientos)
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.PRODUCCION_CONSUMO);
                    assertThat(a.totalDebe()).isEqualByComparingTo("25.00");
                })
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.PRODUCCION_TERMINADO);
                    assertThat(a.totalDebe()).isEqualByComparingTo("25.00");
                });
        // Sin mano de obra en el helper: no debe generarse un asiento PRODUCCION_MANO_OBRA vacio.
        assertThat(asientos).noneMatch(a -> a.origenTipo() == OrigenAsiento.PRODUCCION_MANO_OBRA);
    }

    @Test
    void unaVentaAlContadoGeneraElAsientoDeIngresoConIgvYElDeCostoDeVenta() {
        LoteQueso lote = lote(10);
        VentaQueso venta = vender(lote, 4, CondicionVenta.CONTADO); // 4 u x S/20 = S/80.00 base

        assertThat(venta.getMontoIgv()).isEqualTo(14.4); // 18% de 80.00

        List<AsientoContableResponse> asientos = asientoContableService.libroDiario(HOY, HOY).stream()
                .filter(a -> venta.getId().equals(a.origenId()))
                .toList();

        assertThat(asientos)
                .anySatisfy(a -> {
                    assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.VENTA);
                    // 80.00 base + 14.40 IGV = 94.40, cobrado al contado (cuenta 1011).
                    assertThat(a.totalDebe()).isEqualByComparingTo("94.40");
                    assertThat(a.lineas()).anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("1011"));
                })
                .anySatisfy(a -> assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.VENTA_COSTO));
    }

    @Test
    void anularUnaVentaReversaSusAsientosContables() {
        LoteQueso lote = lote(10);
        VentaQueso venta = vender(lote, 4, CondicionVenta.CONTADO);

        queseriaService.anularVenta(venta.getId(), new AnularVentaRequest("Cliente devolvió el pedido"));

        List<AsientoContableResponse> anulaciones = asientoContableService.libroDiario(HOY, HOY).stream()
                .filter(a -> venta.getId().equals(a.origenId()) && a.origenTipo() == OrigenAsiento.ANULACION_VENTA)
                .toList();

        assertThat(anulaciones).hasSize(2);
        assertThat(anulaciones).allSatisfy(a -> assertThat(a.totalDebe()).isEqualByComparingTo(a.totalHaber()));
    }

    // ---- Vencimiento (V33) ----

    private LoteQueso loteQueVence(int unidades, LocalDate fecha, int diasQueDura) {
        paria.setVidaUtilDias(diasQueDura);
        productoRepository.saveAndFlush(paria);
        return lote(unidades, fecha);
    }

    @Test
    void elLoteVenceSegunLosDiasQueDuraElProducto() {
        LoteQueso lote = loteQueVence(10, HOY.minusDays(3), 10);
        assertThat(lote.getFechaVencimiento()).isEqualTo(HOY.plusDays(7));
    }

    @Test
    void unProductoSinDiasDeDuracionNoSeProduceNiSeInventaUnVencimiento() {
        paria.setVidaUtilDias(null);
        productoRepository.saveAndFlush(paria);
        assertThatThrownBy(() -> lote(10))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cuántos días dura");
    }

    @Test
    void noSeVendeUnLoteVencido() {
        LoteQueso lote = loteQueVence(10, HOY.minusDays(5), 2);

        assertThatThrownBy(() -> vender(lote, 1, CondicionVenta.CONTADO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venció");
    }

    /** FEFO: primero se ofrece lo que vence antes; lo vencido ya no se ofrece. */
    @Test
    void paraVenderSeOfrecePrimeroLoQueVenceAntesYNoLoVencido() {
        LoteQueso vencido = loteQueVence(5, HOY.minusDays(9), 3);
        LoteQueso vencePronto = loteQueVence(5, HOY.minusDays(2), 4);
        LoteQueso venceDespues = loteQueVence(5, HOY.minusDays(1), 20);

        List<Long> ofrecidos = queseriaService.lotesParaVender(HOY).stream()
                .map(l -> l.lote().getId())
                .filter(id -> List.of(vencido.getId(), vencePronto.getId(), venceDespues.getId()).contains(id))
                .toList();

        assertThat(ofrecidos).containsExactly(vencePronto.getId(), venceDespues.getId());
    }

    @Test
    void laFechaDeVencimientoNoPuedeSerAnteriorALaProduccion() {
        assertThatThrownBy(() -> queseriaService.crearLote(new LoteQuesoRequest(
                        AYER, paria.getId(), OrigenLeche.ACOPIO_DEL_DIA, 100.0, 5, "Turno A", null,
                        recetaParia.getId(), 5,
                        List.of(), null, null, null, AYER.minusDays(1))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vencimiento");
    }

    // ---- Historial por cliente ----

    private VentaQueso venderA(LoteQueso lote, String cliente, int unidades, CondicionVenta condicion) {
        return queseriaService.registrarVenta(new VentaQuesoRequest(
                lote.getId(), cliente, unidades, TipoComprador.PUBLICO_GENERAL, condicion, MedioCobro.EFECTIVO));
    }

    /** El cliente es texto libre: "Bodega Rosa" y " bodega rosa " son el mismo cliente. */
    @Test
    void losClientesSeAgrupanSinImportarMayusculasNiEspacios() {
        LoteQueso lote = lote(50);
        venderA(lote, "Bodega Rosa QA", 3, CondicionVenta.CONTADO);
        venderA(lote, "  bodega rosa qa ", 2, CondicionVenta.CREDITO_7D);
        venderA(lote, "Otro cliente QA", 1, CondicionVenta.CONTADO);

        var rosa = queseriaService.clientes(HOY.minusDays(1), HOY).stream()
                .filter(c -> c.cliente().equalsIgnoreCase("bodega rosa qa"))
                .findFirst()
                .orElseThrow();

        assertThat(rosa.compras()).isEqualTo(2);
        assertThat(rosa.unidades()).isEqualTo(5);
        assertThat(rosa.total()).isEqualTo(100.0);
        assertThat(rosa.porCobrar()).isEqualTo(40.0);
        assertThat(rosa.productos()).containsExactly("Queso paria");
    }

    /** El historial muestra tambien las anuladas: son parte de lo que paso con ese cliente. */
    @Test
    void elHistorialDeUnClienteIncluyeSusVentasAnuladas() {
        LoteQueso lote = lote(50);
        VentaQueso v1 = venderA(lote, "Cliente Historial QA", 3, CondicionVenta.CONTADO);
        venderA(lote, "Cliente Historial QA", 2, CondicionVenta.CONTADO);
        queseriaService.anularVenta(v1.getId(), new AnularVentaRequest("Devolución"));

        List<VentaQueso> historial = queseriaService.historialCliente("CLIENTE HISTORIAL QA", null, null, null);

        assertThat(historial).hasSize(2);
        assertThat(historial).filteredOn(v -> !v.isActivo()).hasSize(1);
        assertThat(queseriaService.historialCliente("Cliente Historial QA", productoNuevo("Otro").getId(), null, null))
                .isEmpty();
    }
}
