package org.ramiro.huata.ingesta;

import org.ramiro.huata.calidad.CategoriaLeche;
import org.ramiro.huata.calidad.ResultadoCalidad;

/**
 * Lo que devuelve "Llamar datos": el ticket del analizador mas lo que el sistema deduce de el.
 *
 * <p>La evaluacion se calcula EN EL SERVIDOR y no en el navegador. Duplicar la regla 7.5 en
 * TypeScript significaria que un cambio en los umbrales habria que hacerlo en dos sitios, y el dia
 * que se olvide uno la pantalla diria una cosa y el pago se calcularia con otra.
 *
 * @param evaluacion null si el ticket no trae densidad o temperatura: sin esos dos valores no se
 *     puede clasificar, y es mejor decirlo que inventar una categoria.
 */
public record LecturaEvaluada(LecturaInstrumento lectura, Evaluacion evaluacion) {

    /**
     * @param densidadCorregida densidad llevada a 15 C (regla 7.5)
     * @param categoria categoria del Excel de la planta
     * @param resultado que hacer con la entrega
     * @param precio tarifa que corresponde a ese resultado, o null si no hay tarifa configurada
     */
    public record Evaluacion(
            double densidadCorregida,
            CategoriaLeche categoria,
            String categoriaEtiqueta,
            ResultadoCalidad resultado,
            Double precio) {}
}
