package org.ramiro.huata.almacen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * El almacen de vouchers contra un servidor S3 de verdad (Testcontainers), no contra un doble: lo que
 * se quiere probar es justo la integracion (credenciales, creacion del bucket, subida y lectura).
 *
 * <p>Se usa RustFS y no MinIO porque la imagen publica {@code minio/minio} dejo de estar disponible
 * en Docker Hub. El cliente es el SDK de MinIO, que habla S3 estandar: el mismo codigo sirve contra
 * AWS S3, Cloudflare R2, SeaweedFS o un MinIO con licencia.
 */
@Testcontainers
class MinioAlmacenIT {

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>("rustfs/rustfs:1.0.0")
            .withEnv("RUSTFS_ACCESS_KEY", "huata-test")
            .withEnv("RUSTFS_SECRET_KEY", "huata-test-secreto")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    private MinioAlmacen almacen(String bucket) {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        return new MinioAlmacen(endpoint, "huata-test", "huata-test-secreto", bucket);
    }

    @Test
    void creaElBucketEnElPrimerUsoYGuardaYLeeLaFoto() {
        var almacen = almacen("huata-evidencias");
        byte[] foto = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};

        almacen.guardar("vouchers/2026/09/control-1/a.jpg", foto, "image/jpeg");

        assertThat(almacen.leer("vouchers/2026/09/control-1/a.jpg")).isEqualTo(foto);
    }

    @Test
    void unaClaveInexistenteEsNoEncontradoYNoUnErrorDeAlmacen() {
        var almacen = almacen("huata-evidencias-2");
        almacen.guardar("x.jpg", new byte[] {1}, "image/jpeg");

        assertThatThrownBy(() -> almacen.leer("no-existe.jpg"))
                .isInstanceOf(AlmacenArchivos.ArchivoNoEncontradoException.class);
    }

    @Test
    void credencialesEquivocadasSonAlmacenNoDisponible() {
        String endpoint = "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
        var almacen = new MinioAlmacen(endpoint, "huata-test", "clave-incorrecta", "otro");

        assertThatThrownBy(() -> almacen.guardar("x.jpg", new byte[] {1}, "image/jpeg"))
                .isInstanceOf(AlmacenArchivos.AlmacenNoDisponibleException.class);
    }
}
