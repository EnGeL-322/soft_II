package org.ramiro.huata.verificacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoVerificacionRepository extends JpaRepository<CodigoVerificacion, Long> {

    /** El ultimo emitido a esta persona para este proposito, vigente o no. */
    Optional<CodigoVerificacion> findTopBySolicitanteAndPropositoOrderByIdDesc(
            String solicitante, PropositoVerificacion proposito);

    /**
     * Los que siguen vivos. Al emitir uno nuevo se invalidan: dos codigos validos a la vez duplican
     * la superficie de acierto y dejan al usuario sin saber cual de los dos correos vale.
     */
    List<CodigoVerificacion> findBySolicitanteAndPropositoAndUsadoFalseAndActivoTrue(
            String solicitante, PropositoVerificacion proposito);
}
