package org.ramiro.huata.almacen;

/**
 * Donde viven los archivos de evidencia (fotos de voucher). La base solo guarda la clave y los
 * metadatos (RNF-13, seccion 24): guardar las imagenes dentro de PostgreSQL haria crecer la base y
 * sus respaldos sin necesidad.
 *
 * <p>Dos implementaciones, elegidas por configuracion en {@link AlmacenConfig}: MinIO/S3 para
 * despliegues, y disco local para desarrollo y pruebas.
 */
public interface AlmacenArchivos {

    void guardar(String clave, byte[] contenido, String tipoContenido);

    /** @throws ArchivoNoEncontradoException si la clave no existe. */
    byte[] leer(String clave);

    /** Nombre corto para logs y para el endpoint de salud. */
    String descripcion();

    class ArchivoNoEncontradoException extends RuntimeException {
        public ArchivoNoEncontradoException(String clave) {
            super("Archivo no encontrado: " + clave);
        }
    }

    /** El almacen no respondio (MinIO caido, disco lleno): el cliente puede reintentar. */
    class AlmacenNoDisponibleException extends RuntimeException {
        public AlmacenNoDisponibleException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
