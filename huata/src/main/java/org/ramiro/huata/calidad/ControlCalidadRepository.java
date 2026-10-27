package org.ramiro.huata.calidad;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ControlCalidadRepository extends JpaRepository<ControlCalidad, Long>, JpaSpecificationExecutor<ControlCalidad> {

    Optional<ControlCalidad> findByRecojoId(Long recojoId);

    List<ControlCalidad> findByRecojo_ProductorIdOrderByCreatedAtDesc(Long productorId);

    /** Historial completo del proveedor, con y sin recojo (V47), del analisis mas reciente al mas antiguo. */
    List<ControlCalidad> findByProductor_IdOrderByFechaHoraDesc(Long productorId);

    /** Control de campo de un proveedor en un dia que todavia espera el recojo del acopiador. */
    Optional<ControlCalidad> findFirstByProductor_IdAndRecojoIsNullAndLugarAnalisisAndFechaHoraBetweenOrderByFechaHoraDesc(
            Long productorId, LugarAnalisis lugar, Instant desde, Instant hasta);

    /** Pendientes que nunca se evaluaron contra el estandar (anteriores a V50). */
    List<ControlCalidad> findByAprobadoEnIsNullAndAdvertenciaIsNull();

    /** Analisis todavia sin aprobar en un rango de fechas (V48), del mas reciente al mas antiguo. */
    List<ControlCalidad> findByAprobadoEnIsNullAndFechaHoraBetweenOrderByFechaHoraDesc(Instant desde, Instant hasta);

    /** Todos los analisis de un rango de fechas, aprobados o no, del mas reciente al mas antiguo. */
    List<ControlCalidad> findByFechaHoraBetweenOrderByFechaHoraDesc(Instant desde, Instant hasta);

    /** Controles sin recojo en un rango de fechas: los que la pantalla de calidad muestra por vincular. */
    List<ControlCalidad> findByRecojoIsNullAndFechaHoraBetweenOrderByFechaHoraDesc(Instant desde, Instant hasta);

    /** Controles del portal publico por zona (RF-48): se agrupan por la fecha real del recojo, no por
     * cuando se cargo el analisis. */
    List<ControlCalidad> findByRecojo_FechaHoraBetween(Instant desde, Instant hasta);

    /** Detecciones de mala calidad de un proveedor, sin contar entregas anuladas (retiro, V33). */
    long countByRecojo_ProductorIdAndResultadoInAndRecojo_EstadoNot(
            Long productorId, java.util.Collection<ResultadoCalidad> resultados, org.ramiro.huata.recojo.EstadoRecojo estado);
}
