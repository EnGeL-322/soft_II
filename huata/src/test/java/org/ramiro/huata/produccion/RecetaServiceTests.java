package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receta/BOM de queso: versionado y simulacion de requerimientos (RF-65, RF-66).
 *
 * <p><b>Por que existe.</b> El paquete {@code produccion} estaba al 1,2 % de cobertura y aqui vive
 * logica de negocio que la seccion 35 de CLAUDE.md exige probar: un calculo (cuanto insumo hace falta
 * para N quesos) y una regla de vigencia por version. Si la vigencia elige la receta equivocada, un
 * lote se planifica y se costea con cantidades que no son las que aprobo administracion.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class RecetaServiceTests {

    /** Fechas fijas y lejanas: lo que se mide es lo que crea la prueba, no la receta demo si la hubiera. */
    private static final LocalDate ENERO = LocalDate.of(2031, 1, 1);
    private static final LocalDate JUNIO = LocalDate.of(2031, 6, 1);

    /** Documento de origen de referencia: el esquema exige que todo movimiento declare de donde viene. */
    private static final Long COMPRA_DE_REFERENCIA = 1L;

    @Autowired
    private RecetaService service;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private ProductoRepository productoRepository;

    private MateriaPrima cuajo;
    private MateriaPrima sal;

    /** Producto propio de cada prueba: la receta demo del queso fresco no debe contar aqui. */
    private Producto producto;

    @BeforeEach
    void escenario() {
        cuajo = materiaPrima("Cuajo de prueba", UnidadMedida.LITRO);
        sal = materiaPrima("Sal de prueba", UnidadMedida.KG);
        producto = new Producto();
        producto.setNombre("Producto de prueba " + System.nanoTime());
        producto.setUnidadVenta("unidad");
        producto = productoRepository.saveAndFlush(producto);
    }

    private MateriaPrima materiaPrima(String nombre, UnidadMedida unidad) {
        MateriaPrima mp = new MateriaPrima();
        mp.setNombre(nombre + " " + System.nanoTime());
        mp.setUnidadMedida(unidad);
        mp.setCostoUnitarioReferencial(new BigDecimal("12.5000"));
        return materiaPrimaRepository.saveAndFlush(mp);
    }

    private RecetaQueso crearReceta(LocalDate vigenteDesde, LocalDate vigenteHasta, String porUnidadCuajo) {
        return service.crear(new RecetaRequest(
                producto.getId(),
                vigenteDesde,
                vigenteHasta,
                new BigDecimal("6.5"),
                null,
                List.of(
                        new RecetaItemRequest(cuajo.getId(), new BigDecimal(porUnidadCuajo)),
                        new RecetaItemRequest(sal.getId(), new BigDecimal("0.2")))));
    }

    // ------------------------------------------------------------------------------- Crear

    @Test
    void crearGuardaLaRecetaConSusInsumos() {
        RecetaQueso receta = crearReceta(ENERO, null, "0.5");

        assertThat(receta.getId()).isNotNull();
        assertThat(receta.getProducto().getId()).isEqualTo(producto.getId());
        assertThat(receta.getItems()).hasSize(2);
        assertThat(service.vigente(producto.getId(), ENERO).items())
                .extracting(RecetaItemResponse::cantidadPorUnidad)
                .containsExactlyInAnyOrder(new BigDecimal("0.5"), new BigDecimal("0.2"));
    }

    /** Una receta no puede apuntar a un insumo que no existe: seria una lista de materiales fantasma. */
    @Test
    void noSePuedeCrearUnaRecetaConUnInsumoInexistente() {
        assertThatThrownBy(() -> service.crear(new RecetaRequest(
                        producto.getId(),
                        ENERO,
                        null,
                        null,
                        null,
                        List.of(new RecetaItemRequest(999_999L, BigDecimal.ONE)))))
                .isInstanceOf(NotFoundException.class);
    }

    /** Ni a uno dado de baja: dejo de comprarse, asi que planificar con el es planificar en falso. */
    @Test
    void noSePuedeCrearUnaRecetaConUnInsumoDadoDeBaja() {
        cuajo.setActivo(false);
        materiaPrimaRepository.saveAndFlush(cuajo);

        assertThatThrownBy(() -> crearReceta(ENERO, null, "0.5")).isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------- Versionado

    /**
     * Crear una receta nueva <b>no edita</b> la anterior: las dos quedan, y cada fecha resuelve a la
     * suya. Es lo que permite recostear un lote antiguo con la receta que estaba vigente entonces.
     */
    @Test
    void crearUnaVersionNuevaNoPisaLaAnterior() {
        crearReceta(ENERO, null, "0.5");
        crearReceta(JUNIO, null, "0.8");

        assertThat(cantidadDeCuajoVigenteEl(ENERO)).isEqualByComparingTo("0.5");
        assertThat(cantidadDeCuajoVigenteEl(JUNIO)).isEqualByComparingTo("0.8");
    }

    /** Entre dos versiones manda la mas reciente que ya haya empezado, no la ultima creada. */
    @Test
    void laVigenteEsLaMasRecienteQueYaEmpezo() {
        crearReceta(JUNIO, null, "0.8");
        crearReceta(ENERO, null, "0.5");

        assertThat(cantidadDeCuajoVigenteEl(ENERO.plusMonths(1))).isEqualByComparingTo("0.5");
        assertThat(cantidadDeCuajoVigenteEl(JUNIO.plusMonths(1))).isEqualByComparingTo("0.8");
    }

    /**
     * {@code vigenteHasta} es informativo y NO decide la vigencia (lo dice el javadoc del servicio).
     * Se fija en una prueba porque es justo el detalle que alguien "arreglaria" de buena fe, dejando de
     * golpe sin receta a todos los tipos de queso cuya ultima version tenga fecha de fin pasada.
     */
    @Test
    void unaRecetaConFechaDeFinPasadaSigueSiendoLaVigenteSiEsLaMasReciente() {
        crearReceta(ENERO, ENERO.plusDays(10), "0.5");

        assertThat(cantidadDeCuajoVigenteEl(JUNIO)).isEqualByComparingTo("0.5");
    }

    /** Antes de que exista cualquier version no hay receta, y el mensaje dice que hay que configurarla. */
    @Test
    void sinRecetaVigenteAvisaEnVezDeFallarDeFormaOscura() {
        crearReceta(JUNIO, null, "0.8");

        assertThatThrownBy(() -> service.vigente(producto.getId(), ENERO))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay una receta configurada")
                .hasMessageContaining("Producción");
    }

    // -------------------------------------------------------------------------- Simulacion

    /**
     * El calculo de RF-66: cantidad por unidad x objetivo. Con 100 quesos y 0,5 L por queso son 50 L, y
     * el stock de 60 L alcanza; de sal hacen falta 20 kg y con 5 kg no alcanza.
     */
    @Test
    void simularMultiplicaPorElObjetivoYComparaContraElStock() {
        RecetaQueso receta = crearReceta(ENERO, null, "0.5");
        darStock(cuajo, "60");
        darStock(sal, "5");

        SimulacionResponse simulacion = service.simular(receta.getId(), 100);

        assertThat(simulacion.cantidadObjetivo()).isEqualTo(100);
        assertThat(filaDe(simulacion, cuajo))
                .satisfies(f -> {
                    assertThat(f.cantidadRequerida()).isEqualByComparingTo("50.0");
                    assertThat(f.stockActual()).isEqualByComparingTo("60");
                    assertThat(f.alcanza()).isTrue();
                });
        assertThat(filaDe(simulacion, sal))
                .satisfies(f -> {
                    assertThat(f.cantidadRequerida()).isEqualByComparingTo("20.0");
                    assertThat(f.alcanza()).isFalse();
                });
        // Un solo insumo corto basta para que el lote no se pueda producir completo.
        assertThat(simulacion.alcanzaTodo()).isFalse();
    }

    @Test
    void siTodosLosInsumosAlcanzanLaSimulacionLoDiceAsi() {
        RecetaQueso receta = crearReceta(ENERO, null, "0.5");
        darStock(cuajo, "60");
        darStock(sal, "30");

        assertThat(service.simular(receta.getId(), 100).alcanzaTodo()).isTrue();
    }

    /** Sin stock el saldo es cero, no nulo: la pantalla tiene que poder mostrar "faltan 50". */
    @Test
    void unInsumoSinNingunMovimientoCuentaComoStockCero() {
        RecetaQueso receta = crearReceta(ENERO, null, "0.5");

        SimulacionResponse simulacion = service.simular(receta.getId(), 10);

        assertThat(filaDe(simulacion, cuajo).stockActual()).isEqualByComparingTo("0");
        assertThat(filaDe(simulacion, cuajo).alcanza()).isFalse();
    }

    /** La simulacion es planificacion: no debe mover inventario (RF-66, "no persiste nada"). */
    @Test
    void simularNoTocaElInventario() {
        RecetaQueso receta = crearReceta(ENERO, null, "0.5");
        darStock(cuajo, "60");
        BigDecimal antes = saldoDe(cuajo);

        service.simular(receta.getId(), 100);

        assertThat(saldoDe(cuajo)).isEqualByComparingTo(antes);
    }

    @Test
    void simularSobreUnaRecetaInexistenteEs404YNoUnCalculoVacio() {
        assertThatThrownBy(() -> service.simular(999_999L, 10)).isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------------------ Apoyo

    private BigDecimal cantidadDeCuajoVigenteEl(LocalDate fecha) {
        return service.vigente(producto.getId(), fecha).items().stream()
                .filter(i -> i.materiaPrimaId().equals(cuajo.getId()))
                .findFirst()
                .orElseThrow()
                .cantidadPorUnidad();
    }

    /**
     * Da stock por la unica via que existe para una materia prima: una entrada de compra. El id de
     * documento es obligatorio en el esquema (todo movimiento declara su origen), asi que se pasa uno
     * de referencia; lo que se esta midiendo es el saldo, no la compra.
     */
    private void darStock(MateriaPrima materiaPrima, String cantidad) {
        inventarioService.registrarEntradaCompra(materiaPrima, new BigDecimal(cantidad), ENERO, COMPRA_DE_REFERENCIA);
    }

    private BigDecimal saldoDe(MateriaPrima materiaPrima) {
        return inventarioService
                .saldosMateriasPrimas(List.of(materiaPrima.getId()))
                .getOrDefault(materiaPrima.getId(), BigDecimal.ZERO);
    }

    private static SimulacionItemResponse filaDe(SimulacionResponse simulacion, MateriaPrima materiaPrima) {
        return simulacion.items().stream()
                .filter(i -> i.materiaPrimaId().equals(materiaPrima.getId()))
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------- Ingredientes escritos en el recetario

    private RecetaQueso recetaCon(RecetaItemRequest... items) {
        return service.crear(new RecetaRequest(producto.getId(), ENERO, null, null, null, List.of(items)));
    }

    /** Un ingrediente nuevo se crea junto con la receta, en la misma transaccion. */
    @Test
    void unIngredienteNuevoSeCreaConLaReceta() {
        String nombre = "Estabilizante " + System.nanoTime();

        RecetaQueso receta = recetaCon(new RecetaItemRequest(null, nombre, UnidadMedida.GRAMO, new BigDecimal("3")));

        MateriaPrima creado = receta.getItems().get(0).getMateriaPrima();
        assertThat(creado.getNombre()).isEqualTo(nombre);
        assertThat(creado.getUnidadMedida()).isEqualTo(UnidadMedida.GRAMO);
        assertThat(creado.isActivo()).isTrue();
    }

    /** Escribir el nombre de uno que ya existe usa ese, no crea otro con el mismo nombre. */
    @Test
    void escribirElNombreDeUnInsumoExistenteNoLoDuplica() {
        RecetaQueso receta = recetaCon(
                new RecetaItemRequest(null, sal.getNombre().toUpperCase(), UnidadMedida.GRAMO, new BigDecimal("1")));

        assertThat(receta.getItems().get(0).getMateriaPrima().getId()).isEqualTo(sal.getId());
        // La unidad del insumo manda: no se cambia por la que se eligio en la fila.
        assertThat(receta.getItems().get(0).getMateriaPrima().getUnidadMedida()).isEqualTo(UnidadMedida.KG);
    }

    @Test
    void escribirElNombreDeUnInsumoDadoDeBajaLoReactiva() {
        sal.setActivo(false);
        materiaPrimaRepository.saveAndFlush(sal);

        RecetaQueso receta = recetaCon(new RecetaItemRequest(null, sal.getNombre(), UnidadMedida.KG, new BigDecimal("1")));

        assertThat(receta.getItems().get(0).getMateriaPrima().getId()).isEqualTo(sal.getId());
        assertThat(materiaPrimaRepository.findById(sal.getId()).orElseThrow().isActivo()).isTrue();
    }

    /** El mismo insumo dos veces (por id y por nombre) se rechaza: se suma en una sola fila. */
    @Test
    void elMismoInsumoDosVecesSeRechaza() {
        assertThatThrownBy(() -> recetaCon(
                        new RecetaItemRequest(sal.getId(), new BigDecimal("1")),
                        new RecetaItemRequest(null, sal.getNombre(), UnidadMedida.KG, new BigDecimal("2"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dos veces");
    }

    @Test
    void unIngredienteNuevoSinUnidadSeRechaza() {
        assertThatThrownBy(() -> recetaCon(new RecetaItemRequest(null, "Algo nuevo", null, BigDecimal.ONE)))
                .isInstanceOf(BusinessRuleException.class);
    }
}
