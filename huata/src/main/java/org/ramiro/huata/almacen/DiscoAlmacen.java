package org.ramiro.huata.almacen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Archivos en un directorio del disco. Para desarrollo y pruebas: en un contenedor sin volumen los
 * archivos se pierden al recrearlo, por eso en docker-compose se usa MinIO.
 */
class DiscoAlmacen implements AlmacenArchivos {

    private final Path raiz;

    DiscoAlmacen(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoContenido) {
        Path destino = resolver(clave);
        try {
            Files.createDirectories(destino.getParent());
            // Escritura atomica: un corte a mitad no deja un archivo truncado con la clave definitiva.
            Path temporal = Files.createTempFile(destino.getParent(), ".subiendo-", ".tmp");
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new AlmacenNoDisponibleException("No se pudo guardar el archivo en disco", e);
        }
    }

    @Override
    public byte[] leer(String clave) {
        try {
            return Files.readAllBytes(resolver(clave));
        } catch (NoSuchFileException e) {
            throw new ArchivoNoEncontradoException(clave);
        } catch (IOException e) {
            throw new AlmacenNoDisponibleException("No se pudo leer el archivo del disco", e);
        }
    }

    @Override
    public String descripcion() {
        return "disco " + raiz;
    }

    /**
     * La clave la arma el servidor, nunca el cliente; aun asi se verifica que no escape del
     * directorio raiz ("../"), para que un error futuro no se convierta en lectura de archivos
     * arbitrarios del servidor (path traversal).
     */
    private Path resolver(String clave) {
        Path ruta = raiz.resolve(clave).normalize();
        if (!ruta.startsWith(raiz)) {
            throw new IllegalArgumentException("Clave de archivo invalida");
        }
        return ruta;
    }
}
