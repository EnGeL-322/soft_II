package org.ramiro.huata.rutas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RutaRepository extends JpaRepository<Ruta, Long>, JpaSpecificationExecutor<Ruta> {

    List<Ruta> findByActivoTrueOrderByZonaAscNombreAsc();

    /** Rutas de una zona: la zona es texto libre en Ruta.zona (decision C-05), no una tabla maestra. */
    List<Ruta> findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(String zona);
}
