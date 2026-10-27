package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.ramiro.huata.quesos.LoteQuesoRequest;
import org.ramiro.huata.quesos.OrigenLeche;
import org.ramiro.huata.quesos.QueseriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * "Todo en una sola transaccion: si algo falla, no se descuenta nada."
 *
 * <p><b>Sin {@code @Transactional} a proposito</b>, igual que {@code ComprasAtomicasTests}: dentro de
 * una transaccion de prueba no se distingue "revirtio el servicio" de "revirtio el test". Aqui se
 * escribe de verdad y se limpia en {@link #limpiar()}.
 *
 * <p>El escenario que importa: el insumo que se procesa PRIMERO (id menor, ver
 * {@link CalculoProduccion#consumoPara}) si alcanza y ya se desconto cuando el SEGUNDO falla. Un
 * servicio mal transaccionado dejaria el primero descontado sin lote que lo explique.
 */
@SpringBootTest
@WithMockUser(username = "admin@huata.pe")
class ProduccionAtomicaTests {

    private static final LocalDate AYER = LocalDate.now().minusDays(1);

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
    private JdbcTemplate jdbc;

    private Producto producto;
    private MateriaPrima primera;
    private MateriaPrima segunda;
    private RecetaQueso receta;

    @BeforeEach
    void escenario() {
        producto = new Producto();
        producto.setNombre("Queso atomico " + System.nanoTime());
        producto.setUnidadVenta("unidad");
        producto.setVidaUtilDias(30);
        producto = productoRepository.saveAndFlush(producto);

        // Se crean en este orden a proposito: "primera" tiene el id menor y se descuenta antes.
        primera = materia("Leche atomica", "100");
        segunda = materia("Sal atomica", "1");

        receta = new RecetaQueso();
        receta.setProducto(producto);
        receta.setVigenteDesde(AYER.minusYears(1));
        for (MateriaPrima m : List.of(primera, segunda)) {
            RecetaItem item = new RecetaItem();
            item.setReceta(receta);
            item.setMateriaPrima(m);
            item.setCantidadPorUnidad(BigDecimal.ONE);
            receta.getItems().add(item);
        }
        receta = recetaRepository.saveAndFlush(receta);
    }

    /** Primero lo que referencia y despues lo referenciado, o la clave ajena lo impide. */
    @AfterEach
    void limpiar() {
        String lotes = "select id from lotes_queso where producto_id = " + producto.getId();
        jdbc.update("delete from asiento_lineas where asiento_id in (select id from asientos_contables"
                + " where origen_tipo like 'PRODUCCION%' and origen_id in (" + lotes + "))");
        jdbc.update("delete from asientos_contables where origen_tipo like 'PRODUCCION%' and origen_id in (" + lotes + ")");
        jdbc.update("delete from movimientos_inventario where lote_queso_id in (" + lotes + ")");
        jdbc.update("delete from movimientos_inventario where materia_prima_id in (?, ?)", primera.getId(), segunda.getId());
        jdbc.update("delete from lote_consumo where lote_id in (" + lotes + ")");
        jdbc.update("delete from lotes_queso where producto_id = ?", producto.getId());
        jdbc.update("delete from receta_items where receta_id = ?", receta.getId());
        jdbc.update("delete from recetas_queso where id = ?", receta.getId());
        jdbc.update("delete from materias_primas where id in (?, ?)", primera.getId(), segunda.getId());
        jdbc.update("delete from productos where id = ?", producto.getId());
    }

    private MateriaPrima materia(String nombre, String stock) {
        MateriaPrima m = new MateriaPrima();
        m.setNombre(nombre + " " + System.nanoTime());
        m.setUnidadMedida(UnidadMedida.UNIDAD);
        m.setCostoUnitarioReferencial(BigDecimal.ONE);
        m = materiaPrimaRepository.saveAndFlush(m);
        inventarioService.registrarEntradaCompra(m, new BigDecimal(stock), AYER.minusDays(1), 0L);
        return m;
    }

    private void producir(int unidades) {
        // La receta no lleva la leche del acopio (materias de prueba), asi que los litros van a mano.
        queseriaService.crearLote(new LoteQuesoRequest(
                AYER, producto.getId(), OrigenLeche.ACOPIO_DEL_DIA, 50.0, unidades, "Turno A", null,
                receta.getId(), unidades, List.of(), null, null, null));
    }

    private BigDecimal saldo(MateriaPrima m) {
        return inventarioService.saldoMateriaPrima(m.getId());
    }

    private int lotesDelProducto() {
        return jdbc.queryForObject("select count(*) from lotes_queso where producto_id = ?", Integer.class, producto.getId());
    }

    /** Control positivo: sin el, un "no quedo nada" seria indistinguible de "no se ve nada". */
    @Test
    void unaProduccionQueAlcanzaSiDejaTodoEscrito() {
        producir(1);

        assertThat(saldo(primera)).isEqualByComparingTo("99");
        assertThat(saldo(segunda)).isEqualByComparingTo("0");
        assertThat(lotesDelProducto()).isEqualTo(1);
    }

    @Test
    void siUnInsumoNoAlcanzaNoSeDescuentaNingunoNiQuedaElLote() {
        assertThat(primera.getId()).isLessThan(segunda.getId());

        assertThatThrownBy(() -> producir(5))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(segunda.getNombre());

        assertThat(saldo(primera)).isEqualByComparingTo("100");
        assertThat(saldo(segunda)).isEqualByComparingTo("1");
        assertThat(lotesDelProducto()).isZero();
    }
}
