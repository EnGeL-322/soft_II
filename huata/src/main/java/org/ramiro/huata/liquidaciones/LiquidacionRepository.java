package org.ramiro.huata.liquidaciones;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long>, JpaSpecificationExecutor<Liquidacion> {

    Optional<Liquidacion> findByProductorIdAndPeriodoInicioAndPeriodoFin(Long productorId, LocalDate inicio, LocalDate fin);
}
