package org.ramiro.huata.common;

import jakarta.persistence.criteria.Predicate;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Helpers para armar el "buscador + filtros" que pide el UI/UX de Huata en todas las tablas
 * largas (productores, recojos, calidad, liquidaciones, usuarios), sin repetir criteria queries
 * en cada modulo.
 */
public final class SearchSpecifications {

    private SearchSpecifications() {}

    /**
     * Especificacion neutra ("sin filtro"): sirve como punto de partida y como reemplazo seguro de
     * null. Esta version de Spring Data JPA exige que toda Specification encadenada con
     * and()/or() sea siempre no-nula (a diferencia de versiones antiguas, mas permisivas).
     */
    public static <T> Specification<T> alwaysTrue() {
        return (root, cq, cb) -> cb.conjunction();
    }

    /** Coincidencia parcial, insensible a mayusculas, en cualquiera de los campos de texto dados. */
    public static <T> Specification<T> textSearch(String query, String... fields) {
        if (query == null || query.isBlank()) {
            return alwaysTrue();
        }
        String like = "%" + query.trim().toLowerCase() + "%";
        List<String> fieldList = Arrays.asList(fields);
        return (root, cq, cb) -> cb.or(
                fieldList.stream()
                        .map(field -> cb.like(cb.lower(root.get(field)), like))
                        .toArray(Predicate[]::new));
    }

    /** Solo registros no inactivados (BaseEntity.activo). */
    public static <T> Specification<T> onlyActive() {
        return (root, cq, cb) -> cb.isTrue(root.get("activo"));
    }

    public static <T> Specification<T> equalsIfPresent(String field, Object value) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, cq, cb) -> cb.equal(root.get(field), value);
    }
}
