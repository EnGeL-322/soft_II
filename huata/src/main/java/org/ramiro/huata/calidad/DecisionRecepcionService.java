package org.ramiro.huata.calidad;

import org.springframework.stereotype.Service;

/**
 * Decide calidad, aceptacion y precio de una recepcion (DESARROLLO.md regla 7.1).
 *
 * <p>Es logica pura y deliberadamente sin repositorios: recibe todo lo que necesita y devuelve la
 * decision. Por eso se cubre al 100% con pruebas parametrizadas y por eso la misma entrada da
 * siempre la misma salida, sin importar quien la llame ni desde donde.
 *
 * <p><b>Quien decide es el sistema, no el operador.</b> Hasta aqui el resultado llegaba en la
 * peticion ({@code ControlCalidadRequest.resultado}) y se guardaba tal cual: quien registraba la
 * muestra podia declarar apta una leche adulterada. Este servicio es el que fija el resultado.
 */
@Service
public class DecisionRecepcionService {

    /**
     * Parametros del negocio que entran en la decision. Se pasan como dato y no se leen aqui de la
     * base para que el calculo siga siendo puro y probable sin levantar un contexto de Spring.
     *
     * @param densidadMin limite inferior de densidad corregida a 15 C, inclusive
     * @param densidadMax limite superior, inclusive
     * @param precioNormal S/ por litro de una recepcion apta
     * @param precioReducido S/ por litro de una recepcion de baja calidad
     * @param precioExcelencia S/ por litro de un proveedor Excelencia; {@code null} si la tarifa
     *     vigente es anterior a la regla 7.4, en cuyo caso se cobra el normal
     */
    public record ParametrosDecision(
            double densidadMin, double densidadMax, double precioNormal, double precioReducido, Double precioExcelencia) {}

    /**
     * Lo que se observo de la muestra.
     *
     * @param densidad densidad ya corregida a 15 C (ver {@link CorreccionDensidadService}). Un 0
     *     significa "no se midio", no "densidad cero": no debe disparar baja calidad.
     * @param calidadDeclarada lo que el operador cree; se tiene en cuenta para empeorar la decision,
     *     nunca para mejorarla
     */
    public record Muestra(
            AguaAnadida agua, ResultadoLugol lugol, ResultadoCalidad calidadDeclarada, double densidad) {}

    /**
     * Decision tomada.
     *
     * @param precioPorLitro 0 cuando la recepcion se rechaza: no se paga leche que no se recibe
     */
    public record Decision(ResultadoCalidad resultado, boolean aceptada, double precioPorLitro, String motivo) {}

    /**
     * Aplica las tres reglas de 7.1, en orden. El orden importa: la regla 1 (adulterada) tiene
     * prioridad sobre la 2 (baja calidad), de modo que una muestra que dispara ambas se rechaza.
     */
    public Decision decidir(Muestra muestra, boolean proveedorExcelencia, ParametrosDecision parametros) {
        if (esAdulterada(muestra)) {
            return new Decision(ResultadoCalidad.RECHAZADA, false, 0.0, motivoAdulteracion(muestra));
        }
        if (esBajaCalidad(muestra, parametros)) {
            return new Decision(
                    ResultadoCalidad.OBSERVADA, true, parametros.precioReducido(), motivoBajaCalidad(muestra, parametros));
        }
        double precio = proveedorExcelencia && parametros.precioExcelencia() != null
                ? parametros.precioExcelencia()
                : parametros.precioNormal();
        String motivo = proveedorExcelencia && parametros.precioExcelencia() != null
                ? "Apta · tarifa Excelencia"
                : "Apta";
        return new Decision(ResultadoCalidad.ACEPTADA, true, precio, motivo);
    }

    /** Adulteracion: agua confirmada, Lugol positivo o el propio operador la declara adulterada. */
    private boolean esAdulterada(Muestra m) {
        return m.agua() == AguaAnadida.CONFIRMADA
                || m.lugol() == ResultadoLugol.POSITIVA
                || m.calidadDeclarada() == ResultadoCalidad.RECHAZADA;
    }

    /**
     * Baja calidad: sospecha de agua, el operador la declara observada, o la densidad corregida cae
     * fuera de la banda.
     *
     * <p>La comparacion es estricta contra los limites: una densidad exactamente igual a
     * {@code densidadMin} o a {@code densidadMax} es APTA. Los limites pertenecen a la banda, y
     * tratarlos como fuera penalizaria al proveedor por redondeo.
     *
     * <p>Densidad 0 = no medida. Sin este resguardo, toda recepcion sin densidad —las de ruta, que
     * se miden despues en planta— caeria en baja calidad por comparar 0 contra 1.0296.
     */
    private boolean esBajaCalidad(Muestra m, ParametrosDecision p) {
        boolean densidadFueraDeBanda =
                m.densidad() > 0 && (m.densidad() < p.densidadMin() || m.densidad() > p.densidadMax());
        return m.agua() == AguaAnadida.SOSPECHA
                || m.calidadDeclarada() == ResultadoCalidad.OBSERVADA
                || densidadFueraDeBanda;
    }

    private String motivoAdulteracion(Muestra m) {
        if (m.agua() == AguaAnadida.CONFIRMADA) {
            return "Rechazada · agua anadida confirmada";
        }
        if (m.lugol() == ResultadoLugol.POSITIVA) {
            return "Rechazada · Lugol positivo (almidon)";
        }
        return "Rechazada · declarada adulterada en la recepcion";
    }

    private String motivoBajaCalidad(Muestra m, ParametrosDecision p) {
        if (m.agua() == AguaAnadida.SOSPECHA) {
            return "Observada · sospecha de agua anadida";
        }
        if (m.densidad() > 0 && (m.densidad() < p.densidadMin() || m.densidad() > p.densidadMax())) {
            return "Observada · densidad " + m.densidad() + " fuera de " + p.densidadMin() + "-" + p.densidadMax();
        }
        return "Observada · declarada de baja calidad en la recepcion";
    }
}
