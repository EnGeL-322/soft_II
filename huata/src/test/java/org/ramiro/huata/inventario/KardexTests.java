package org.ramiro.huata.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.quesos.LoteQueso;
import org.ramiro.huata.quesos.LoteQuesoRepository;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.produccion.ProductoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * El kardex: como se calcula el saldo y que deja anotado cada movimiento.
 *
 * <p><b>Por que existe.</b> El paquete {@code inventario} no tenia ninguna prueba propia, y es el
 * kernel compartido: {@code compras}, {@code produccion} y {@code quesos} dependen de que el saldo se
 * calcule igual para todos. La regla que sostiene todo es que la <b>cantidad se guarda siempre
 * positiva</b> y la direccion la decide el tipo de movimiento; si alguien cambiara eso, una salida
 * empezaria a sumar stock y el sintoma seria inventario que crece al vender.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class KardexTests {

    private static final LocalDate HOY = LocalDate.now();
    private static final Long DOCUMENTO_DE_REFERENCIA = 1L;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private LoteQuesoRepository loteQuesoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    private MateriaPrima materiaPrima;

    @BeforeEach
    void escenario() {
        MateriaPrima mp = new MateriaPrima();
        mp.setNombre("Insumo kardex " + System.nanoTime());
        mp.setUnidadMedida(UnidadMedida.KG);
        materiaPrima = materiaPrimaRepository.saveAndFlush(mp);
    }

    private BigDecimal saldo() {
        return inventarioService.saldoMateriaPrima(materiaPrima.getId());
    }

    // --------------------------------------------------------------- Saldo de materia prima

    /** Sin movimientos el saldo es cero, no nulo: la pantalla tiene que poder restar contra el. */
    @Test
    void unaMateriaPrimaSinMovimientosTieneSaldoCeroYNoNulo() {
        assertThat(saldo()).isNotNull().isEqualByComparingTo("0");
    }

    @Test
    void unaEntradaDeCompraSuma() {
        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("40"), HOY, DOCUMENTO_DE_REFERENCIA);

        assertThat(saldo()).isEqualByComparingTo("40");
    }

    /**
     * La regla central: la salida se registra con cantidad <b>positiva</b> y aun asi resta. Si algun
     * dia se guardara negativa "para que reste", restaria dos veces.
     */
    @Test
    void unaSalidaDeConsumoRestaAunqueLaCantidadSeGuardePositiva() {
        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("40"), HOY, DOCUMENTO_DE_REFERENCIA);

        MovimientoInventario salida =
                inventarioService.registrarSalidaProduccionConsumo(materiaPrima, new BigDecimal("15"), HOY, 7L);

        assertThat(salida.getCantidad()).isEqualByComparingTo("15");
        assertThat(salida.getTipo().esEntrada()).isFalse();
        assertThat(saldo()).isEqualByComparingTo("25");
    }

    @Test
    void variosMovimientosSeAcumulanEnElOrdenQueSea() {
        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("30"), HOY, DOCUMENTO_DE_REFERENCIA);
        inventarioService.registrarSalidaProduccionConsumo(materiaPrima, new BigDecimal("10"), HOY, 7L);
        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("5"), HOY, DOCUMENTO_DE_REFERENCIA);
        inventarioService.registrarSalidaProduccionConsumo(materiaPrima, new BigDecimal("2.5"), HOY, 8L);

        assertThat(saldo()).isEqualByComparingTo("22.5");
    }

    /**
     * El saldo puede quedar negativo si alguien registra una salida mayor que el stock. El kardex no lo
     * impide: quien lo impide es el servicio que orquesta la operacion ({@code QueseriaService} verifica
     * el disponible antes de consumir). Queda fijado aqui para que se sepa donde vive la guarda —
     * <b>no</b> es una invitacion a saltarse esa verificacion llamando al kardex directamente.
     */
    @Test
    void elKardexNoImpidePorSiSoloUnSaldoNegativo() {
        inventarioService.registrarSalidaProduccionConsumo(materiaPrima, new BigDecimal("5"), HOY, 7L);

        assertThat(saldo()).isEqualByComparingTo("-5");
    }

    // ------------------------------------------------------------ Saldos en lote (una consulta)

    /** El saldo por lotes tiene que dar lo mismo que preguntarlo uno por uno. */
    @Test
    void elSaldoEnLoteCoincideConElIndividual() {
        MateriaPrima otra = new MateriaPrima();
        otra.setNombre("Otro insumo kardex " + System.nanoTime());
        otra.setUnidadMedida(UnidadMedida.LITRO);
        otra = materiaPrimaRepository.saveAndFlush(otra);

        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("40"), HOY, DOCUMENTO_DE_REFERENCIA);
        inventarioService.registrarEntradaCompra(otra, new BigDecimal("12"), HOY, DOCUMENTO_DE_REFERENCIA);

        var saldos = inventarioService.saldosMateriasPrimas(List.of(materiaPrima.getId(), otra.getId()));

        assertThat(saldos.get(materiaPrima.getId())).isEqualByComparingTo("40");
        assertThat(saldos.get(otra.getId())).isEqualByComparingTo("12");
    }

    /**
     * Una materia prima sin movimientos no aparece en el mapa agregado (la consulta agrupa sobre filas
     * que existen). Quien lo use tiene que leerlo con un valor por defecto, y por eso se fija: es la
     * diferencia entre "faltan 50" y un NullPointerException en pantalla.
     */
    @Test
    void enElMapaAgregadoUnaMateriaPrimaSinMovimientosSimplementeNoApareceYSeLeeComoCero() {
        var saldos = inventarioService.saldosMateriasPrimas(List.of(materiaPrima.getId()));

        assertThat(saldos.getOrDefault(materiaPrima.getId(), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    @Test
    void pedirSaldosDeUnaListaVaciaNoRompe() {
        assertThat(inventarioService.saldosMateriasPrimas(List.of())).isEmpty();
    }

    // ------------------------------------------------------------------------- Trazabilidad

    /**
     * La seccion 14 lo pide explicitamente: todo movimiento debe indicar origen, documento/lote,
     * usuario, fecha y cantidad. Sin el usuario y el documento, un descuadre de inventario no se puede
     * investigar — solo constatar.
     */
    @Test
    void cadaMovimientoDejaOrigenDocumentoUsuarioYFecha() {
        MovimientoInventario movimiento =
                inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal("40"), HOY, 33L);

        assertThat(movimiento.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA_COMPRA);
        assertThat(movimiento.getDocumentoOrigenTipo()).isEqualTo(DocumentoOrigenTipo.COMPRA);
        assertThat(movimiento.getDocumentoOrigenId()).isEqualTo(33L);
        assertThat(movimiento.getFecha()).isEqualTo(HOY);
        assertThat(movimiento.getUsuario()).isEqualTo("admin@huata.pe");
        assertThat(movimiento.getMateriaPrima().getId()).isEqualTo(materiaPrima.getId());
    }

    /** Un consumo de produccion queda atado a su lote (origen PRODUCCION), que es como se reconstruye el costo del lote. */
    @Test
    void elConsumoDeProduccionQuedaAtadoASuLote() {
        MovimientoInventario movimiento =
                inventarioService.registrarSalidaProduccionConsumo(materiaPrima, new BigDecimal("8"), HOY, 55L);

        assertThat(movimiento.getDocumentoOrigenTipo()).isEqualTo(DocumentoOrigenTipo.PRODUCCION);
        assertThat(movimiento.getDocumentoOrigenId()).isEqualTo(55L);
    }

    // ------------------------------------------------------------------ Saldo de queso terminado

    /**
     * El stock de un lote de queso: entra al cerrar la produccion, sale al vender y vuelve a entrar si
     * la venta se anula. Es el mismo kardex, con otra columna.
     */
    @Test
    void elStockDeUnLoteEsProduccionMenosVentasMasAnulaciones() {
        LoteQueso lote = lote("LT-KDX-" + (System.nanoTime() % 100000));

        inventarioService.registrarEntradaProduccionTerminado(lote, new BigDecimal("100"), HOY);
        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("100");

        inventarioService.registrarSalidaVenta(lote, new BigDecimal("30"), HOY, 9L);
        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("70");

        inventarioService.registrarEntradaAnulacionVenta(lote, new BigDecimal("30"), HOY, 9L, "Cliente devolvio");
        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("100");
    }

    /** La anulacion deja dicho por que: es el unico rastro de que ese queso volvio a la camara. */
    @Test
    void laAnulacionDeVentaAnotaSuMotivo() {
        LoteQueso lote = lote("LT-ANU-" + (System.nanoTime() % 100000));
        inventarioService.registrarEntradaProduccionTerminado(lote, new BigDecimal("10"), HOY);

        MovimientoInventario anulacion =
                inventarioService.registrarEntradaAnulacionVenta(lote, new BigDecimal("4"), HOY, 9L, "Error de digitacion");

        assertThat(anulacion.getObservaciones()).contains("Error de digitacion");
        assertThat(anulacion.getTipo().esEntrada()).isTrue();
    }

    /** Un lote sin produccion registrada no tiene stock, aunque el lote exista. */
    @Test
    void unLoteSinEntradaDeProduccionNoTieneStock() {
        LoteQueso lote = lote("LT-VAC-" + (System.nanoTime() % 100000));

        assertThat(inventarioService.saldoLoteQueso(lote.getId())).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------------- Bloqueo

    /**
     * {@code bloquearMateriaPrima} es lo que evita el sobreconsumo concurrente. Aqui solo se fija su
     * contrato de rechazo: lo que no existe o esta de baja no se puede bloquear para consumir. La
     * exclusion entre dos lotes simultaneos no se puede comprobar con una sola transaccion.
     */
    @Test
    void noSePuedeBloquearUnaMateriaPrimaInexistente() {
        assertThatThrownBy(() -> inventarioService.bloquearMateriaPrima(999_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void noSePuedeBloquearUnaMateriaPrimaDadaDeBaja() {
        materiaPrima.setActivo(false);
        materiaPrimaRepository.saveAndFlush(materiaPrima);

        assertThatThrownBy(() -> inventarioService.bloquearMateriaPrima(materiaPrima.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void bloquearUnaMateriaPrimaActivaLaDevuelve() {
        assertThat(inventarioService.bloquearMateriaPrima(materiaPrima.getId()).getId())
                .isEqualTo(materiaPrima.getId());
    }

    // ------------------------------------------------------------------------------ Apoyo

    /** Lote minimo valido: solo lo que el esquema exige, porque aqui lo que se mide es el kardex. */
    private LoteQueso lote(String codigo) {
        LoteQueso lote = new LoteQueso();
        lote.setCodigo(codigo);
        lote.setFecha(HOY);
        lote.setProducto(productoRepository.findByNombreIgnoreCase("Queso fresco").orElseThrow());
        lote.setOrigenLeche(OrigenLeche.ACOPIO_DEL_DIA);
        lote.setLitrosDestinados(650.0);
        lote.setUnidadesProducidas(100);
        lote.setResponsable("Responsable de prueba");
        return loteQuesoRepository.saveAndFlush(lote);
    }

    /**
     * La consulta de saldo de materia prima lista a mano los tipos que SUMAN. Si se agrega un tipo de
     * entrada y se olvida ahi, ese movimiento resta: paso con ENTRADA_ACOPIO (V31) y la leche recibida
     * aparecia en negativo. Esta prueba ata la lista al enum.
     */
    @Test
    void laConsultaDeSaldoSumaTodasLasEntradasDeMateriaPrima() throws Exception {
        String consulta = MovimientoInventarioRepository.class
                .getMethod("saldoMateriaPrima", Long.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value();
        // Las dos entradas propias del queso terminado nunca van en una fila de materia prima.
        java.util.Set<TipoMovimientoInventario> soloDeQueso = java.util.EnumSet.of(
                TipoMovimientoInventario.ENTRADA_PRODUCCION_TERMINADO, TipoMovimientoInventario.ENTRADA_ANULACION_VENTA);

        for (TipoMovimientoInventario tipo : TipoMovimientoInventario.values()) {
            boolean enLaLista = consulta.contains("'" + tipo.name() + "'");
            boolean debeSumar = tipo.esEntrada() && !soloDeQueso.contains(tipo);
            assertThat(enLaLista).as(tipo + " en la lista de entradas del saldo").isEqualTo(debeSumar);
        }
    }
}
