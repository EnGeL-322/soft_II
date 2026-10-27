package org.ramiro.huata.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.contabilidad.AsientoContableRepository;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.MovimientoInventarioRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * RNF-18 de verdad: una compra que falla a mitad de camino <b>no deja existencias parciales</b>.
 *
 * <p><b>Sin {@code @Transactional} a proposito, y es el punto entero de esta clase.</b> El resto de las
 * pruebas de compras corre dentro de una transaccion de test que revierte al terminar: ahi es
 * imposible distinguir "el servicio revirtio" de "revirtio el test". Aqui cada caso escribe de verdad,
 * comprueba lo que quedo en la base y limpia en {@link #limpiar()}.
 *
 * <p>Lo que se protege es concreto: una compra de tres insumos donde el tercero esta mal no puede
 * dejar el stock de los dos primeros aumentado. Si pasara, el kardex diria que hay materia prima que
 * nunca entro, y el error se descubriria semanas despues al cuadrar el inventario fisico — sin ningun
 * rastro de por que.
 */
@SpringBootTest
@WithMockUser(username = "admin@huata.pe")
class ComprasAtomicasTests {

    private static final LocalDate HOY = LocalDate.now();
    private static final String DOCUMENTO = "ATOMICA-TEST";

    @Autowired
    private CompraService compraService;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private ProveedorCompraRepository proveedorRepository;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoRepository;

    @Autowired
    private AsientoContableRepository asientoRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private JdbcTemplate jdbc;

    private ProveedorCompra proveedor;
    private MateriaPrima primera;
    private MateriaPrima segunda;

    @BeforeEach
    void escenario() {
        proveedor = new ProveedorCompra();
        proveedor.setCodigo("PC-ATOM-" + System.nanoTime() % 100000);
        proveedor.setNombre("Insumos Atomicos SAC");
        proveedor = proveedorRepository.saveAndFlush(proveedor);

        primera = materiaPrima("Cuajo atomico");
        segunda = materiaPrima("Sal atomica");
    }

    /**
     * Se borra a mano lo que cada caso haya escrito. El orden importa: primero lo que referencia y
     * despues lo referenciado, o la clave ajena lo impide.
     */
    @AfterEach
    void limpiar() {
        jdbc.update("delete from asiento_lineas where asiento_id in"
                + " (select id from asientos_contables where origen_tipo = 'COMPRA' and origen_id in"
                + " (select id from compras where documento = ?))", DOCUMENTO);
        jdbc.update("delete from asientos_contables where origen_tipo = 'COMPRA' and origen_id in"
                + " (select id from compras where documento = ?)", DOCUMENTO);
        jdbc.update("delete from movimientos_inventario where materia_prima_id in (?, ?)", primera.getId(), segunda.getId());
        jdbc.update("delete from compra_items where compra_id in (select id from compras where documento = ?)", DOCUMENTO);
        jdbc.update("delete from compras where documento = ?", DOCUMENTO);
        jdbc.update("delete from materias_primas where id in (?, ?)", primera.getId(), segunda.getId());
        jdbc.update("delete from proveedores_compra where id = ?", proveedor.getId());
    }

    private MateriaPrima materiaPrima(String nombre) {
        MateriaPrima mp = new MateriaPrima();
        mp.setNombre(nombre + " " + System.nanoTime());
        mp.setUnidadMedida(UnidadMedida.KG);
        return materiaPrimaRepository.saveAndFlush(mp);
    }

    private static CompraItemRequest item(Long materiaPrimaId, String cantidad) {
        return new CompraItemRequest(materiaPrimaId, new BigDecimal(cantidad), new BigDecimal("10.00"));
    }

    private CompraRequest compraCon(List<CompraItemRequest> items) {
        return new CompraRequest(proveedor.getId(), HOY, DOCUMENTO, null, items);
    }

    private BigDecimal saldo(MateriaPrima mp) {
        return inventarioService.saldoMateriaPrima(mp.getId());
    }

    private long comprasConEseDocumento() {
        return compraRepository.findAll().stream()
                .filter(c -> DOCUMENTO.equals(c.getDocumento()))
                .count();
    }

    /**
     * Control positivo. Va primero porque sin el, el resto de la clase no probaria nada: si la prueba
     * no fuera capaz de ver los efectos ya confirmados, un "no quedo nada" seria indistinguible de
     * "no se ve nada".
     */
    @Test
    void unaCompraCorrectaSiDejaTodoEscrito() {
        compraService.registrar(compraCon(List.of(item(primera.getId(), "40"), item(segunda.getId(), "15"))));

        assertThat(saldo(primera)).isEqualByComparingTo("40");
        assertThat(saldo(segunda)).isEqualByComparingTo("15");
        assertThat(comprasConEseDocumento()).isEqualTo(1);
        assertThat(asientoRepository.findAll())
                .anySatisfy(a -> assertThat(a.getOrigenTipo()).isEqualTo(OrigenAsiento.COMPRA));
    }

    /**
     * El caso que importa: el segundo item apunta a una materia prima que no existe. El primero ya
     * habia generado su entrada al kardex cuando el segundo falla, asi que esta es exactamente la
     * situacion en la que un servicio sin transaccion dejaria stock inventado.
     */
    @Test
    void siUnItemFallaNoQuedaStockDeLosItemsAnteriores() {
        assertThatThrownBy(() -> compraService.registrar(
                        compraCon(List.of(item(primera.getId(), "40"), item(999_999L, "15")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(saldo(primera)).isEqualByComparingTo("0");
        assertThat(movimientoRepository.findAll())
                .noneSatisfy(m -> assertThat(m.getMateriaPrima().getId()).isEqualTo(primera.getId()));
    }

    /** Y tampoco queda la cabecera de la compra: ni media compra ni un documento huerfano. */
    @Test
    void siUnItemFallaNoQuedaLaCabeceraDeLaCompra() {
        assertThatThrownBy(() -> compraService.registrar(
                        compraCon(List.of(item(primera.getId(), "40"), item(999_999L, "15")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(comprasConEseDocumento()).isZero();
    }

    /**
     * Un insumo dado de baja tambien aborta la compra completa. Es el mismo fallo a mitad de camino
     * pero por una causa que si ocurre en la operacion real: alguien inactiva una materia prima
     * mientras otro esta registrando una compra que la incluye.
     */
    @Test
    void unInsumoDadoDeBajaEnMedioDeLaCompraLaRevierteCompleta() {
        segunda.setActivo(false);
        materiaPrimaRepository.saveAndFlush(segunda);

        assertThatThrownBy(() -> compraService.registrar(
                        compraCon(List.of(item(primera.getId(), "40"), item(segunda.getId(), "15")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(saldo(primera)).isEqualByComparingTo("0");
        assertThat(saldo(segunda)).isEqualByComparingTo("0");
        assertThat(comprasConEseDocumento()).isZero();
    }

    /**
     * El asiento contable se escribe dentro de la misma transaccion, asi que tampoco puede sobrevivir
     * a un fallo. Un asiento sin su compra descuadraria el libro diario contra el kardex.
     */
    @Test
    void siLaCompraFallaNoQuedaAsientoContableSuelto() {
        long asientosAntes = asientoRepository.count();

        assertThatThrownBy(() -> compraService.registrar(
                        compraCon(List.of(item(primera.getId(), "40"), item(999_999L, "15")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(asientoRepository.count()).isEqualTo(asientosAntes);
    }

    /**
     * El rechazo por fecha futura ocurre antes de tocar nada, pero conviene fijar que asi sea: si
     * algun dia la validacion se moviera despues de guardar la cabecera, el sintoma seria una compra
     * fantasma sin items.
     */
    @Test
    void unaCompraConFechaFuturaNoEscribeNada() {
        assertThatThrownBy(() -> compraService.registrar(new CompraRequest(
                        proveedor.getId(), HOY.plusDays(3), DOCUMENTO, null, List.of(item(primera.getId(), "40")))))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(comprasConEseDocumento()).isZero();
        assertThat(saldo(primera)).isEqualByComparingTo("0");
    }

    /** Un proveedor inexistente falla antes de escribir la cabecera. */
    @Test
    void unProveedorInexistenteNoDejaCompraNiMovimientos() {
        assertThatThrownBy(() -> compraService.registrar(new CompraRequest(
                        999_999L, HOY, DOCUMENTO, null, List.of(item(primera.getId(), "40")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(comprasConEseDocumento()).isZero();
        assertThat(saldo(primera)).isEqualByComparingTo("0");
    }

    /**
     * El costo de referencia solo se actualiza si la compra se confirma. Es informativo (prellena el
     * siguiente formulario), pero si sobreviviera a una compra revertida estaria mostrando el precio
     * de una compra que nunca ocurrio.
     */
    @Test
    void elCostoDeReferenciaNoCambiaSiLaCompraSeRevierte() {
        assertThatThrownBy(() -> compraService.registrar(
                        compraCon(List.of(item(primera.getId(), "40"), item(999_999L, "15")))))
                .isInstanceOf(NotFoundException.class);

        assertThat(materiaPrimaRepository.findById(primera.getId()).orElseThrow().getCostoUnitarioReferencial())
                .isNull();
    }
}
