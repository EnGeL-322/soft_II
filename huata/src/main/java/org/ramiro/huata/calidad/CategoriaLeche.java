package org.ramiro.huata.calidad;

/**
 * Categoria de la leche segun su densidad corregida a 15 C (DESARROLLO.md regla 7.5).
 *
 * <p>Los umbrales salen de tabla_correccion_densidad_15C.csv, el Excel que usa la planta. Ese
 * archivo tiene 200 filas (lecturas 25-34 x temperaturas 15-34 C), pero no es una lista arbitraria:
 * la categoria depende UNICAMENTE de la densidad corregida, sin ambiguedad en ninguna fila. Por eso
 * aqui hay umbrales y no una tabla cargada en memoria.
 *
 * <p>Eso importa en la practica: los tickets reales del analizador caen fuera del rango tabulado
 * (W1.jpeg mide 12.2 C y la tabla empieza en 15 C; W.jpeg da una lectura de 23.9 y la tabla empieza
 * en 25). Con la tabla como lista, esas muestras no se podrian clasificar; con la regla, si.
 */
public enum CategoriaLeche {
    CON_AGUA("CON AGUA"),
    MALO("MALO"),
    REGULAR("REGULAR"),
    BUENO("BUENO"),
    MUY_BUENO("MUY BUENO");

    private final String etiqueta;

    CategoriaLeche(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /**
     * Clasifica por densidad ya corregida a 15 C.
     *
     * <p>Se compara con un margen de media milesima porque los limites de la tabla son exactos
     * (1.0280, 1.0300, ...) y en coma flotante un 1.0299999999 caeria en la categoria de abajo.
     */
    public static CategoriaLeche de(double densidadCorregida) {
        double d = densidadCorregida + 0.00005;
        if (d < 1.0280) return CON_AGUA;
        if (d < 1.0300) return MALO;
        if (d < 1.0310) return REGULAR;
        if (d < 1.0320) return BUENO;
        return MUY_BUENO;
    }
}
