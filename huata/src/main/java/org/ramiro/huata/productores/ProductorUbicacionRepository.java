package org.ramiro.huata.productores;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductorUbicacionRepository extends JpaRepository<ProductorUbicacion, Long> {

    /**
     * {@code join fetch u.productor} a proposito: con {@code spring.jpa.open-in-view=false} el
     * proxy LAZY del productor ya no se puede inicializar cuando
     * {@code ProductorUbicacionResponse.from} lee su nombre, y el endpoint respondia 500 con
     * {@code LazyInitializationException}. Es el mismo caso que se corrigio en
     * {@link SolicitudCambioZonaRepository}; aqui no se habia visto porque la tabla estaba vacia.
     */
    @Query("select u from ProductorUbicacion u join fetch u.productor"
            + " where u.productor.id = :productorId order by u.fecha desc, u.id desc")
    List<ProductorUbicacion> findByProductor_IdOrderByFechaDesc(@Param("productorId") Long productorId);

    /**
     * Ultima ubicacion conocida de varios proveedores de una vez (el mapa de la ruta). Ordenado
     * tambien por id: la fecha es un LocalDate y varios puntos del mismo proveedor caen el mismo
     * dia, asi que sin desempate "la ultima" seria arbitraria.
     */
    @Query("select u from ProductorUbicacion u join fetch u.productor"
            + " where u.productor.id in :productorIds order by u.fecha desc, u.id desc")
    List<ProductorUbicacion> findByProductor_IdInOrderByFechaDescIdDesc(
            @Param("productorIds") List<Long> productorIds);
}
