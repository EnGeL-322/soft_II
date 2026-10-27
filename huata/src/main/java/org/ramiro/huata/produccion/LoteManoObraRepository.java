package org.ramiro.huata.produccion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoteManoObraRepository extends JpaRepository<LoteManoObra, Long> {

    List<LoteManoObra> findByLote_IdOrderById(Long loteId);
}
