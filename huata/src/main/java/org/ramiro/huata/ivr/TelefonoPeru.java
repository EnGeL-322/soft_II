package org.ramiro.huata.ivr;

/**
 * El numero que entrega la red ("+51987654321", "51987654321", "987654321") y el que esta en la ficha
 * del proveedor ("987 654 321", "987-654-321") no tienen el mismo formato. Se comparan por los
 * ultimos 9 digitos, que es la longitud de un celular en Peru.
 */
final class TelefonoPeru {
    private TelefonoPeru() {}

    static final int DIGITOS = 9;

    /** Los ultimos 9 digitos, o {@code null} si no hay suficientes para ser un celular. */
    static String normalizar(String telefono) {
        if (telefono == null) return null;
        String digitos = telefono.replaceAll("\\D", "");
        return digitos.length() < DIGITOS ? null : digitos.substring(digitos.length() - DIGITOS);
    }
}
