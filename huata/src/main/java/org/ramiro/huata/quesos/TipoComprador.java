package org.ramiro.huata.quesos;

/**
 * A quien se le vende. Decide el precio de la unidad, y es el unico dato del formulario que lo
 * decide: el precio en si no viaja en la peticion.
 *
 * <p>Es el mismo criterio que ya rige la leche (commit "la calidad y el precio los decide la regla,
 * no quien registra"): si el precio lo escribiera quien vende, la lista de precios de la cooperativa
 * seria una sugerencia y cada nota de venta podria llevar el suyo.
 *
 * <p>PROVEEDOR es el propio proveedor de leche comprando queso. Hoy solo es un precio preferente; no
 * descuenta todavia de su liquidacion. Cuando se quiera cerrar ese circulo, el ajuste que ya existe
 * para eso es {@code TipoAjuste.DESCUENTO_QUESO}.
 */
public enum TipoComprador {
    PUBLICO_GENERAL,
    MAYORISTA,
    PROVEEDOR;

    /** Precio por unidad que le corresponde a este comprador en la tarifa dada. */
    public double precioEn(TarifaQueso tarifa) {
        return switch (this) {
            case PUBLICO_GENERAL -> tarifa.getPrecioPublicoGeneral();
            case MAYORISTA -> tarifa.getPrecioMayorista();
            case PROVEEDOR -> tarifa.getPrecioProveedor();
        };
    }
}
