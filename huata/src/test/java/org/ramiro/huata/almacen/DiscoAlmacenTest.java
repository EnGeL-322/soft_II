package org.ramiro.huata.almacen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoAlmacenTest {

    @TempDir Path raiz;

    @Test
    void guardaYLeeLoMismo() {
        var almacen = new DiscoAlmacen(raiz);
        almacen.guardar("vouchers/2026/09/a.jpg", new byte[] {1, 2, 3}, "image/jpeg");
        assertThat(almacen.leer("vouchers/2026/09/a.jpg")).containsExactly(1, 2, 3);
    }

    @Test
    void leerAlgoQueNoExisteEsNoEncontrado() {
        assertThatThrownBy(() -> new DiscoAlmacen(raiz).leer("no/existe.jpg"))
                .isInstanceOf(AlmacenArchivos.ArchivoNoEncontradoException.class);
    }

    /** Una clave con "../" nunca puede leer ni escribir fuera del directorio del almacen. */
    @Test
    void noSeSaleDelDirectorioRaiz() {
        var almacen = new DiscoAlmacen(raiz.resolve("evidencias"));
        assertThatThrownBy(() -> almacen.leer("../../etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> almacen.guardar("../fuera.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
