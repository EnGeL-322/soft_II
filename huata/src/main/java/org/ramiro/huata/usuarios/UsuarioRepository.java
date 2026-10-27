package org.ramiro.huata.usuarios;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByUsernameIgnoreCaseAndActivoTrue(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<Usuario> findByActivoTrue();

    long countByActivoTrueAndRoles_Id(Long rolId);

    /**
     * Consulta directa y no save(): guardar la entidad moveria updated_at/updated_by en cada login, y
     * esas columnas deben decir quien cambio la cuenta, no quien entro.
     */
    @Modifying
    @Transactional
    @Query("update Usuario u set u.ultimoAcceso = :momento where u.id = :id")
    void marcarUltimoAcceso(@Param("id") Long id, @Param("momento") Instant momento);
}
