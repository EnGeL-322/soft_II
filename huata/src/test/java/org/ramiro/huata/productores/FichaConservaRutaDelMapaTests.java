package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La ficha del proveedor ya no pide orden de visita ni acopiador responsable: el orden se define en
 * "Rutas y mapa" y el acopiador sale de la ruta. Editar la ficha no puede borrarlos (antes lo hacia,
 * porque el formulario no los mandaba); cambiarlo de ruta si los descarta, porque eran de la otra.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class FichaConservaRutaDelMapaTests {

    @Autowired ProductorService service;
    @Autowired ProductorRepository productores;
    @Autowired RutaRepository rutas;
    @Autowired AcopiadorRepository acopiadores;

    private ProductorRequest ficha(Productor p, Long rutaId) {
        return new ProductorRequest(p.getDocumentoIdentidad(), p.getNombres(), p.getApellidos(), p.getTelefono(),
                rutaId, p.getNotas(), null, null, null, p.getUsuario() != null ? p.getUsuario().getUsername() : null, null);
    }

    private Productor proveedorConOrdenYResponsable(Ruta ruta) {
        Acopiador acopiador = acopiadores.findByRuta_IdAndActivoTrueOrderByNombresAsc(ruta.getId()).get(0);
        Productor p = service.crear(new ProductorRequest("71234568", "Lucia", "Ccama", null, ruta.getId(), null,
                null, null, null, "lucia.ficha@huata.pe", "Huata2026*"));
        p.setOrdenEnRuta(7);
        p.setAcopiadorResponsable(acopiador);
        return productores.saveAndFlush(p);
    }

    /** La ruta del acopiador de demo: tiene zona y al menos un acopiador. */
    private Ruta rutaConAcopiador() {
        return acopiadores.findByUsuario_UsernameIgnoreCase("acopiador.demo@huata.pe").orElseThrow().getRuta();
    }

    @Test
    void editarLaFichaConservaElOrdenDelMapaYElResponsable() {
        Ruta ruta = rutaConAcopiador();
        Productor p = proveedorConOrdenYResponsable(ruta);
        Long responsable = p.getAcopiadorResponsable().getId();

        service.actualizar(p.getId(), ficha(p, ruta.getId()));

        Productor editado = productores.findById(p.getId()).orElseThrow();
        assertThat(editado.getOrdenEnRuta()).isEqualTo(7);
        assertThat(editado.getAcopiadorResponsable().getId()).isEqualTo(responsable);
    }

    @Test
    void cambiarDeRutaDescartaElOrdenYElResponsableDeLaRutaAnterior() {
        Ruta ruta = rutaConAcopiador();
        Productor p = proveedorConOrdenYResponsable(ruta);
        Ruta otra = rutas.findAll().stream()
                .filter(r -> r.getZona() != null && !r.getZona().isBlank() && !r.getId().equals(ruta.getId()))
                .findFirst()
                .orElseThrow();

        service.actualizar(p.getId(), ficha(p, otra.getId()));

        Productor movido = productores.findById(p.getId()).orElseThrow();
        assertThat(movido.getRuta().getId()).isEqualTo(otra.getId());
        assertThat(movido.getOrdenEnRuta()).isNull();
        assertThat(movido.getAcopiadorResponsable()).isNull();
    }
}
