package org.ramiro.huata.rutas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RutaPuntoRepository extends JpaRepository<RutaPunto, Long> {

    List<RutaPunto> findByRuta_IdOrderBySecuenciaAsc(Long rutaId);

    void deleteByRuta_Id(Long rutaId);
}
