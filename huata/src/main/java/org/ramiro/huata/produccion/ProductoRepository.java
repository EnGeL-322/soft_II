package org.ramiro.huata.produccion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findByActivoTrueOrderByNombreAsc();

    List<Producto> findAllByOrderByActivoDescNombreAsc();

    Optional<Producto> findByNombreIgnoreCase(String nombre);
}
