package org.ramiro.huata.ubicaciones;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UbicacionGpsRepository extends JpaRepository<UbicacionGps, Long> {

    Optional<UbicacionGps> findTopByAcopiadorIdOrderByFechaDesc(Long acopiadorId);

    List<UbicacionGps> findByFechaAfterOrderByFechaDesc(Instant desde);

    List<UbicacionGps> findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(Long acopiadorId, Instant desde, Instant hasta);

    /**
     * El ultimo punto de cada acopiador, resuelto en la base. Reemplaza a traer la tabla entera y
     * quedarse con el primero de cada uno en memoria: con un punto cada 10 m la tabla crece unos
     * miles de filas por acopiador y dia, y esa lectura se hacia cada vez que alguien abria Rutas.
     * Usa el indice (acopiador_id, fecha) de V30.
     */
    @Query("select u from UbicacionGps u join fetch u.acopiador a"
            + " where u.fecha = (select max(u2.fecha) from UbicacionGps u2 where u2.acopiador = u.acopiador)"
            + " order by u.fecha desc")
    List<UbicacionGps> ultimaDeCadaAcopiador();
}
