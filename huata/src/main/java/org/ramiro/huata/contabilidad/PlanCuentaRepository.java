package org.ramiro.huata.contabilidad;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCuentaRepository extends JpaRepository<PlanCuenta, Long> {

    List<PlanCuenta> findByActivoTrueOrderByCodigoAsc();

    Optional<PlanCuenta> findByCodigoAndActivoTrue(String codigo);
}
