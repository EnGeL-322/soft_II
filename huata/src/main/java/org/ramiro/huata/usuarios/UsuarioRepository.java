package org.ramiro.huata.usuarios;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByUsernameIgnoreCaseAndActivoTrue(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
