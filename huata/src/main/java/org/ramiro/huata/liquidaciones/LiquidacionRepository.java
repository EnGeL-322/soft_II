package org.ramiro.huata.liquidaciones;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LiquidacionRepository extends JpaRepository<Liquidacion, Long>, JpaSpecificationExecutor<Liquidacion> {

    /**
     * Solo las activas: V4 inactiva los duplicados historicos en vez de borrarlos, y sin este filtro
     * esta consulta volveria a fallar por resultado no unico al toparse con ellos.
     */
    Optional<Liquidacion> findByProductorIdAndPeriodoInicioAndPeriodoFinAndActivoTrue(
            Long productorId, LocalDate inicio, LocalDate fin);

    List<Liquidacion> findByProductorIdAndActivoTrueAndEstadoAndPeriodoFinGreaterThanEqualAndPeriodoInicioLessThanEqual(
            Long productorId, EstadoLiquidacion estado, LocalDate desde, LocalDate hasta);

    /** Liquidaciones ya pagadas de un ciclo exacto (jueves-miercoles), para el "pago a proveedores de leche" del reporte semanal de queso. */
    List<Liquidacion> findByActivoTrueAndEstadoAndPeriodoInicioAndPeriodoFin(
            EstadoLiquidacion estado, LocalDate periodoInicio, LocalDate periodoFin);
}
