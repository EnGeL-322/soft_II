package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.UnidadMedida;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogo de productos (V28): lo que antes era el enum fijo de tres quesos. Lo que se fija aqui es
 * que el catalogo no admite dos filas para el mismo producto y que dar de baja uno corta lo nuevo
 * (recetas) sin perder lo que ya existe.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ProductoServiceTests {

    @Autowired
    private ProductoService service;

    @Autowired
    private RecetaService recetaService;

    @Autowired
    private MateriaPrimaRepository materiaPrimaRepository;

    private static String nombreUnico(String base) {
        return base + " " + System.nanoTime();
    }

    @Test
    void losTresQuesosDeAntesYaSonProductos() {
        assertThat(service.listar(false))
                .extracting(Producto::getNombre)
                .contains("Queso fresco", "Queso paria", "Queso andino madurado");
    }

    @Test
    void seCreaUnProductoNuevoRecortandoLosEspacios() {
        String nombre = nombreUnico("Yogur natural");

        Producto yogur = service.crear(new ProductoRequest("  " + nombre + "  ", " botella 1 L ", "  "));

        assertThat(yogur.getNombre()).isEqualTo(nombre);
        assertThat(yogur.getUnidadVenta()).isEqualTo("botella 1 L");
        assertThat(yogur.getDescripcion()).isNull();
        assertThat(service.listar(false)).extracting(Producto::getId).contains(yogur.getId());
    }

    /** Dos filas para el mismo producto partirian su stock, recetas y precios en dos. */
    @Test
    void noSeRepiteElNombreAunqueCambienLasMayusculas() {
        String nombre = nombreUnico("Manjar blanco");
        service.crear(new ProductoRequest(nombre, "unidad", null));

        assertThatThrownBy(() -> service.crear(new ProductoRequest(nombre.toUpperCase(), "unidad", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void editarSinCambiarElNombreNoChocaConsigoMismo() {
        Producto p = service.crear(new ProductoRequest(nombreUnico("Queso mantecoso"), "unidad", null));

        Producto editado = service.actualizar(p.getId(), new ProductoRequest(p.getNombre(), "kg", "Por peso"));

        assertThat(editado.getUnidadVenta()).isEqualTo("kg");
        assertThat(editado.getDescripcion()).isEqualTo("Por peso");
    }

    @Test
    void unProductoDadoDeBajaNoSeOfreceYAvisaQueHayQueReactivarlo() {
        Producto p = service.crear(new ProductoRequest(nombreUnico("Kumis"), "botella", null));

        service.cambiarEstado(p.getId(), false);

        assertThat(service.listar(false)).extracting(Producto::getId).doesNotContain(p.getId());
        assertThat(service.listar(true)).extracting(Producto::getId).contains(p.getId());
        assertThatThrownBy(() -> service.crear(new ProductoRequest(p.getNombre(), "botella", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("reactívalo");
    }

    /** Un producto dado de baja ya no se fabrica: no admite recetas nuevas. */
    @Test
    void noSeCreaRecetaParaUnProductoDadoDeBaja() {
        Producto p = service.crear(new ProductoRequest(nombreUnico("Suero"), "litro", null));
        service.cambiarEstado(p.getId(), false);
        MateriaPrima leche = new MateriaPrima();
        leche.setNombre(nombreUnico("Leche"));
        leche.setUnidadMedida(UnidadMedida.LITRO);
        leche = materiaPrimaRepository.save(leche);
        Long lecheId = leche.getId();

        assertThatThrownBy(() -> recetaService.crear(new RecetaRequest(
                        p.getId(), LocalDate.now(), null, null, null,
                        List.of(new RecetaItemRequest(lecheId, BigDecimal.ONE)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dado de baja");
    }

    @Test
    void reactivarDevuelveElProductoAlCatalogo() {
        Producto p = service.crear(new ProductoRequest(nombreUnico("Queso crema"), "unidad", null));
        service.cambiarEstado(p.getId(), false);

        service.cambiarEstado(p.getId(), true);

        assertThat(service.activo(p.getId()).getId()).isEqualTo(p.getId());
    }

    @Test
    void unProductoInexistenteEs404YNo500() {
        assertThatThrownBy(() -> service.activo(999_999L)).isInstanceOf(NotFoundException.class);
    }
}
