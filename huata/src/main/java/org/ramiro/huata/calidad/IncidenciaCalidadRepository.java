package org.ramiro.huata.calidad;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidenciaCalidadRepository extends JpaRepository<IncidenciaCalidad, Long>, JpaSpecificationExecutor<IncidenciaCalidad> {

    List<IncidenciaCalidad> findByProductorIdOrderByFechaDesc(Long productorId);

    /** Incidencias del dia para el aviso publico (RF-49) y el aviso activo a los demas proveedores. */
    List<IncidenciaCalidad> findByFechaBetweenOrderByFechaDesc(Instant desde, Instant hasta);
}
