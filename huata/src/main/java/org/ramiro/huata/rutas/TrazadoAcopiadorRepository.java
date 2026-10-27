package org.ramiro.huata.rutas;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrazadoAcopiadorRepository extends JpaRepository<TrazadoAcopiador, Long> {

    Optional<TrazadoAcopiador> findByAcopiador_IdAndTipoAndFecha(Long acopiadorId, TipoTrazado tipo, LocalDate fecha);

    Optional<TrazadoAcopiador> findByAcopiador_IdAndTipo(Long acopiadorId, TipoTrazado tipo);

    /**
     * Trazados de un tipo y una fecha, con acopiador y ruta ya traidos. Con
     * {@code open-in-view=false} y una fila por acopiador, resolverlos uno por uno serian N consultas
     * extra al pintar la pantalla.
     */
    @Query("select t from TrazadoAcopiador t join fetch t.acopiador join fetch t.ruta "
            + "where t.tipo = :tipo and t.fecha = :fecha "
            + "order by t.acopiador.nombres asc, t.id asc")
    List<TrazadoAcopiador> deTipoEnFecha(@Param("tipo") TipoTrazado tipo, @Param("fecha") LocalDate fecha);

    /**
     * Los de un tipo sin filtrar por fecha. Existe como consulta aparte, y no como un
     * {@code (:fecha is null or t.fecha = :fecha)} en la de arriba, porque comparar un parametro
     * contra null en JPQL obliga a la base a inferir el tipo de un bind sin valor: H2 lo tolera y
     * PostgreSQL puede responder "could not determine data type of parameter". Seria un fallo que
     * solo aparece en produccion.
     *
     * <p>Se usa para los PREDETERMINADOS, que no tienen fecha y estan acotados a uno por acopiador.
     * No se expone para los del dia: esos sin fecha crecen sin techo (ver el controlador).
     */
    @Query("select t from TrazadoAcopiador t join fetch t.acopiador join fetch t.ruta "
            + "where t.tipo = :tipo order by t.acopiador.nombres asc, t.id asc")
    List<TrazadoAcopiador> deTipo(@Param("tipo") TipoTrazado tipo);
}
