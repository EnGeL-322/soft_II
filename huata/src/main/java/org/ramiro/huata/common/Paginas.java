package org.ramiro.huata.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Paginacion que llega del cliente, saneada en un solo sitio.
 *
 * <p>Dos problemas que tenian los listados:
 * <ul>
 *   <li><b>Tamano sin techo.</b> {@code ?size=100000000} obligaba a cargar la tabla entera en
 *       memoria (RNF-05): con recojos y GPS creciendo sin fin, una sola peticion podia tumbar el
 *       backend. Y un {@code size} o {@code page} negativo hacia que {@code PageRequest.of} lanzara
 *       una excepcion que el cliente veia como error.
 *   <li><b>Orden elegido por el cliente.</b> Los endpoints que reciben un {@link Pageable} de
 *       Spring aceptaban {@code ?sort=cualquierCosa}. No es inyeccion SQL (Spring Data valida la
 *       propiedad contra la entidad), pero una propiedad inexistente respondia 500 y una anidada
 *       podia forzar joins que nadie planeo. El orden de esos listados ya lo fija la consulta
 *       ({@code ...OrderByFechaDesc...}), asi que se descarta el del cliente.
 * </ul>
 */
public final class Paginas {

    /** Techo de filas por pagina. La app movil pide hasta 500 (ruta del dia, historial). */
    public static final int TAMANO_MAXIMO = 500;

    private Paginas() {}

    public static PageRequest de(int page, int size) {
        return PageRequest.of(Math.max(page, 0), acotar(size));
    }

    public static PageRequest de(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), acotar(size), sort);
    }

    /** Conserva pagina y tamano (acotado) del cliente y descarta el orden que haya pedido. */
    public static Pageable sinOrdenDelCliente(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20);
        }
        return de(pageable.getPageNumber(), pageable.getPageSize());
    }

    static int acotar(int size) {
        if (size < 1) {
            return 1;
        }
        return Math.min(size, TAMANO_MAXIMO);
    }
}
