package org.ramiro.huata.productores;

/**
 * Da de alta un proveedor desde pruebas de otros paquetes: ProductorRequest es un record de paquete.
 * Sin DNI, que es opcional.
 */
public final class ProductorRequestDePrueba {

    private ProductorRequestDePrueba() {}

    public static Productor crear(ProductorService service, String nombres, String apellidos, String telefono,
            Long rutaId, String username) {
        return service.crear(new ProductorRequest(null, nombres, apellidos, telefono, rutaId, null, null, null, null,
                username, "Huata2026*"));
    }
}
