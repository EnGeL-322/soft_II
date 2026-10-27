package org.ramiro.huata.rutas;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrazadoAcopiadorPuntoRepository extends JpaRepository<TrazadoAcopiadorPunto, Long> {

    List<TrazadoAcopiadorPunto> findByTrazado_IdOrderBySecuenciaAsc(Long trazadoId);

    /** Los puntos de varios trazados de una vez, para no consultar uno por fila de la pantalla. */
    List<TrazadoAcopiadorPunto> findByTrazado_IdInOrderByTrazado_IdAscSecuenciaAsc(Collection<Long> trazadoIds);

    void deleteByTrazado_Id(Long trazadoId);
}
