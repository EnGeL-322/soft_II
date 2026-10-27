package org.ramiro.huata.rutas;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuntoEstablecidoRepository extends JpaRepository<PuntoEstablecido, Long> {
    List<PuntoEstablecido> findByEliminadoFalseOrderByNombreAsc();
    List<PuntoEstablecido> findByRuta_IdAndEliminadoFalseOrderByNombreAsc(Long rutaId);
    List<PuntoEstablecido> findByRuta_IdOrderByNombreAsc(Long rutaId);
    List<PuntoEstablecido> findByAcopiador_IdOrderByNombreAsc(Long acopiadorId);

    List<PuntoEstablecido> findByActivoTrueOrderByNombreAsc();

    List<PuntoEstablecido> findByRuta_IdAndActivoTrueOrderByNombreAsc(Long rutaId);

    List<PuntoEstablecido> findByAcopiador_IdAndActivoTrueOrderByNombreAsc(Long acopiadorId);

    /** Usado por RecojoService: el o los puntos establecidos activos de un proveedor, si tiene. */
    List<PuntoEstablecido> findByProveedor_IdAndActivoTrue(Long proveedorId);
}
