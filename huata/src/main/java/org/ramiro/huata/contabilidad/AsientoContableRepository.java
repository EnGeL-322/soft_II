package org.ramiro.huata.contabilidad;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoContableRepository extends JpaRepository<AsientoContable, Long> {

    /**
     * Join fetch de lineas y cuenta: el libro diario recorre cada linea de cada asiento para
     * imprimirla, y sin el fetch serian N+1 consultas (mismo bug que ya se corrigio en
     * SolicitudCambioZonaRepository con spring.jpa.open-in-view=false).
     */
    @Query(
            "select distinct a from AsientoContable a "
                    + "left join fetch a.lineas l left join fetch l.cuenta "
                    + "where a.activo = true and a.fecha between :desde and :hasta "
                    + "order by a.fecha asc, a.id asc")
    List<AsientoContable> findConLineasByFechaBetween(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
