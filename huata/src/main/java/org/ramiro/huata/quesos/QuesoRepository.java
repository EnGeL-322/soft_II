package org.ramiro.huata.quesos;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuesoRepository extends JpaRepository<Queso, Long> {
    List<Queso> findByFechaBetweenOrderByFechaDesc(LocalDate desde, LocalDate hasta);
}
