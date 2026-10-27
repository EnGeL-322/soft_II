package org.ramiro.huata.usuarios;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuloRepository extends JpaRepository<Modulo, Long> {

    Optional<Modulo> findByCodigo(String codigo);

    List<Modulo> findByActivoTrueOrderByOrdenAsc();
}
