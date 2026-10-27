package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Ubicacion del proveedor (RF-59), que ahora tambien se marca desde el mapa de la web.
 *
 * <p><b>Esta clase NO lleva {@code @Transactional} a proposito.</b> Con una transaccion de test
 * abierta la sesion de Hibernate sigue viva y el proxy LAZY del productor se inicializa sin
 * problema, que es justo lo que ocultaba el fallo: en produccion
 * ({@code spring.jpa.open-in-view=false}) el controlador lee el nombre del productor cuando la
 * sesion ya se cerro y el endpoint respondia 500 con {@code LazyInitializationException}.
 */
@SpringBootTest
@WithMockUser(username = "admin@huata.pe")
class ProductorUbicacionServiceTests {

    @Autowired
    private ProductorUbicacionService service;

    @Autowired
    private ProductorRepository productorRepository;

    private Productor algunProductor() {
        return productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void elHistorialSePuedeLeerFueraDeLaSesionDeHibernate() {
        Long productorId = algunProductor().getId();
        service.registrar(
                productorId, new ProductorUbicacionRequest(-15.61, -69.97, 12.0, TipoUbicacion.CASA));

        List<ProductorUbicacion> historial = service.historial(productorId);

        // Leer el productor es lo que rompia: aqui ya no hay sesion abierta.
        assertThatCode(() -> historial.forEach(u -> u.getProductor().getNombres()))
                .doesNotThrowAnyException();
        assertThat(historial).isNotEmpty();
    }

    @Test
    void ultimasPorProductorDevuelveSoloLaMasRecienteDeCadaUno() {
        Long productorId = algunProductor().getId();
        service.registrar(
                productorId, new ProductorUbicacionRequest(-15.10, -70.10, null, TipoUbicacion.CASA));
        ProductorUbicacion ultima = service.registrar(
                productorId, new ProductorUbicacionRequest(-15.20, -70.20, null, TipoUbicacion.ACTUAL));

        Map<Long, ProductorUbicacion> ultimas = service.ultimasPorProductor(List.of(productorId));

        assertThat(ultimas).hasSize(1);
        assertThat(ultimas.get(productorId).getId()).isEqualTo(ultima.getId());
        assertThat(ultimas.get(productorId).getTipo()).isEqualTo(TipoUbicacion.ACTUAL);
        assertThatCode(() -> ultimas.get(productorId).getProductor().getNombres())
                .doesNotThrowAnyException();
    }

    @Test
    void sinProveedoresNoSeConsultaNada() {
        assertThat(service.ultimasPorProductor(List.of())).isEmpty();
    }

    @Test
    void unProveedorSinUbicacionNoApareceEnElMapa() {
        Long conUbicacion = algunProductor().getId();
        service.registrar(
                conUbicacion, new ProductorUbicacionRequest(-15.30, -70.30, null, TipoUbicacion.CASA));
        Long sinUbicacion = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .map(Productor::getId)
                .filter(id -> !id.equals(conUbicacion))
                .filter(id -> service.historial(id).isEmpty())
                .findFirst()
                .orElseThrow();

        Map<Long, ProductorUbicacion> ultimas =
                service.ultimasPorProductor(List.of(conUbicacion, sinUbicacion));

        assertThat(ultimas).containsKey(conUbicacion).doesNotContainKey(sinUbicacion);
    }
}
