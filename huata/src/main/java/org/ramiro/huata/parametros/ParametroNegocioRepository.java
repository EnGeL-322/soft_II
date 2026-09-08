package org.ramiro.huata.parametros;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroNegocioRepository extends JpaRepository<ParametroNegocio, Long> {
    Optional<ParametroNegocio> findByClave(String clave);
}
