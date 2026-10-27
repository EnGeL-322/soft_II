package org.ramiro.huata.recojo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConciliacionRutaRepository extends JpaRepository<ConciliacionRuta, Long> {

    List<ConciliacionRuta> findByFechaOrderByRuta_Nombre(LocalDate fecha);

    Optional<ConciliacionRuta> findByFechaAndRuta_Id(LocalDate fecha, Long rutaId);
}
