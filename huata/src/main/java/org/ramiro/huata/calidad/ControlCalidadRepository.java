package org.ramiro.huata.calidad;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ControlCalidadRepository extends JpaRepository<ControlCalidad, Long>, JpaSpecificationExecutor<ControlCalidad> {

    Optional<ControlCalidad> findByRecojoId(Long recojoId);

    List<ControlCalidad> findByRecojo_ProductorIdOrderByCreatedAtDesc(Long productorId);
}
