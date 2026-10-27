package org.ramiro.huata.appmovil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Qué versión de la app móvil está publicada y cuál es la mínima que se acepta.
 *
 * <p>La app se entrega como APK directo (sin Play Store). Los cambios de código Dart llegan solos
 * como parche de Shorebird; este endpoint cubre lo que un parche no puede cambiar (plugins nativos,
 * permisos de Android): la app lo consulta al abrir y, si hay versión nueva, ofrece descargar el APK
 * desde {@code urlDescarga}. Si la instalada es menor que {@code versionMinima}, la app bloquea el
 * uso hasta actualizar (p. ej. cuando cambia un contrato del backend que la versión vieja rompería).
 *
 * <p>Es público porque se consulta antes del login y solo dice números de versión y una URL de
 * descarga, nada de ninguna persona. Se configura por entorno, sin redesplegar código:
 * {@code HUATA_APP_VERSION_ACTUAL}, {@code HUATA_APP_VERSION_MINIMA}, {@code HUATA_APP_URL_DESCARGA},
 * {@code HUATA_APP_NOTAS}. Vacío = no hay nada que ofrecer.
 */
@RestController
@RequestMapping("/api/v1/public/app-movil")
public class VersionAppMovilController {

    private final VersionAppMovil version;

    public VersionAppMovilController(
            @Value("${huata.app-movil.version-actual:}") String versionActual,
            @Value("${huata.app-movil.version-minima:}") String versionMinima,
            @Value("${huata.app-movil.url-descarga:}") String urlDescarga,
            @Value("${huata.app-movil.notas:}") String notas) {
        this.version = new VersionAppMovil(
                vacioANulo(versionActual), vacioANulo(versionMinima), vacioANulo(urlDescarga), vacioANulo(notas));
    }

    @GetMapping("/version")
    public VersionAppMovil version() {
        return version;
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    /** Versiones en formato {@code 1.2.3}, como {@code version:} de pubspec.yaml sin el {@code +build}. */
    public record VersionAppMovil(String versionActual, String versionMinima, String urlDescarga, String notas) {}
}
