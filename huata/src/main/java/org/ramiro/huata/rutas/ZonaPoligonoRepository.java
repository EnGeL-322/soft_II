package org.ramiro.huata.rutas;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaPoligonoRepository extends JpaRepository<ZonaPoligono, Long> {

    Optional<ZonaPoligono> findByZonaIgnoreCase(String zona);

    List<ZonaPoligono> findByActivoTrueOrderByZonaAsc();
}
