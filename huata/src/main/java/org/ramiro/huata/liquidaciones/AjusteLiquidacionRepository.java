package org.ramiro.huata.liquidaciones;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AjusteLiquidacionRepository extends JpaRepository<AjusteLiquidacion, Long> {
    List<AjusteLiquidacion> findByLiquidacionIdOrderByIdAsc(Long liquidacionId);
}
