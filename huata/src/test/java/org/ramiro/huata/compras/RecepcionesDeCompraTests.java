package org.ramiro.huata.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableResponse;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compra y recepcion separadas (V61, prototipo aprobado por el cliente): la compra no es stock; lo
 * es la recepcion, total o parcial, con lote y vencimiento. El asiento va por lo recibido.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria@huata.pe")
class RecepcionesDeCompraTests {

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
    private MateriaPrima cuajo;

    @BeforeEach
    void escenario() {
        proveedor = new ProveedorCompra();
        proveedor.setCodigo("PC-REC");
        proveedor.setNombre("Insumos Recepcion SAC");
        proveedor = proveedorRepository.save(proveedor);
        sal = materia("Sal recepcion", UnidadMedida.KG);
        cuajo = materia("Cuajo recepcion", UnidadMedida.ML);
    }

    private MateriaPrima materia(String nombre, UnidadMedida unidad) {
        MateriaPrima m = new MateriaPrima();
        m.setNombre(nombre + " " + System.nanoTime());
        m.setUnidadMedida(unidad);
        return materiaPrimaRepository.save(m);
    }

    /** Sal 10 kg a S/ 2 y cuajo 500 ml a S/ 0.10, sin recibir. */
    private Compra compraPendiente() {
        return compraService.registrar(new CompraRequest(
                proveedor.getId(), HOY, "F001-1", null,
                List.of(
                        new CompraItemRequest(sal.getId(), new BigDecimal("10"), new BigDecimal("2")),
                        new CompraItemRequest(cuajo.getId(), new BigDecimal("500"), new BigDecimal("0.10"))),
                false));
    }

    private CompraItem linea(Compra c, MateriaPrima m) {
        return c.getItems().stream().filter(i -> i.getMateriaPrima().getId().equals(m.getId())).findFirst().orElseThrow();
    }

    private BigDecimal saldo(MateriaPrima m) {
        return inventarioService.saldoMateriaPrima(m.getId());
    }

    private List<AsientoContableResponse> asientosDe(Compra c, OrigenAsiento origen) {
        return asientoContableService.libroDiario(HOY, HOY).stream()
                .filter(a -> a.origenTipo() == origen && c.getId().equals(a.origenId()))
                .toList();
    }

    @Test
    void comprarNoMueveElAlmacenNiLaContabilidad() {
        Compra c = compraPendiente();

        assertThat(c.getEstado()).isEqualTo(EstadoCompra.PENDIENTE_RECEPCION);
        assertThat(saldo(sal)).isEqualByComparingTo("0");
        assertThat(asientosDe(c, OrigenAsiento.COMPRA)).isEmpty();
        assertThat(compraService.pendientesDeRecepcion()).extracting(CompraResponse::id).contains(c.getId());
        assertThat(compraService.enCaminoPorMateriaPrima().get(sal.getId())).isEqualByComparingTo("10");
    }

    @Test
    void unaRecepcionParcialSumaLoQueLlegoYSuAsientoEsPorLoRecibido() {
        Compra c = compraPendiente();

        compraService.recibir(c.getId(), new RecepcionRequest(HOY, "GR-01",
                List.of(new RecepcionItemRequest(linea(c, sal).getId(), new BigDecimal("4"), "L-SAL-1", HOY.plusMonths(6)))));

        assertThat(c.getEstado()).isEqualTo(EstadoCompra.RECIBIDA_PARCIAL);
        assertThat(saldo(sal)).isEqualByComparingTo("4");
        assertThat(saldo(cuajo)).isEqualByComparingTo("0");
        assertThat(asientosDe(c, OrigenAsiento.COMPRA)).singleElement()
                .satisfies(a -> assertThat(a.totalDebe()).isEqualByComparingTo("8.00"));
        assertThat(compraService.enCaminoPorMateriaPrima().get(sal.getId())).isEqualByComparingTo("6");
        assertThat(compraService.recepciones()).first().satisfies(r -> {
            assertThat(r.compraId()).isEqualTo(c.getId());
            assertThat(r.recibidoPor()).isEqualTo("secretaria@huata.pe");
            assertThat(r.items()).singleElement().satisfies(i -> {
                assertThat(i.lote()).isEqualTo("L-SAL-1");
                assertThat(i.fechaVencimiento()).isEqualTo(HOY.plusMonths(6));
            });
        });

        // El resto llega despues: la compra queda completa.
        compraService.recibir(c.getId(), new RecepcionRequest(HOY, null, List.of(
                new RecepcionItemRequest(linea(c, sal).getId(), new BigDecimal("6"), null, null),
                new RecepcionItemRequest(linea(c, cuajo).getId(), new BigDecimal("500"), null, null))));

        assertThat(c.getEstado()).isEqualTo(EstadoCompra.RECIBIDA);
        assertThat(saldo(sal)).isEqualByComparingTo("10");
        assertThat(saldo(cuajo)).isEqualByComparingTo("500");
        assertThat(compraService.pendientesDeRecepcion()).extracting(CompraResponse::id).doesNotContain(c.getId());
    }

    @Test
    void noSeRecibeMasDeLoPendienteNiAlgoYaVencidoNiUnaCompraCompleta() {
        Compra c = compraPendiente();
        Long lineaSal = linea(c, sal).getId();

        assertThatThrownBy(() -> compraService.recibir(c.getId(), new RecepcionRequest(HOY, null,
                        List.of(new RecepcionItemRequest(lineaSal, new BigDecimal("11"), null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("quedan por recibir 10");
        assertThatThrownBy(() -> compraService.recibir(c.getId(), new RecepcionRequest(HOY, null,
                        List.of(new RecepcionItemRequest(lineaSal, BigDecimal.ONE, null, HOY.minusDays(1))))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("llega ya vencido");
        assertThatThrownBy(() -> compraService.recibir(c.getId(), new RecepcionRequest(HOY.minusDays(1), null,
                        List.of(new RecepcionItemRequest(lineaSal, BigDecimal.ONE, null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior a la compra");
    }

    @Test
    void sinDecirNadaLaCompraSeRecibeEnElActoComoAntes() {
        // Asi registra la app movil (no manda recibirAhora): no cambia su comportamiento.
        Compra c = compraService.registrar(new CompraRequest(proveedor.getId(), HOY, null, null,
                List.of(new CompraItemRequest(sal.getId(), new BigDecimal("3"), new BigDecimal("2")))));

        assertThat(c.getEstado()).isEqualTo(EstadoCompra.RECIBIDA);
        assertThat(saldo(sal)).isEqualByComparingTo("3");
        assertThat(asientosDe(c, OrigenAsiento.COMPRA)).hasSize(1);
    }

    @Test
    void anularUnaCompraSinRecibirNoMueveNadaYConParteRecibidaSoloRevierteEsaParte() {
        Compra pendiente = compraPendiente();
        compraService.anular(pendiente.getId(), "Se pidió por error");
        assertThat(pendiente.isActivo()).isFalse();
        assertThat(asientosDe(pendiente, OrigenAsiento.ANULACION_COMPRA)).isEmpty();
        assertThatThrownBy(() -> compraService.recibir(pendiente.getId(), new RecepcionRequest(HOY, null,
                        List.of(new RecepcionItemRequest(linea(pendiente, sal).getId(), BigDecimal.ONE, null, null)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anulada");

        Compra parcial = compraPendiente();
        compraService.recibir(parcial.getId(), new RecepcionRequest(HOY, null,
                List.of(new RecepcionItemRequest(linea(parcial, sal).getId(), new BigDecimal("4"), null, null))));
        compraService.anular(parcial.getId(), "El proveedor no entregará el resto");

        assertThat(saldo(sal)).isEqualByComparingTo("0");
        assertThat(asientosDe(parcial, OrigenAsiento.ANULACION_COMPRA)).singleElement()
                .satisfies(a -> assertThat(a.totalDebe()).isEqualByComparingTo("8.00"));
    }
}
