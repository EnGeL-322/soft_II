package org.ramiro.huata.produccion;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecetaQuesoRepository extends JpaRepository<RecetaQueso, Long> {

    boolean existsByProductoId(Long productoId);

    List<RecetaQueso> findByProductoId(Long productoId);

    List<RecetaQueso> findByActivoTrueOrderByProductoNombreAscVigenteDesdeDesc();

    /** La version vigente de la receta de un producto en una fecha dada. */
    Optional<RecetaQueso> findTopByActivoTrueAndProductoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            Long productoId, LocalDate fecha);
}
