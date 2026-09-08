package org.ramiro.huata.liquidaciones;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarifaLecheRepository extends JpaRepository<TarifaLeche, Long> {

    Optional<TarifaLeche> findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate fecha);
}
