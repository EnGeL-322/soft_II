package org.ramiro.huata.almacen;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;

/** Archivos en MinIO (o cualquier almacen compatible con S3). */
@Slf4j
class MinioAlmacen implements AlmacenArchivos {

    private final MinioClient cliente;
    private final String bucket;
    private final String endpoint;
    private volatile boolean bucketVerificado;

    MinioAlmacen(String endpoint, String accessKey, String secretKey, String bucket) {
        this.cliente = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.endpoint = endpoint;
    }

    @Override
    public void guardar(String clave, byte[] contenido, String tipoContenido) {
        try {
            asegurarBucket();
            cliente.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(clave)
                    .stream(new ByteArrayInputStream(contenido), contenido.length, -1)
                    .contentType(tipoContenido)
                    .build());
        } catch (Exception e) {
            throw new AlmacenNoDisponibleException("No se pudo guardar el archivo en MinIO", e);
        }
    }

    @Override
    public byte[] leer(String clave) {
        try (InputStream in = cliente.getObject(GetObjectArgs.builder().bucket(bucket).object(clave).build())) {
            return in.readAllBytes();
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new ArchivoNoEncontradoException(clave);
            }
            throw new AlmacenNoDisponibleException("MinIO rechazo la lectura", e);
        } catch (Exception e) {
            throw new AlmacenNoDisponibleException("No se pudo leer el archivo de MinIO", e);
        }
    }

    @Override
    public String descripcion() {
        return "MinIO " + endpoint + "/" + bucket;
    }

    /**
     * El bucket se crea en el primer uso y no al arrancar: si MinIO todavia no esta listo cuando
     * arranca el backend, el resto del sistema no tiene por que quedarse sin levantar.
     */
    private void asegurarBucket() throws Exception {
        if (bucketVerificado) {
            return;
        }
        synchronized (this) {
            if (!bucketVerificado) {
                if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    cliente.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("Bucket de evidencias creado: {}", bucket);
                }
                bucketVerificado = true;
            }
        }
    }
}
