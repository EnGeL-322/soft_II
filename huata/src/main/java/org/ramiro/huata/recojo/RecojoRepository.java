package org.ramiro.huata.recojo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecojoRepository extends JpaRepository<Recojo, Long>, JpaSpecificationExecutor<Recojo> {

    Optional<Recojo> findByClientUuid(String clientUuid);

    List<Recojo> findByProductorIdAndEstadoNotOrderByFechaHoraDesc(Long productorId, EstadoRecojo estado);

    List<Recojo> findByFechaHoraBetweenAndEstadoNot(Instant desde, Instant hasta, EstadoRecojo estado);

    List<Recojo> findByFechaHoraGreaterThanEqualAndFechaHoraLessThanAndEstadoNot(
            Instant desde, Instant hasta, EstadoRecojo estado);

    List<Recojo> findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
            Long productorId, Instant desde, Instant hasta, EstadoRecojo estado);

    /**
     * Recojos de un dia con proveedor, ruta y acopiador ya traidos, para armar el detalle de la
     * conciliacion. Con {@code open-in-view=false} y un detalle por parada, resolver el proveedor
     * fila por fila serian N consultas extra por cada ruta que se pinta (y en los mensajes del
     * WebSocket, N por cada acopio registrado).
     */
    @Query("select r from Recojo r join fetch r.productor left join fetch r.ruta left join fetch r.acopiador "
            + "where r.fechaHora >= :desde and r.fechaHora < :hasta and r.estado <> :estado "
            + "order by r.fechaHora asc")
    List<Recojo> delDiaConDetalle(
            @Param("desde") Instant desde, @Param("hasta") Instant hasta, @Param("estado") EstadoRecojo estado);
}
