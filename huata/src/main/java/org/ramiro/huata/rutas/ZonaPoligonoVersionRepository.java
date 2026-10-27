package org.ramiro.huata.rutas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaPoligonoVersionRepository extends JpaRepository<ZonaPoligonoVersion, Long> {

    List<ZonaPoligonoVersion> findTop20ByZonaIgnoreCaseOrderByCreatedAtDescIdDesc(String zona);
}
