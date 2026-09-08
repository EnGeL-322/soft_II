package org.ramiro.huata.acopiadores;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcopiadorRepository extends JpaRepository<Acopiador, Long>, JpaSpecificationExecutor<Acopiador> {

    Optional<Acopiador> findByUsuario_UsernameIgnoreCase(String username);
}
