package org.ramiro.huata.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una compra genera entrada de inventario para cada item y actualiza el costo de referencia de la
 * materia prima, todo en una sola transaccion (RF-70, RNF-18).
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class CompraServiceTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired
    private CompraService compraService;

    @Autowired
    private ProveedorCompraRepository proveedorRepository;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private AsientoContableService asientoContableService;

    private ProveedorCompra proveedor;
    private MateriaPrima sal;

    @BeforeEach
    void escenario() {
        proveedor = new ProveedorCompra();
        proveedor.setCodigo("PC-TEST");
        proveedor.setNombre("Insumos Andinos SAC");
        proveedor = proveedorRepository.save(proveedor);

        sal = new MateriaPrima();
        sal.setNombre("Sal");
        sal.setUnidadMedida(UnidadMedida.KG);
        sal = materiaPrimaRepository.save(sal);
    }

    @Test
    void unaCompraAumentaElStockDeCadaMateriaPrimaCompradaYActualizaSuCostoDeReferencia() {
        BigDecimal antes = inventarioService.saldoMateriaPrima(sal.getId());

        compraService.registrar(new CompraRequest(
                proveedor.getId(),
                HOY,
                "F001-000123",
                null,
                List.of(new CompraItemRequest(sal.getId(), BigDecimal.valueOf(50), BigDecimal.valueOf(3.20)))));

        assertThat(inventarioService.saldoMateriaPrima(sal.getId())).isEqualByComparingTo(antes.add(BigDecimal.valueOf(50)));
        assertThat(materiaPrimaRepository.findById(sal.getId()).orElseThrow().getCostoUnitarioReferencial())
                .isEqualByComparingTo("3.20");
    }

    @Test
    void elTotalDeLaCompraSaleDeSusItemsYNoSeGuardaAparte() {
        Compra compra = compraService.registrar(new CompraRequest(
                proveedor.getId(),
                HOY,
                null,
                null,
                List.of(
                        new CompraItemRequest(sal.getId(), BigDecimal.valueOf(10), BigDecimal.valueOf(3.00)),
                        new CompraItemRequest(sal.getId(), BigDecimal.valueOf(5), BigDecimal.valueOf(4.00)))));

        assertThat(compra.total()).isEqualByComparingTo("50.00");
    }

    @Test
    void unaCompraGeneraUnAsientoContableDeMateriaPrimaContraCuentasPorPagar() {
        compraService.registrar(new CompraRequest(
                proveedor.getId(),
                HOY,
                "F001-000999",
                null,
                List.of(new CompraItemRequest(sal.getId(), BigDecimal.valueOf(10), BigDecimal.valueOf(5.00)))));

        var asientos = asientoContableService.libroDiario(HOY, HOY);
        var asiento = asientos.stream()
                .filter(a -> a.origenTipo() == org.ramiro.huata.contabilidad.OrigenAsiento.COMPRA)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se genero el asiento de la compra"));

        assertThat(asiento.totalDebe()).isEqualByComparingTo("50.00");
        assertThat(asiento.totalHaber()).isEqualByComparingTo("50.00");
        assertThat(asiento.lineas())
                .anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("2411"))
                .anySatisfy(l -> assertThat(l.codigoCuenta()).isEqualTo("4212"));
    }

    @Test
    void noSeRegistraUnaCompraConFechaFutura() {
        assertThatThrownBy(() -> compraService.registrar(new CompraRequest(
                        proveedor.getId(),
                        HOY.plusDays(1),
                        null,
                        null,
                        List.of(new CompraItemRequest(sal.getId(), BigDecimal.ONE, BigDecimal.ONE)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("fecha futura");
    }
}
