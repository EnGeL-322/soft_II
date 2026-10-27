package org.ramiro.huata.calidad;

import org.springframework.stereotype.Service;

/**
 * Correccion de la densidad a 15 C y clasificacion de la leche (DESARROLLO.md regla 7.5).
 *
 * <p>Es logica pura, sin base de datos: la misma entrada da siempre la misma salida, y por eso se
 * cubre con las 200 filas del Excel de la planta como prueba parametrizada.
 */
@Service
public class CorreccionDensidadService {

    /** Temperatura de referencia del lactodensimetro. */
    private static final double TEMPERATURA_REFERENCIA = 15.0;

    /** Correccion estandar por grado de desviacion respecto de los 15 C. */
    private static final double CORRECCION_POR_GRADO = 0.0002;

    /**
     * Convierte la lectura del lactodensimetro a densidad real a 15 C.
     *
     * <p>La lectura son GRADOS (31.4 en los tickets), no g/mL: primero se lleva a densidad
     * (1 + grados/1000) y despues se corrige por temperatura. Por encima de 15 C la leche se dilata
     * y el aparato lee de menos, asi que la correccion suma; por debajo, resta.
     */
    public double corregirA15C(double lecturaLactodensimetro, double temperaturaC) {
        double densidadALaLectura = 1 + lecturaLactodensimetro / 1000.0;
        return densidadALaLectura + CORRECCION_POR_GRADO * (temperaturaC - TEMPERATURA_REFERENCIA);
    }

    /** Categoria de la muestra a partir de la lectura y la temperatura del analizador. */
    public CategoriaLeche clasificar(double lecturaLactodensimetro, double temperaturaC) {
        return CategoriaLeche.de(corregirA15C(lecturaLactodensimetro, temperaturaC));
    }

    /**
     * Traduce la categoria al resultado de la recepcion.
     *
     * <p>PENDIENTE DE CONFIRMAR CON HUATA: el reparto es el que sugieren los nombres de la tabla.
     * "CON AGUA" es adulteracion y "MALO" no llega al minimo, asi que se rechazan; "REGULAR" pasa
     * pero con tarifa reducida; "BUENO" y "MUY BUENO" son aptas. Si la planta paga la REGULAR como
     * apta, o acepta la MALO observada, solo cambia este metodo.
     */
    public ResultadoCalidad resultadoDe(CategoriaLeche categoria) {
        return switch (categoria) {
            case CON_AGUA, MALO -> ResultadoCalidad.RECHAZADA;
            case REGULAR -> ResultadoCalidad.OBSERVADA;
            case BUENO, MUY_BUENO -> ResultadoCalidad.ACEPTADA;
        };
    }
}
