package org.ramiro.huata.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ramiro.huata.productores.ProductorService;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * Lo que el cliente manda en busquedas y paginacion no puede alterar la consulta.
 *
 * <p>No hay inyeccion SQL clasica en el backend (sin SQL nativo ni concatenacion: todo va por
 * parametros enlazados). Lo que se fija aqui son las tres rendijas que si quedaban: comodines de
 * LIKE sin escapar, tamano de pagina sin techo y orden elegido por el cliente.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe", authorities = {Permiso.PROVEEDORES_READ, Permiso.COMPRAS_READ, Permiso.INVENTARIO_READ})
class EntradaSeguraTests {

    @Autowired ProductorService productorService;
    @Autowired WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- LIKE ----

    @Test
    void losComodinesDeLikeSeEscapan() {
        assertThat(SearchSpecifications.escaparLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
        assertThat(SearchSpecifications.escaparLike("Quispe")).isEqualTo("Quispe");
    }

    /** Antes, buscar "%" o "_" devolvia el padron entero. */
    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "%%", "__", "\\"})
    void buscarUnComodinNoDevuelveTodoElPadron(String comodin) {
        long todos = productorService.buscar(ProductorService.Filtro.texto(null), PageRequest.of(0, 500)).totalElements();
        assertThat(todos).as("el padron demo no esta vacio").isPositive();

        assertThat(productorService.buscar(ProductorService.Filtro.texto(comodin), PageRequest.of(0, 500)).totalElements()).isZero();
    }

    /** Un intento de inyeccion se trata como texto literal: no rompe la consulta ni trae filas. */
    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1", "'; DROP TABLE productores; --", "\" OR 1=1 --", "a' UNION SELECT * FROM usuarios --"})
    void unIntentoDeInyeccionEsTextoLiteral(String ataque) {
        assertThat(productorService.buscar(ProductorService.Filtro.texto(ataque), PageRequest.of(0, 500)).totalElements()).isZero();
    }

    @Test
    void unaBusquedaLarguisimaNoRompe() {
        assertThat(productorService.buscar(ProductorService.Filtro.texto("a".repeat(10_000)), PageRequest.of(0, 20)).totalElements()).isZero();
    }

    // ---- Paginacion ----

    @Test
    void elTamanoDePaginaTieneTecho() {
        assertThat(Paginas.de(0, 100_000_000).getPageSize()).isEqualTo(Paginas.TAMANO_MAXIMO);
        assertThat(Paginas.de(0, 20).getPageSize()).isEqualTo(20);
    }

    @Test
    void paginaYTamanoNegativosNoRevientan() {
        var p = Paginas.de(-3, -10);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(1);
    }

    @Test
    void seDescartaElOrdenQuePideElCliente() {
        var pedido = PageRequest.of(2, 30, Sort.by("password"));
        var saneado = Paginas.sinOrdenDelCliente(pedido);
        assertThat(saneado.getSort().isSorted()).isFalse();
        assertThat(saneado.getPageNumber()).isEqualTo(2);
        assertThat(saneado.getPageSize()).isEqualTo(30);
    }

    @Test
    void porHttpUnTamanoEnormeSeAcota() throws Exception {
        mvc().perform(get("/api/v1/productores").param("size", "100000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(Paginas.TAMANO_MAXIMO));
    }

    @Test
    void porHttpUnTamanoNegativoNoDa500() throws Exception {
        mvc().perform(get("/api/v1/productores").param("size", "-5").param("page", "-1"))
                .andExpect(status().isOk());
    }

    /** Antes una propiedad de orden inexistente respondia 500. */
    @Test
    void porHttpUnOrdenArbitrarioSeIgnora() throws Exception {
        mvc().perform(get("/api/v1/compras").param("sort", "noExiste,desc"))
                .andExpect(status().isOk());
        mvc().perform(get("/api/v1/inventario/kardex").param("sort", "materiaPrima.nombre);drop table x;--"))
                .andExpect(status().isOk());
    }
}
