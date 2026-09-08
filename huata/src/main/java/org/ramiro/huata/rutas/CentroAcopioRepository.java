package org.ramiro.huata.rutas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CentroAcopioRepository extends JpaRepository<CentroAcopio, Long>, JpaSpecificationExecutor<CentroAcopio> {}
