package org.ramiro.huata.usuarios;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VistaRepository extends JpaRepository<Vista, Long> {

    List<Vista> findByActivoTrueOrderByOrdenAsc();
}
