package org.ramiro.huata.produccion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteConsumoRepository extends JpaRepository<LoteConsumo, Long> {

    List<LoteConsumo> findByLote_IdOrderById(Long loteId);
}
