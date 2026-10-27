package org.ramiro.huata.mensajeria;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeProveedorRepository extends JpaRepository<MensajeProveedor, Long> {
    List<MensajeProveedor> findByConversacion_IdAndActivoTrueOrderByCreatedAtAsc(Long conversacionId);

    /** Idempotencia: el mismo clientUuid es el mismo mensaje (V44). */
    Optional<MensajeProveedor> findByClientUuid(String clientUuid);

    /** Sincronizacion de la planta al reconectar: todo lo posterior al ultimo id que tiene. */
    List<MensajeProveedor> findByIdGreaterThanAndActivoTrueOrderByIdAsc(Long desdeId, Pageable pagina);

    /** Sincronizacion del proveedor al reconectar: solo su conversacion. */
    List<MensajeProveedor> findByConversacion_Productor_IdAndIdGreaterThanAndActivoTrueOrderByIdAsc(
            Long productorId, Long desdeId, Pageable pagina);
    long countByConversacion_IdAndAutor_IdNotAndLeidoPorPlantaAtIsNullAndActivoTrue(Long conversacionId, Long autorId);
    long countByConversacion_IdAndAutor_IdNotAndLeidoPorProveedorAtIsNullAndActivoTrue(Long conversacionId, Long autorId);
}
