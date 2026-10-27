package org.ramiro.huata.ingesta;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicionInstrumentoRepository extends JpaRepository<MedicionInstrumento, Long> {
    Optional<MedicionInstrumento> findByRecojoId(Long recojoId);
}
