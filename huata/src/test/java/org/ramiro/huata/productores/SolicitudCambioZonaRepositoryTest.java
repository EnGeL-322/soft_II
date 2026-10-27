package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regresión de un bug real: con {@code spring.jpa.open-in-view=false} (dev y prod), guardar y leer
 * pasan por transacciones distintas, así que la sesión de Hibernate que guardó la solicitud ya está
 * cerrada cuando se lee. Sin {@code join fetch} en el repositorio,
 * {@code SolicitudCambioZonaResponse.from()} revienta con {@code LazyInitializationException} en
 * cuanto toca {@code getProductor()} — se detectó probando
 * {@code GET /productores/me/solicitudes-zona} contra datos reales en Docker, no en un test.
 *
 * <p>A propósito SIN {@code @Transactional}: esa anotación en el test mantendría una única sesión
 * abierta durante todo el método y ocultaría exactamente el bug que se quiere cubrir.
 */
@SpringBootTest
class SolicitudCambioZonaRepositoryTest {

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private SolicitudCambioZonaRepository repository;

    @Test
    void elProductorDeLaSolicitudSeLeeSinAbrirUnaNuevaSesion() {
        Productor productor = new Productor();
        productor.setCodigo("SCZ-" + UUID.randomUUID().toString().substring(0, 6));
        productor.setNombres("Flora");
        productor.setApellidos("Ticona");
        productor.setEstado(EstadoProductor.ACTIVO);
        productor.setZonaPrincipal("Zona X");
        productor = productorRepository.save(productor);

        SolicitudCambioZona solicitud = new SolicitudCambioZona();
        solicitud.setProductor(productor);
        solicitud.setZonaActual("Zona X");
        solicitud.setZonaSolicitada("Zona Y");
        solicitud.setMotivo("Cambio de residencia");
        solicitud.setEstado(EstadoSolicitudZona.PENDIENTE);
        solicitud.setFechaSolicitud(LocalDate.now());
        repository.save(solicitud);

        // Nueva transacción/sesión: aquí es exactamente donde fallaba antes del join fetch.
        List<SolicitudCambioZona> solicitudes = repository.findByProductor_IdOrderByFechaSolicitudDesc(productor.getId());

        assertThat(solicitudes).hasSize(1);
        assertThat(solicitudes.get(0).getProductor().getNombres()).isEqualTo("Flora");
        // Mismo mapeo que usa el controlador: si el fetch no alcanzara a productor, esto ya
        // habría lanzado LazyInitializationException en la línea anterior.
        assertThat(SolicitudCambioZonaResponse.from(solicitudes.get(0)).productorNombre()).isEqualTo("Flora Ticona");
    }
}
