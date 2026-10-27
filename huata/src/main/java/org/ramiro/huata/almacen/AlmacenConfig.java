package org.ramiro.huata.almacen;

import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
class AlmacenConfig {

    @Bean
    AlmacenArchivos almacenArchivos(
            @Value("${huata.almacen.minio.endpoint:}") String endpoint,
            @Value("${huata.almacen.minio.access-key:}") String accessKey,
            @Value("${huata.almacen.minio.secret-key:}") String secretKey,
            @Value("${huata.almacen.minio.bucket:huata-evidencias}") String bucket,
            @Value("${huata.almacen.directorio:${java.io.tmpdir}/huata-evidencias}") String directorio) {
        AlmacenArchivos almacen;
        if (endpoint == null || endpoint.isBlank()) {
            almacen = new DiscoAlmacen(Path.of(directorio));
            log.warn("Evidencias en {} (sin MinIO configurado). Aceptable en desarrollo, no en produccion.", almacen.descripcion());
        } else {
            if (accessKey.isBlank() || secretKey.isBlank()) {
                throw new IllegalStateException("HUATA_MINIO_ENDPOINT esta definido pero faltan HUATA_MINIO_ACCESS_KEY/SECRET_KEY");
            }
            almacen = new MinioAlmacen(endpoint, accessKey, secretKey, bucket);
            log.info("Evidencias en {}", almacen.descripcion());
        }
        return almacen;
    }
}
