package org.ramiro.huata.compras;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProveedorCompraRepository extends JpaRepository<ProveedorCompra, Long> {

    List<ProveedorCompra> findByActivoTrueOrderByNombre();
}
