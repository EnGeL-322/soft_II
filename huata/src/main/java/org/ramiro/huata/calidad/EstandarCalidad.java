package org.ramiro.huata.calidad;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * La regla de calidad ESTANDAR que decide si un analisis se aprueba solo (cliente, 2026-10-14).
 *
 * <p>No define umbrales propios: usa los que ya existen.
 * <ul>
 *   <li>El resultado de la regla 7.1 (DESARROLLO.md, {@link DecisionRecepcionService}) debe ser
 *       ACEPTADA: agua anadida, Lugol y banda de densidad.
 *   <li>Cada valor medido debe estar dentro de su rango en {@code parametros_calidad}, la tabla que
 *       se edita en "Precios y calidad" (densidad, punto de congelacion, acidez, grasa, solidos).
 * </ul>
 *
 * <p>Un valor que no se midio no cuenta como falla: es el mismo criterio que la regla 7.1 aplica a
 * la densidad 0. Un parametro configurado que no corresponde a ningun campo del analisis se ignora.
 *
 * <p>No cambia el resultado ni el precio: solo decide si la aprobacion es automatica o si el
 * analisis queda pendiente con una advertencia para que una persona lo apruebe en la app.
 */
public final class EstandarCalidad {

    private EstandarCalidad() {}

    /** Campos del analisis que se comparan, por nombre normalizado del parametro. */
    private static final Map<String, Function<ControlCalidad, Double>> CAMPOS = Map.of(
            "densidad", ControlCalidad::getDensidad,
            "punto de congelacion", ControlCalidad::getPuntoCongelacion,
            "acidez", ControlCalidad::getAcidez,
            "grasa", ControlCalidad::getGrasa,
            "temperatura", ControlCalidad::getTemperatura,
            "solidos no grasos", ControlCalidad::getSolidosNoGrasos,
            "solidos totales", ControlCalidad::getSolidosTotales);

    /** Lo que no cumple el estandar, en lenguaje de planta. Vacia = pasa y se aprueba solo. */
    public static List<String> fallas(ControlCalidad control, List<ParametroCalidad> parametros) {
        List<String> fallas = new ArrayList<>();
        if (control.getResultado() != null && control.getResultado() != ResultadoCalidad.ACEPTADA) {
            String resultado = control.getResultado() == ResultadoCalidad.OBSERVADA ? "observada" : "rechazada";
            fallas.add("Resultado " + resultado
                    + (control.getMotivoRechazo() != null ? " (" + control.getMotivoRechazo() + ")" : ""));
        }
        for (ParametroCalidad p : parametros) {
            if (!p.isActivo() || p.getNombre() == null) continue;
            Function<ControlCalidad, Double> campo = CAMPOS.get(normalizar(p.getNombre()));
            if (campo == null) continue;
            Double valor = campo.apply(control);
            if (valor == null || valor == 0) continue;
            String unidad = p.getUnidad() == null ? "" : " " + p.getUnidad();
            if (p.getValorMin() != null && valor < p.getValorMin()) {
                fallas.add(p.getNombre() + " " + formato(valor) + " bajo el mínimo " + formato(p.getValorMin()) + unidad);
            } else if (p.getValorMax() != null && valor > p.getValorMax()) {
                fallas.add(p.getNombre() + " " + formato(valor) + " sobre el máximo " + formato(p.getValorMax()) + unidad);
            }
        }
        return fallas;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private static String formato(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
