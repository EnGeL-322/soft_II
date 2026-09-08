package org.ramiro.huata.ubicaciones;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionGpsRepository extends JpaRepository<UbicacionGps, Long> {

    Optional<UbicacionGps> findTopByAcopiadorIdOrderByFechaDesc(Long acopiadorId);

    List<UbicacionGps> findByFechaAfterOrderByFechaDesc(Instant desde);

    List<UbicacionGps> findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(Long acopiadorId, Instant desde, Instant hasta);
}
