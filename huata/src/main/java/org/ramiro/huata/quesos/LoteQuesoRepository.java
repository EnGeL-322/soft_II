package org.ramiro.huata.quesos;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteQuesoRepository extends JpaRepository<LoteQueso, Long> {

    boolean existsByProductoId(Long productoId);

    List<LoteQueso> findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);

    List<LoteQueso> findByActivoTrueOrderByFechaDescIdDesc();

    /**
     * Bloquea la fila del lote mientras se registra una venta.
     *
     * <p>Sin esto, dos ventas simultaneas de las ultimas unidades leen las dos el mismo stock, las
     * dos lo encuentran suficiente y las dos se guardan: el lote acaba vendiendo mas de lo que
     * produjo. Comprobar el stock antes de escribir no basta si nadie impide que el otro lo
     * comprueba a la vez.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoteQueso l where l.id = :id")
    Optional<LoteQueso> findParaVenderById(@Param("id") Long id);
}
