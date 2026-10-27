package org.ramiro.huata.quesos;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VentaQuesoRepository extends JpaRepository<VentaQueso, Long> {

    List<VentaQueso> findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);

    /** Historial de un cliente: el nombre se compara sin mayusculas ni espacios de sobra. */
    @org.springframework.data.jpa.repository.Query(
            "select v from VentaQueso v join fetch v.lote l join fetch l.producto"
                    + " where lower(trim(v.cliente)) = :cliente and v.fecha between :desde and :hasta"
                    + " order by v.fecha desc, v.id desc")
    List<VentaQueso> historialDe(
            @org.springframework.data.repository.query.Param("cliente") String cliente,
            @org.springframework.data.repository.query.Param("desde") LocalDate desde,
            @org.springframework.data.repository.query.Param("hasta") LocalDate hasta);
}
