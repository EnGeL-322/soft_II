package org.ramiro.huata.common;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Regla de nombres de datos maestros (productos e insumos), pedida por el cliente el 2026-10-27:
 * no puede haber dos nombres casi iguales. "yogurd" junto a "yogurt" o "zal" junto a "sal" son un
 * error de tipeo que parte en dos el stock, las recetas y los precios de una misma cosa.
 *
 * <ul>
 *   <li>Iguales: se comparan sin mayusculas, tildes ni espacios de mas ("Azúcar" = "azucar ").
 *   <li>Parecidos: a una letra de distancia (dos si el nombre tiene 8 letras o mas), contando
 *       cambiar, poner o quitar una letra. Nombres de menos de 3 letras no se comparan.
 *   <li>Si los numeros de los dos nombres son distintos, no son parecidos: "Envase 1 L" y
 *       "Envase 5 L" son productos distintos de verdad.
 * </ul>
 *
 * <p>La misma regla esta en la web ({@code nombres-parecidos.ts}) para avisar mientras se escribe;
 * esta es la que manda.
 */
public final class NombresParecidos {

    private NombresParecidos() {}

    /** Nombre como se guarda: sin espacios sobrantes. */
    public static String limpiar(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }

    /** Nombre como se compara: sin tildes, mayusculas ni espacios de mas. */
    public static String clave(String nombre) {
        return Normalizer.normalize(limpiar(nombre), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public static boolean iguales(String a, String b) {
        return clave(a).equals(clave(b));
    }

    /** Casi iguales, pero no iguales. */
    public static boolean parecidos(String a, String b) {
        String x = clave(a);
        String y = clave(b);
        if (x.equals(y) || Math.min(x.length(), y.length()) < 3) {
            return false;
        }
        if (!x.replaceAll("\\D", "").equals(y.replaceAll("\\D", ""))) {
            return false;
        }
        int tolerancia = Math.max(x.length(), y.length()) >= 8 ? 2 : 1;
        return Math.abs(x.length() - y.length()) <= tolerancia && distancia(x, y) <= tolerancia;
    }

    /** El primer candidato igual o parecido a {@code nombre}, si lo hay. */
    public static <T> Optional<T> buscarChoque(String nombre, Collection<T> candidatos, Function<T, String> nombreDe) {
        return candidatos.stream()
                .filter(c -> iguales(nombre, nombreDe.apply(c)) || parecidos(nombre, nombreDe.apply(c)))
                // Primero el igual exacto, para que el mensaje hable del caso mas claro.
                .min((a, b) -> Boolean.compare(!iguales(nombre, nombreDe.apply(a)), !iguales(nombre, nombreDe.apply(b))));
    }

    /** Cuantas letras hay que cambiar, poner o quitar para pasar de una a otra (Levenshtein). */
    static int distancia(String a, String b) {
        int[] fila = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            fila[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = fila[0];
            fila[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int arriba = fila[j];
                fila[j] = Math.min(Math.min(fila[j] + 1, fila[j - 1] + 1), diagonal + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
                diagonal = arriba;
            }
        }
        return fila[b.length()];
    }
}
