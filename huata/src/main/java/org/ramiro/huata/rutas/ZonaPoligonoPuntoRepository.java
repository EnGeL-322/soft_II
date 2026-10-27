package org.ramiro.huata.rutas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZonaPoligonoPuntoRepository extends JpaRepository<ZonaPoligonoPunto, Long> {

    List<ZonaPoligonoPunto> findByZonaPoligono_IdOrderBySecuenciaAsc(Long zonaPoligonoId);

    void deleteByZonaPoligono_Id(Long zonaPoligonoId);
}
