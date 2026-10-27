package org.ramiro.huata.calidad;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroCalidadRepository extends JpaRepository<ParametroCalidad, Long> {

    Optional<ParametroCalidad> findByNombreIgnoreCase(String nombre);
}
