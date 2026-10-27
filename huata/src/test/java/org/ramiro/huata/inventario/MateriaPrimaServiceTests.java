package org.ramiro.huata.inventario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maestro de materias primas y lectura del kardex.
 *
 * <p><b>Por que existe.</b> {@code MateriaPrimaService} estaba al 0 % y es dato maestro del que cuelga
 * todo lo demas: una receta apunta a una materia prima, una compra la ingresa y un lote la consume. Dos
 * filas para el mismo insumo fisico parten su kardex en dos y el stock deja de significar nada.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class MateriaPrimaServiceTests {

    private static final LocalDate HOY = LocalDate.now();

    @Autowired
    private MateriaPrimaService service;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private MateriaPrimaRepository repository;

    private String nombreUnico(String base) {
        return base + " " + System.nanoTime();
    }

    private MateriaPrima crear(String nombre) {
        return service.crear(new MateriaPrimaRequest(nombre, UnidadMedida.KG, new BigDecimal("8.00")));
    }

    // ------------------------------------------------------------------------------ Alta

    @Test
    void crearGuardaLaMateriaPrimaActivaYConSuUnidad() {
        MateriaPrima creada = crear(nombreUnico("Cuajo"));

        assertThat(creada.getId()).isNotNull();
        assertThat(creada.isActivo()).isTrue();
        assertThat(creada.getUnidadMedida()).isEqualTo(UnidadMedida.KG);
        assertThat(creada.getCostoUnitarioReferencial()).isEqualByComparingTo("8.00");
    }

    /**
     * El nombre se recorta. Sin esto, "Sal " y "Sal" serian dos insumos distintos para el sistema y el
     * mismo saco en la bodega.
     */
    @Test
    void elNombreSeGuardaSinEspaciosSobrantes() {
        String nombre = nombreUnico("Sal");

        assertThat(crear("  " + nombre + "  ").getNombre()).isEqualTo(nombre);
    }

    /** No se admite un duplicado, y da igual como se escriban las mayusculas. */
    @Test
    void noSePuedeCrearDosVecesLaMismaMateriaPrima() {
        String nombre = nombreUnico("Cloruro");
        crear(nombre);

        assertThatThrownBy(() -> crear(nombre.toUpperCase()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe una materia prima");
    }

    /** Ni con espacios de más alrededor: se compara el nombre ya recortado. */
    @Test
    void elDuplicadoSeDetectaAunqueLleguePadeadoDeEspacios() {
        String nombre = nombreUnico("Fermento");
        crear(nombre);

        assertThatThrownBy(() -> crear("   " + nombre + "   ")).isInstanceOf(BusinessRuleException.class);
    }

    // -------------------------------------------------------------------------- Edicion y baja

    @Test
    void actualizarCambiaNombreUnidadYCostoDeReferencia() {
        MateriaPrima creada = crear(nombreUnico("Cuajo"));
        String nuevoNombre = nombreUnico("Cuajo liquido");

        MateriaPrima actualizada = service.actualizar(
                creada.getId(), new MateriaPrimaRequest(nuevoNombre, UnidadMedida.LITRO, new BigDecimal("11.50")));

        assertThat(actualizada.getNombre()).isEqualTo(nuevoNombre);
        assertThat(actualizada.getUnidadMedida()).isEqualTo(UnidadMedida.LITRO);
        assertThat(actualizada.getCostoUnitarioReferencial()).isEqualByComparingTo("11.50");
    }

    @Test
    void actualizarUnaMateriaPrimaInexistenteEs404() {
        assertThatThrownBy(() -> service.actualizar(
                        999_999L, new MateriaPrimaRequest("Lo que sea", UnidadMedida.KG, null)))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * Dar de baja es logico, no fisico: la fila se conserva porque el kardex historico la referencia. Si
     * se borrara, los movimientos antiguos quedarian apuntando a la nada.
     */
    @Test
    void inactivarNoBorraLaFilaYLaSacaDelListado() {
        MateriaPrima creada = crear(nombreUnico("Colorante"));

        service.inactivar(creada.getId());

        assertThat(repository.findById(creada.getId())).isPresent();
        assertThat(repository.findById(creada.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(service.listar()).noneSatisfy(c -> assertThat(c.materiaPrima().getId()).isEqualTo(creada.getId()));
    }

    @Test
    void inactivarUnaMateriaPrimaInexistenteEs404() {
        assertThatThrownBy(() -> service.inactivar(999_999L)).isInstanceOf(NotFoundException.class);
    }

    /**
     * Comportamiento vigente, documentado a proposito: <b>se puede dar de baja una materia prima que
     * todavia tiene stock</b>, y ese stock queda fuera del listado aunque siga existiendo en el kardex.
     * Nadie definio que hacer en ese caso (¿bloquear la baja? ¿exigir un ajuste a cero?), asi que la
     * prueba fija lo que hoy ocurre en vez de suponer una regla que el negocio no confirmo.
     */
    @Test
    void hoySePuedeDarDeBajaUnaMateriaPrimaConStockYEseStockDejaDeVerse() {
        MateriaPrima creada = crear(nombreUnico("Insumo con saldo"));
        inventarioService.registrarEntradaCompra(creada, new BigDecimal("25"), HOY, 1L);

        service.inactivar(creada.getId());

        assertThat(service.saldoDe(creada.getId())).isEqualByComparingTo("25");
        assertThat(service.listar()).noneSatisfy(c -> assertThat(c.materiaPrima().getId()).isEqualTo(creada.getId()));
    }

    // ------------------------------------------------------------------------------ Listado

    /** El stock del listado sale del kardex, no de una columna: es lo que evita que se desincronice. */
    @Test
    void elListadoTraeElStockCalculadoDesdeElKardex() {
        MateriaPrima creada = crear(nombreUnico("Insumo listado"));
        inventarioService.registrarEntradaCompra(creada, new BigDecimal("30"), HOY, 1L);
        inventarioService.registrarSalidaProduccionConsumo(creada, new BigDecimal("12"), HOY, 5L);

        assertThat(service.listar())
                .filteredOn(c -> c.materiaPrima().getId().equals(creada.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.stock()).isEqualByComparingTo("18"));
    }

    /** Una materia prima sin movimientos aparece con stock cero, no ausente ni nula. */
    @Test
    void unaMateriaPrimaSinMovimientosApareceConStockCero() {
        MateriaPrima creada = crear(nombreUnico("Insumo sin movimientos"));

        assertThat(service.listar())
                .filteredOn(c -> c.materiaPrima().getId().equals(creada.getId()))
                .singleElement()
                .satisfies(c -> assertThat(c.stock()).isEqualByComparingTo("0"));
    }

    // ------------------------------------------------------------------------------ Kardex

    /**
     * El kardex se lee paginado y el DTO se arma <b>dentro</b> de la transaccion, porque
     * {@code MovimientoInventario.materiaPrima} es LAZY y la respuesta necesita su nombre, no solo el
     * id. Esta prueba toca esos campos a proposito: es el mismo fallo (§36.1 y §36.6) que en este
     * proyecto ya aparecio dos veces por mapear fuera de la sesion, y con {@code open-in-view=false} no
     * hay red que lo tape.
     */
    @Test
    void elKardexDeUnaMateriaPrimaTraeElNombreYNoSoloElId() {
        MateriaPrima creada = crear(nombreUnico("Insumo kardex paginado"));
        inventarioService.registrarEntradaCompra(creada, new BigDecimal("30"), HOY, 77L);

        var pagina = service.kardexDeMateriaPrima(creada.getId(), PageRequest.of(0, 10));

        assertThat(pagina.getContent())
                .singleElement()
                .satisfies(m -> {
                    assertThat(m.materiaPrimaNombre()).isEqualTo(creada.getNombre());
                    assertThat(m.cantidad()).isEqualByComparingTo("30");
                    assertThat(m.tipo()).isEqualTo(TipoMovimientoInventario.ENTRADA_COMPRA);
                    assertThat(m.documentoOrigenId()).isEqualTo(77L);
                });
    }

    /** El kardex general incluye el movimiento recien registrado y lo trae con el mas nuevo primero. */
    @Test
    void elKardexGeneralIncluyeLosMovimientosDeCualquierMateriaPrima() {
        MateriaPrima creada = crear(nombreUnico("Insumo kardex general"));
        inventarioService.registrarEntradaCompra(creada, new BigDecimal("9"), HOY, 1L);

        assertThat(service.kardex(PageRequest.of(0, 50)).getContent())
                .anySatisfy(m -> assertThat(m.materiaPrimaNombre()).isEqualTo(creada.getNombre()));
    }

    @Test
    void elKardexDeUnaMateriaPrimaSinMovimientosVieneVacio() {
        MateriaPrima creada = crear(nombreUnico("Insumo kardex vacio"));

        assertThat(service.kardexDeMateriaPrima(creada.getId(), PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }
}
