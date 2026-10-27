package org.ramiro.huata.mensajeria;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversacionProveedorRepository extends JpaRepository<ConversacionProveedor, Long> {
    Optional<ConversacionProveedor> findByProductor_Id(Long productorId);
    List<ConversacionProveedor> findAllByOrderByUpdatedAtDesc();
}
