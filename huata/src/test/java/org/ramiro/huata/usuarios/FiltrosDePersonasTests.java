package org.ramiro.huata.usuarios;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.acopiadores.AcopiadorService;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorService;
import org.ramiro.huata.rutas.Ruta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filtros de las pestanas Acopiadores y Proveedores de Usuarios (V55). La zona es texto libre en la
 * ruta (decision C-05), asi que el filtro no distingue mayusculas ni espacios de los extremos: una
 * zona escrita "collana " en el filtro tiene que encontrar la ruta "COLLANA".
 */
@SpringBootTest
@Transactional
class FiltrosDePersonasTests {

    private static final PageRequest PAGINA = PageRequest.of(0, 500);

    @Autowired
    private AcopiadorService acopiadorService;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    @Autowired
    private ProductorService productorService;

    @Autowired
    private ProductorRepository productorRepository;

    private Acopiador acopiador;
    private Ruta ruta;

    @BeforeEach
    void escenario() {
        acopiador = acopiadorRepository.findByUsuario_UsernameIgnoreCase("acopiador.demo@huata.pe").orElseThrow();
        ruta = acopiador.getRuta();
        assertThat(ruta.getZona()).isNotBlank();
    }

    @Test
    void acopiadoresPorRutaYPorZonaSinDistinguirMayusculas() {
        String zonaEscritaDistinto = "  " + ruta.getZona().toLowerCase() + " ";

        assertThat(acopiadorService.buscar(new AcopiadorService.Filtro(null, ruta.getId(), null), PAGINA).content())
                .isNotEmpty()
                .allSatisfy(a -> assertThat(a.getRuta().getId()).isEqualTo(ruta.getId()))
                .extracting(Acopiador::getId).contains(acopiador.getId());
        assertThat(acopiadorService.buscar(new AcopiadorService.Filtro(null, null, zonaEscritaDistinto), PAGINA).content())
                .allSatisfy(a -> assertThat(a.getRuta().getZona()).isEqualToIgnoringCase(ruta.getZona()))
                .extracting(Acopiador::getId).contains(acopiador.getId());
        assertThat(acopiadorService.buscar(new AcopiadorService.Filtro(null, null, "zona que no existe"), PAGINA).content())
                .isEmpty();
    }

    @Test
    void proveedoresPorZonaRutaYAcopiadorResponsable() {
        Productor proveedor = productorRepository
                .findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(ruta.getId())
                .get(0);
        proveedor.setAcopiadorResponsable(acopiador);
        productorRepository.saveAndFlush(proveedor);

        assertThat(productorService.buscar(filtro(ruta.getId(), null, null), PAGINA).content())
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.getRuta().getId()).isEqualTo(ruta.getId()));
        assertThat(productorService.buscar(filtro(null, ruta.getZona().toLowerCase(), null), PAGINA).content())
                .allSatisfy(p -> assertThat(p.getRuta().getZona()).isEqualToIgnoringCase(ruta.getZona()))
                .extracting(Productor::getId).contains(proveedor.getId());
        // Los que recoge: su responsable directo es el, o no tienen responsable y son de su ruta.
        assertThat(productorService.buscar(filtro(null, null, acopiador.getId()), PAGINA).content())
                .allSatisfy(p -> assertThat(p.getAcopiadorResponsable() != null
                                ? p.getAcopiadorResponsable().getId().equals(acopiador.getId())
                                : p.getRuta().getId().equals(ruta.getId()))
                        .isTrue())
                .extracting(Productor::getId).contains(proveedor.getId());
    }

    @Test
    void sinFiltrosDevuelveElPadronActivoCompleto() {
        long activos = productorRepository.findByActivoTrueOrderByNombresAscApellidosAsc().size();

        assertThat(productorService.buscar(ProductorService.Filtro.texto(null), PAGINA).totalElements())
                .isEqualTo(activos);
    }

    private static ProductorService.Filtro filtro(Long rutaId, String zona, Long acopiadorId) {
        return new ProductorService.Filtro(null, null, rutaId, zona, acopiadorId);
    }

    /**
     * El buscador busca cada palabra en todos los datos (nombre, apellidos, DNI, telefono, cuenta,
     * ruta y zona), en cualquier orden y sin tildes. Antes "Proveedor Demo E" no encontraba nada.
     */
    @Test
    void elBuscadorEncuentraPorVariasPalabrasEnCualquierCampo() {
        Productor p = org.ramiro.huata.productores.ProductorRequestDePrueba.crear(productorService,
                "Teófila", "Quispe Ñahui", "951222333", ruta.getId(), "teofila.busqueda@huata.pe");

        for (String q : new String[] {"teofila nahui", "ÑAHUI teófila", "quispe 951222333", "teofila.busqueda",
                ruta.getNombre() + " teofila"}) {
            assertThat(productorService.buscar(ProductorService.Filtro.texto(q), PAGINA).content())
                    .as("buscar \"%s\"", q)
                    .extracting(Productor::getId)
                    .contains(p.getId());
        }
        assertThat(productorService.buscar(ProductorService.Filtro.texto("teofila inexistente"), PAGINA).content())
                .isEmpty();
        // Una letra suelta cuenta como inicio de palabra: "teofila n" (de Ñahui) si, "teofila h" no.
        assertThat(productorService.buscar(ProductorService.Filtro.texto("teofila n"), PAGINA).content())
                .extracting(Productor::getId).contains(p.getId());
        assertThat(productorService.buscar(ProductorService.Filtro.texto("teofila h"), PAGINA).content())
                .extracting(Productor::getId).doesNotContain(p.getId());
    }

    @Test
    void elBuscadorDeAcopiadoresUsaNombreCompletoCuentaYRuta() {
        String nombreCompleto = acopiador.getNombres() + " " + acopiador.getApellidos();
        for (String q : new String[] {nombreCompleto, "acopiador.demo", ruta.getZona()}) {
            assertThat(acopiadorService.buscar(new AcopiadorService.Filtro(q, null, null), PAGINA).content())
                    .as("buscar \"%s\"", q)
                    .extracting(Acopiador::getId)
                    .contains(acopiador.getId());
        }
    }
}
