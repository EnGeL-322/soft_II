package org.ramiro.huata.reportes;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CierreAcopioRepository extends JpaRepository<CierreAcopio, Long> {
    Optional<CierreAcopio> findByFecha(LocalDate fecha);
}
