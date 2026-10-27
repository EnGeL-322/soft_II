package org.ramiro.huata.ivr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * El campo {@code texto} de cada respuesta: la frase en espanol sencillo que un canal con voz
 * sintetica (RapidPro / Twilio) lee tal cual. Asterisk no la usa: arma la frase con los campos
 * estructurados y audios pregrabados.
 *
 * <p>Los numeros van en cifras ("42 litros") porque los motores de voz los leen bien en espanol; lo que
 * se cuida aqui es lo que un motor no adivina: medio litro, singular, centimos, el dia de la semana.
 */
final class FrasesIvr {
    private FrasesIvr() {}

    private static final String[] DIAS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /**
     * Litros redondeados al medio litro mas cercano: nadie en una llamada necesita "42,3". El valor
     * exacto viaja aparte en el campo {@code litros}.
     */
    static String litros(double litros) {
        double medios = Math.round(litros * 2) / 2.0;
        long enteros = (long) Math.floor(medios);
        boolean medio = medios - enteros >= 0.5;
        if (enteros == 0) return medio ? "medio litro" : "0 litros";
        String base = enteros + (enteros == 1 ? " litro" : " litros");
        return medio ? base + " y medio" : base;
    }

    /** "502 soles con 35 céntimos", "1 sol", "50 céntimos". */
    static String soles(double monto) {
        BigDecimal valor = BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP);
        long soles = valor.longValue();
        int centimos = valor.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String parteCentimos = centimos + (centimos == 1 ? " céntimo" : " céntimos");
        if (soles == 0) return centimos == 0 ? "0 soles" : parteCentimos;
        String parteSoles = soles + (soles == 1 ? " sol" : " soles");
        return centimos == 0 ? parteSoles : parteSoles + " con " + parteCentimos;
    }

    /** "el viernes 9 de octubre". */
    static String fecha(LocalDate fecha) {
        return "el " + DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth() + " de "
                + MESES[fecha.getMonthValue() - 1];
    }
}
