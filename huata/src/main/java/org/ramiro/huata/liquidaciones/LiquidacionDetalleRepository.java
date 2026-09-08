package org.ramiro.huata.liquidaciones;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiquidacionDetalleRepository extends JpaRepository<LiquidacionDetalle, Long> {

    List<LiquidacionDetalle> findByLiquidacionId(Long liquidacionId);
}
