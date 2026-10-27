package org.ramiro.huata.contabilidad;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoLineaRepository extends JpaRepository<AsientoLinea, Long> {

    /** Movimientos de una cuenta especifica (libro mayor), con el asiento ya cargado para no golpear N+1. */
    @Query(
            "select l from AsientoLinea l join fetch l.asiento a "
                    + "where l.activo = true and a.activo = true and l.cuenta.codigo = :codigoCuenta "
                    + "and a.fecha between :desde and :hasta "
                    + "order by a.fecha asc, a.id asc, l.id asc")
    List<AsientoLinea> findPorCuentaYFecha(
            @Param("codigoCuenta") String codigoCuenta, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** Cuentas que de verdad tienen movimiento, para no ofrecer en el selector cuentas siempre vacias. */
    @Query("select distinct l.cuenta.codigo from AsientoLinea l where l.activo = true")
    List<String> codigosDeCuentasConMovimiento();
}
