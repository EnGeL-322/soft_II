package org.ramiro.huata.notificaciones;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @Query("select n from Notificacion n where n.usuario.id = :usuarioId or n.usuario is null order by n.createdAt desc")
    List<Notificacion> buscarParaUsuario(@Param("usuarioId") Long usuarioId);
}
