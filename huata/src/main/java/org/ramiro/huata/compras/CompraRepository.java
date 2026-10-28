package org.ramiro.huata.compras;

import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Page<Compra> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta, Pageable pageable);

    Page<Compra> findAllByOrderByFechaDescIdDesc(Pageable pageable);

    /** Compras vigentes que no estan en ese estado; con RECIBIDA: lo que falta recibir (V61). */
    java.util.List<Compra> findByActivoTrueAndEstadoNotOrderByFechaAscIdAsc(EstadoCompra estado);

    /** Sin paginar: usado por el reporte semanal de queso, que necesita el detalle completo del ciclo. */
    java.util.List<Compra> findByFechaBetweenOrderByFechaDescIdDesc(LocalDate desde, LocalDate hasta);
}
