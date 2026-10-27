package org.ramiro.huata.productores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudCambioZonaRepository extends JpaRepository<SolicitudCambioZona, Long> {

    /**
     * {@code join fetch s.productor} a propósito: con {@code spring.jpa.open-in-view=false} (dev y
     * prod), la sesión de Hibernate ya está cerrada cuando el controlador mapea la lista a DTO. Sin
     * el fetch explícito, {@code SolicitudCambioZonaResponse.from()} lanza
     * {@code LazyInitializationException} en cuanto toca {@code getProductor()} — bug real
     * encontrado al probar {@code GET /productores/me/solicitudes-zona} contra datos reales; afecta
     * a los tres métodos de abajo por igual, no solo al endpoint nuevo.
     */
    @Query("select s from SolicitudCambioZona s join fetch s.productor where s.estado = :estado order by s.fechaSolicitud desc")
    List<SolicitudCambioZona> findByEstadoOrderByFechaSolicitudDesc(@Param("estado") EstadoSolicitudZona estado);

    @Query("select s from SolicitudCambioZona s join fetch s.productor where s.productor.id = :productorId order by s.fechaSolicitud desc")
    List<SolicitudCambioZona> findByProductor_IdOrderByFechaSolicitudDesc(@Param("productorId") Long productorId);

    @Query("select s from SolicitudCambioZona s join fetch s.productor order by s.fechaSolicitud desc")
    List<SolicitudCambioZona> findAllByOrderByFechaSolicitudDesc();
}
