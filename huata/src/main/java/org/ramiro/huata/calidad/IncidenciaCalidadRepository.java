package org.ramiro.huata.calidad;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidenciaCalidadRepository extends JpaRepository<IncidenciaCalidad, Long>, JpaSpecificationExecutor<IncidenciaCalidad> {

    List<IncidenciaCalidad> findByProductorIdOrderByFechaDesc(Long productorId);
}
