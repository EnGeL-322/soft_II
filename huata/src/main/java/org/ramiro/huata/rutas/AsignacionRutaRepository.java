package org.ramiro.huata.rutas;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsignacionRutaRepository extends JpaRepository<AsignacionRuta, Long> {

    List<AsignacionRuta> findByFechaAndActivoTrue(LocalDate fecha);

    List<AsignacionRuta> findByAcopiadorIdAndFechaAndActivoTrue(Long acopiadorId, LocalDate fecha);
}
