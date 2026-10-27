package org.ramiro.huata.common;

/**
 * Reglas de forma de los datos personales: DNI y nombres. Un solo sitio, porque las usan proveedores,
 * acopiadores y cualquier actor que se registre despues; duplicar la expresion regular en cada DTO es
 * garantizar que un dia dos pantallas acepten cosas distintas.
 *
 * <p><b>El DNI son 8 digitos exactos</b>, que es el formato del DNI peruano (RENIEC). No 7: con 7 no
 * entraria ningun documento real y no se podria registrar a nadie.
 *
 * <p><b>Un nombre no lleva numeros.</b> Se aceptan letras (con tildes y ñ), espacios, apostrofo y
 * guion — porque apellidos como "D'Angelo" o "Quispe-Mamani" existen — y nada mas. Sin esto entraba
 * cualquier cosa: en la base de desarrollo hay proveedores llamados "r" y "12".
 */
public final class DatosPersonales {

    private DatosPersonales() {}

    /** DNI: exactamente 8 digitos, sin espacios ni guiones. */
    public static final String DNI_REGEX = "^\\d{8}$";

    public static final String DNI_MENSAJE = "El DNI debe tener exactamente 8 dígitos numéricos.";

    /**
     * Nombres y apellidos: letras, espacios, apostrofo y guion. Minimo dos caracteres, porque una sola
     * letra no es un nombre sino un dato de prueba que se quedo.
     */
    public static final String NOMBRE_REGEX = "^[\\p{L}][\\p{L} .'\\-]{1,}$";

    public static final String NOMBRE_MENSAJE =
            "Solo letras, espacios, apóstrofo y guion; sin números ni símbolos, y al menos dos caracteres.";

    /**
     * Telefono: 6 a 15 digitos, admitiendo espacios, guiones, parentesis y un "+" inicial. No se fija
     * en 9 (el movil peruano) porque en la zona tambien se anotan fijos y numeros de recado, y una
     * regla mas estricta dejaria fuera contactos que hoy sirven.
     *
     * <p>Cuenta <b>digitos</b>, no caracteres: cada repeticion es un digito con su separador opcional.
     * Escrito como un rango de longitud total, "12345" colaba — cinco digitos parecen seis cuando se
     * mide el largo del texto en vez de los numeros que contiene.
     */
    public static final String TELEFONO_REGEX = "^\\+?(?:\\d[ .()\\-]?){6,15}$";

    /**
     * El telefono es opcional: el formulario envia "" cuando se deja vacio, y {@code @Pattern} solo
     * deja pasar null. Sin esta variante, dejarlo vacio devolvia "Telefono invalido".
     */
    public static final String TELEFONO_OPCIONAL_REGEX = "^$|" + TELEFONO_REGEX;

    public static final String TELEFONO_MENSAJE = "Teléfono inválido: use entre 6 y 15 dígitos.";

    /** Cuantos digitos finales del DNI se dejan ver al enmascararlo. */
    private static final int DIGITOS_VISIBLES = 4;

    /**
     * Enmascara un documento para publicarlo sin identificar a la persona: {@code ****4040}.
     *
     * <p>Hace falta desde que el codigo del proveedor <b>es</b> su DNI: el panel publico publica ese
     * codigo cuando hay una incidencia de calidad (RF-49), y la seccion 30 y RNF-12 prohiben exponer
     * el DNI ahi. Sin esto, activar "codigo = DNI" convertia esa pantalla en una lista de documentos.
     */
    public static String enmascarar(String documento) {
        if (documento == null || documento.isBlank()) {
            return documento;
        }
        String limpio = documento.trim();
        if (limpio.length() <= DIGITOS_VISIBLES) {
            // Demasiado corto para enmascarar sin dejarlo reconocible: se oculta entero.
            return "*".repeat(limpio.length());
        }
        return "*".repeat(limpio.length() - DIGITOS_VISIBLES) + limpio.substring(limpio.length() - DIGITOS_VISIBLES);
    }

    /** True si el texto cumple la forma de un DNI. Para validar en servicio, no solo por anotacion. */
    public static boolean esDniValido(String documento) {
        return documento != null && documento.trim().matches(DNI_REGEX);
    }
}
