package org.ramiro.huata.acopiadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Eliminar un acopiador desde Usuarios es una baja logica: su historial (recojos, liquidaciones) se
 * conserva, pero deja de listarse, su cuenta deja de entrar y ningun proveedor queda apuntando a el.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class BajaDeAcopiadorTests {

    @Autowired AcopiadorService acopiadorService;
    @Autowired RutaRepository rutaRepository;
    @Autowired ProductorRepository productorRepository;
    @Autowired UsuarioRepository usuarioRepository;

    @Test
    void laBajaLoSacaDeLaListaBloqueaSuCuentaYLiberaASusProveedores() {
        Ruta ruta = rutaRepository.findAll().stream()
                .filter(r -> r.getZona() != null && !r.getZona().isBlank())
                .findFirst()
                .orElseThrow();
        Acopiador acopiador = acopiadorService.crear(
                new AcopiadorRequest("Baja", "Prueba", null, null, ruta.getId(), "baja.prueba@huata.pe", "Huata2026*"));
        Productor proveedor = productorRepository
                .findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(ruta.getId())
                .get(0);
        proveedor.setAcopiadorResponsable(acopiador);
        productorRepository.saveAndFlush(proveedor);

        acopiadorService.inactivar(acopiador.getId());

        assertThat(acopiadorService.buscar(new AcopiadorService.Filtro(null, null, null), PageRequest.of(0, 500)).content())
                .extracting(Acopiador::getId)
                .doesNotContain(acopiador.getId());
        assertThatThrownBy(() -> acopiadorService.obtener(acopiador.getId())).isInstanceOf(NotFoundException.class);
        assertThat(usuarioRepository.findById(acopiador.getUsuario().getId()).orElseThrow().isActivo()).isFalse();
        assertThat(productorRepository.findById(proveedor.getId()).orElseThrow().getAcopiadorResponsable()).isNull();
    }
}
