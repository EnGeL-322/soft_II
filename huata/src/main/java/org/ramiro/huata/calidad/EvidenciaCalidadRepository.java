package org.ramiro.huata.calidad;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenciaCalidadRepository extends JpaRepository<EvidenciaCalidad, Long> {

    List<EvidenciaCalidad> findByControlCalidadId(Long controlCalidadId);

    List<EvidenciaCalidad> findByIncidenciaId(Long incidenciaId);
}
