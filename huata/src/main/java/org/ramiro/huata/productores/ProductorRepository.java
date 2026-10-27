package org.ramiro.huata.productores;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductorRepository extends JpaRepository<Productor, Long>, JpaSpecificationExecutor<Productor> {

    Optional<Productor> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    /**
     * Para que dos proveedores no compartan DNI. Hoy pasa: en la base de desarrollo el documento
     * "987654321" esta repetido, porque nunca se comprobo.
     */
    boolean existsByDocumentoIdentidad(String documentoIdentidad);

    boolean existsByDocumentoIdentidadAndIdNot(String documentoIdentidad, Long id);

    Optional<Productor> findByUsuario_UsernameIgnoreCase(String username);

    /** "Mis proveedores hoy" para el acopiador: los de su ruta, en orden de visita. */
    List<Productor> findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(Long rutaId);

    List<Productor> findByAcopiadorResponsable_IdAndActivoTrue(Long acopiadorId);

    /** Bandeja de mensajería: incluye también a quien todavía no inició una conversación. */
    List<Productor> findByActivoTrueOrderByNombresAscApellidosAsc();
}
