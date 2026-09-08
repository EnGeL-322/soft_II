package org.ramiro.huata.auditoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {
    Page<RegistroAuditoria> findAllByOrderByFechaDesc(Pageable pageable);
}
