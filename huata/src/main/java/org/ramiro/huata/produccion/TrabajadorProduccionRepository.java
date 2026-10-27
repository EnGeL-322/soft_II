package org.ramiro.huata.produccion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrabajadorProduccionRepository extends JpaRepository<TrabajadorProduccion, Long> {

    List<TrabajadorProduccion> findByActivoTrueOrderByNombre();
}
