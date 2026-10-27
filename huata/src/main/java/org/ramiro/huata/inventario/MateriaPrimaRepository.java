package org.ramiro.huata.inventario;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MateriaPrimaRepository extends JpaRepository<MateriaPrima, Long> {

    List<MateriaPrima> findByActivoTrueOrderByNombre();

    boolean existsByNombreIgnoreCase(String nombre);

    /** La materia prima marcada como leche del acopio (V31). */
    Optional<MateriaPrima> findFirstByLecheAcopioTrue();

    Optional<MateriaPrima> findFirstByNombreIgnoreCase(String nombre);

    /**
     * Bloquea la fila de la materia prima mientras se consume en un lote de produccion.
     *
     * <p>Sin esto, dos lotes que consumen la misma materia prima al mismo tiempo leen el mismo stock
     * disponible, los dos lo encuentran suficiente y los dos descuentan: el kardex termina en
     * negativo sin que nadie lo haya permitido. Quien consume varias materias primas a la vez debe
     * pedir este lock en orden ascendente de id para no producir un deadlock con otro lote que las
     * consume en orden distinto.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MateriaPrima m where m.id = :id")
    Optional<MateriaPrima> findParaConsumirById(@Param("id") Long id);
}
