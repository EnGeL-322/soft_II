package org.ramiro.huata.quesos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TarifaQuesoRepository extends JpaRepository<TarifaQueso, Long> {

    boolean existsByProductoId(Long productoId);

    /**
     * Periodos de un producto que cubren la fecha: ya empezaron y, si tienen fin, todavia no
     * terminaron. El primero es el que manda (el que empezo mas tarde; a igual inicio, el ultimo
     * registrado).
     */
    @Query("select t from TarifaQueso t where t.activo = true and t.producto.id = :productoId"
            + " and t.vigenteDesde <= :fecha and (t.vigenteHasta is null or t.vigenteHasta >= :fecha)"
            + " order by t.vigenteDesde desc, t.id desc")
    List<TarifaQueso> vigentesEn(@Param("productoId") Long productoId, @Param("fecha") LocalDate fecha);

    List<TarifaQueso> findByActivoTrueOrderByProductoNombreAscVigenteDesdeDesc();

    List<TarifaQueso> findByActivoTrueAndProductoIdOrderByVigenteDesdeDesc(Long productoId);
}
