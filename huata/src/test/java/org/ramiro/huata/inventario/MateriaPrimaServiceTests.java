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
                .hasMessageContaining("Ya existe un insumo");
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

    // ------------------------------------------------------- Nombres y reactivacion

    /** "Azúcar" y "azucar  " son el mismo saco: tildes, mayusculas y espacios no hacen otro insumo. */
    @Test
    void elDuplicadoSeDetectaSinTildesNiEspacios() {
        String base = "Azúcar " + System.nanoTime();
        crear(base);

        assertThatThrownBy(() -> crear("  " + base.replace("ú", "u").toUpperCase() + "  "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe un insumo");
    }

    /**
     * El caso que reporto el cliente: borrar "sal" y volver a crearla decia "Ya existe" de algo que no
     * se veia en ninguna pantalla. Ahora se reactiva la misma fila, con su historial.
     */
    @Test
    void crearConElNombreDeUnInsumoDadoDeBajaLoReactiva() {
        String nombre = nombreUnico("sal");
        MateriaPrima original = crear(nombre);
        service.inactivar(original.getId());

        MateriaPrima otraVez = service.crear(new MateriaPrimaRequest(nombre.toUpperCase(), UnidadMedida.GRAMO, null));

        assertThat(otraVez.getId()).isEqualTo(original.getId());
        assertThat(otraVez.isActivo()).isTrue();
        // Sin movimientos, toma la unidad nueva.
        assertThat(otraVez.getUnidadMedida()).isEqualTo(UnidadMedida.GRAMO);
        assertThat(service.listar()).anySatisfy(c -> assertThat(c.materiaPrima().getId()).isEqualTo(original.getId()));
    }

    /** Con kardex, la unidad no cambia al reactivar: los saldos viejos estan en esa unidad. */
    @Test
    void alReactivarUnInsumoConMovimientosConservaSuUnidad() {
        String nombre = nombreUnico("Cuajo");
        MateriaPrima original = crear(nombre);
        inventarioService.registrarEntradaCompra(original, new BigDecimal("3"), HOY, 1L);
        service.inactivar(original.getId());

        MateriaPrima otraVez = service.crear(new MateriaPrimaRequest(nombre, UnidadMedida.GRAMO, null));

        assertThat(otraVez.getUnidadMedida()).isEqualTo(UnidadMedida.KG);
        assertThat(service.saldoDe(otraVez.getId())).isEqualByComparingTo("3");
    }

    @Test
    void obtenerOCrearReusaElExistenteEnVezDeDuplicarlo() {
        String nombre = nombreUnico("Cultivo");
        MateriaPrima existente = crear(nombre);

        assertThat(service.obtenerOCrear(" " + nombre.toLowerCase() + " ", UnidadMedida.GRAMO).getId())
                .isEqualTo(existente.getId());
    }

    /** Renombrar a un nombre ocupado era un error 500 de la base de datos; ahora es un aviso claro. */
    @Test
    void noSePuedeRenombrarAUnNombreQueYaExiste() {
        String ocupado = nombreUnico("Envase");
        crear(ocupado);
        MateriaPrima otra = crear(nombreUnico("Pote"));

        assertThatThrownBy(() -> service.actualizar(otra.getId(), new MateriaPrimaRequest(ocupado, UnidadMedida.KG, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe un insumo");
    }

    @Test
    void noSePuedeCambiarLaUnidadDeUnInsumoConMovimientos() {
        MateriaPrima m = crear(nombreUnico("Cloruro"));
        inventarioService.registrarEntradaCompra(m, new BigDecimal("2"), HOY, 1L);

        assertThatThrownBy(() -> service.actualizar(m.getId(), new MateriaPrimaRequest(m.getNombre(), UnidadMedida.GRAMO, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("unidad");
    }

    // ------------------------------------------- Nombres casi iguales (cliente, 2026-10-27)

    private static String letras() {
        return String.valueOf(System.nanoTime()).chars()
                .mapToObj(c -> String.valueOf((char) ('a' + (c - '0'))))
                .reduce("", String::concat);
    }

    /** El caso del cliente: con "sal" en el inventario, "zal" no se puede crear. */
    @Test
    void noSePuedeCrearUnInsumoCasiIgualAOtro() {
        String sufijo = letras();
        crear("sal " + sufijo);

        assertThatThrownBy(() -> crear("zal " + sufijo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("casi igual");
    }

    /** Tampoco si el parecido esta dado de baja: lo correcto es reactivarlo escribiendo su nombre. */
    @Test
    void noSePuedeCrearUnInsumoCasiIgualAUnoDadoDeBaja() {
        String sufijo = letras();
        MateriaPrima sal = crear("sal " + sufijo);
        service.inactivar(sal.getId());

        assertThatThrownBy(() -> crear("zal " + sufijo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dado de baja");
    }

    /** Desde la receta pasa por la misma regla. */
    @Test
    void obtenerOCrearTambienBloqueaLosCasiIguales() {
        String sufijo = letras();
        crear("cuajo " + sufijo);

        assertThatThrownBy(() -> service.obtenerOCrear("cuajos " + sufijo, UnidadMedida.ML))
                .isInstanceOf(BusinessRuleException.class);
    }
}
