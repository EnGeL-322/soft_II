package org.ramiro.huata.acopiadores;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcopiadorRepository extends JpaRepository<Acopiador, Long>, JpaSpecificationExecutor<Acopiador> {}
