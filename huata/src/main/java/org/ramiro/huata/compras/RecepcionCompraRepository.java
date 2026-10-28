package org.ramiro.huata.compras;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RecepcionCompraRepository extends JpaRepository<RecepcionCompra, Long> {

    /** Todas, con lo necesario para listarlas sin una consulta por fila. */
    @Query("select distinct r from RecepcionCompra r join fetch r.compra c join fetch c.proveedor"
            + " left join fetch r.items i left join fetch i.compraItem ci left join fetch ci.materiaPrima"
            + " order by r.fecha desc, r.id desc")
    List<RecepcionCompra> todasConDetalle();
}
