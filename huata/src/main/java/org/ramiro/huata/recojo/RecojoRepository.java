package org.ramiro.huata.recojo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecojoRepository extends JpaRepository<Recojo, Long>, JpaSpecificationExecutor<Recojo> {

    Optional<Recojo> findByClientUuid(String clientUuid);

    List<Recojo> findByProductorIdAndEstadoNotOrderByFechaHoraDesc(Long productorId, EstadoRecojo estado);

    List<Recojo> findByFechaHoraBetweenAndEstadoNot(Instant desde, Instant hasta, EstadoRecojo estado);

    List<Recojo> findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
            Long productorId, Instant desde, Instant hasta, EstadoRecojo estado);
}
