package org.ramiro.huata.common;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Largo maximo del texto de busqueda: nadie busca un proveedor con mas de esto. */
    static final int LARGO_MAXIMO_BUSQUEDA = 100;

    private static final char ESCAPE = '\\';

    /**
     * Coincidencia parcial, insensible a mayusculas, en cualquiera de los campos de texto dados.
     *
     * <p>El texto viaja como parametro enlazado (Criteria API), asi que no hay inyeccion SQL. Lo que
     * si faltaba es escapar los comodines de LIKE: buscar "%" o "_" devolvia el padron entero, y
     * "a_b" coincidia con cualquier letra en medio. Ahora el texto se busca literal. Los nombres de
     * campo nunca vienen del cliente: los fija cada servicio.
     */
    public static <T> Specification<T> textSearch(String query, String... fields) {
        if (query == null || query.isBlank()) {
            return alwaysTrue();
        }
        String texto = query.trim().toLowerCase();
        if (texto.length() > LARGO_MAXIMO_BUSQUEDA) {
            texto = texto.substring(0, LARGO_MAXIMO_BUSQUEDA);
        }
        String like = "%" + escaparLike(texto) + "%";
        List<String> fieldList = Arrays.asList(fields);
        return (root, cq, cb) -> cb.or(
                fieldList.stream()
                        .map(field -> cb.like(cb.lower(root.get(field)), like, ESCAPE))
                        .toArray(Predicate[]::new));
    }

    private static final String CON_TILDE = "áéíóúüñ";
    private static final String SIN_TILDE = "aeiouun";
    private static final int MAXIMO_PALABRAS = 8;

    /**
     * Buscador por palabras: cada palabra tiene que aparecer en alguno de los campos, en cualquier
     * orden, sin distinguir mayusculas ni tildes. Asi "proveedor demo e" encuentra a nombres
     * "Proveedor" + apellidos "Demo E", y "collana 951" al de esa zona con ese telefono. Con
     * {@link #textSearch} la frase entera tenia que estar en un solo campo y casi nunca encontraba.
     *
     * <p>Los campos pueden ser anidados ("usuario.username", "ruta.nombre"): se unen con LEFT JOIN para
     * no perder a quien no tiene cuenta o ruta. Los comodines de LIKE se escapan igual que en textSearch.
     */
    public static <T> Specification<T> buscarPalabras(String query, String... campos) {
        if (query == null || query.isBlank()) {
            return alwaysTrue();
        }
        String texto = query.trim().toLowerCase();
        if (texto.length() > LARGO_MAXIMO_BUSQUEDA) {
            texto = texto.substring(0, LARGO_MAXIMO_BUSQUEDA);
        }
        List<String> palabras = Arrays.stream(sinTildes(texto).split("\\s+"))
                .filter(p -> !p.isBlank())
                .limit(MAXIMO_PALABRAS)
                .toList();
        return (root, cq, cb) -> {
            Map<String, From<?, ?>> uniones = new HashMap<>();
            List<Expression<String>> normalizados = Arrays.stream(campos)
                    .map(campo -> {
                        Path<String> ruta;
                        int punto = campo.indexOf('.');
                        if (punto < 0) {
                            ruta = root.get(campo);
                        } else {
                            From<?, ?> union = uniones.computeIfAbsent(
                                    campo.substring(0, punto), r -> root.join(r, JoinType.LEFT));
                            ruta = union.get(campo.substring(punto + 1));
                        }
                        return cb.function("translate", String.class,
                                cb.lower(ruta.as(String.class)), cb.literal(CON_TILDE), cb.literal(SIN_TILDE));
                    })
                    .toList();
            return cb.and(palabras.stream()
                    .map(palabra -> {
                        String limpia = escaparLike(palabra);
                        // Una letra suelta ("Demo E") esta en casi todo: solo cuenta como inicio de palabra.
                        boolean inicial = palabra.length() == 1;
                        return cb.or(normalizados.stream()
                                .map(campo -> inicial
                                        ? cb.or(cb.like(campo, limpia + "%", ESCAPE), cb.like(campo, "% " + limpia + "%", ESCAPE))
                                        : cb.like(campo, "%" + limpia + "%", ESCAPE))
                                .toArray(Predicate[]::new));
                    })
                    .toArray(Predicate[]::new));
        };
    }

    static String sinTildes(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            int i = CON_TILDE.indexOf(c);
            sb.append(i < 0 ? c : SIN_TILDE.charAt(i));
        }
        return sb.toString();
    }

    /** Escapa los comodines de LIKE para que el texto del usuario se busque tal cual. */
    static String escaparLike(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
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

    /** Igualdad sobre un atributo anidado, p. ej. ("ruta", "id"). Sin valor no filtra. */
    public static <T> Specification<T> equalsPathIfPresent(Object value, String... path) {
        if (value == null) {
            return alwaysTrue();
        }
        return (root, cq, cb) -> cb.equal(navegar(root, path), value);
    }

    /**
     * Igualdad de texto sin distinguir mayusculas ni espacios de los extremos, sobre un atributo
     * anidado. Sirve para la zona, que es texto libre en la ruta (decision C-05).
     */
    public static <T> Specification<T> equalsIgnoreCaseIfPresent(String value, String... path) {
        if (value == null || value.isBlank()) {
            return alwaysTrue();
        }
        String buscado = value.trim().toLowerCase();
        return (root, cq, cb) -> cb.equal(cb.lower(cb.trim(navegar(root, path).as(String.class))), buscado);
    }

    private static Path<Object> navegar(Path<?> root, String... path) {
        Path<Object> actual = root.get(path[0]);
        for (int i = 1; i < path.length; i++) {
            actual = actual.get(path[i]);
        }
        return actual;
    }
}
