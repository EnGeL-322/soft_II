package org.ramiro.huata.produccion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenProduccionRepository extends JpaRepository<OrdenProduccion, Long> {

    /** En el orden en que reservan el almacen: la que se planifico antes, primero. */
    List<OrdenProduccion> findByEstadoOrderByFechaPlanAscIdAsc(OrdenProduccion.Estado estado);

    List<OrdenProduccion> findAllByOrderByFechaPlanDescIdDesc();

    Optional<OrdenProduccion> findByLote_Id(Long loteId);
}
