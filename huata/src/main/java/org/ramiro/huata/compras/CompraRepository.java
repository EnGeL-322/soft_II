package org.ramiro.huata.compras;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Page<Compra> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Compra> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    /** Sin paginar: usado por el reporte semanal de queso, que necesita el detalle completo del ciclo. */
    java.util.List<Compra> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);
}
