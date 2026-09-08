package org.ramiro.huata.productores;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductorRepository extends JpaRepository<Productor, Long>, JpaSpecificationExecutor<Productor> {

    Optional<Productor> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
