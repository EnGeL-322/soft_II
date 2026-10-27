package org.ramiro.huata.calidad;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.almacen.AlmacenArchivos;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Foto del voucher del analizador, guardada en el almacen de objetos y referenciada desde la base
 * (RF-19, RF-85, RNF-13, RNF-20, seccion 24).
 *
 * <p>Antes la app tomaba la foto y la dejaba en el telefono: no habia a donde subirla. Ahora queda
 * la imagen original, el texto que leyo el OCR, los valores que propuso y quien los confirmo.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    /** Mismo tope que spring.servlet.multipart.max-file-size; se repite para no depender de Tomcat. */
    static final int TAMANO_MAXIMO_BYTES = 8 * 1024 * 1024;

    private static final int LARGO_MAXIMO_TEXTO_OCR = 20_000;
    private static final int LARGO_MAXIMO_VALORES_OCR = 4_000;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final ControlCalidadRepository controlCalidadRepository;
    private final EvidenciaCalidadRepository evidenciaRepository;
    private final AlmacenArchivos almacen;
    private final AuditoriaService auditoriaService;

    public record Datos(String clientUuid, String textoOcr, String valoresOcr, Double confianzaOcr) {}

    /** Archivo listo para devolver al navegador. */
    public record Archivo(byte[] contenido, String tipoContenido) {}

    @Transactional
    public EvidenciaCalidad subir(Long controlId, byte[] contenido, Datos datos, String usuario) {
        // Idempotencia: la app reintenta desde su cola offline con el mismo clientUuid. Si la subida
        // ya llego antes (se corto la respuesta, no la peticion), se devuelve la que existe.
        String uuid = normalizarUuid(datos.clientUuid());
        if (uuid != null) {
            var existente = evidenciaRepository.findByClientUuid(uuid);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        ControlCalidad control = controlCalidadRepository
                .findById(controlId)
                .orElseThrow(() -> NotFoundException.of("Control de calidad", controlId));

        if (contenido == null || contenido.length == 0) {
            throw new BusinessRuleException("La foto del voucher llego vacia.");
        }
        if (contenido.length > TAMANO_MAXIMO_BYTES) {
            throw new BusinessRuleException("La foto del voucher supera los 8 MB.");
        }
        // El tipo se decide por el contenido real, nunca por lo que declara el cliente: un archivo
        // con nombre .jpg puede ser cualquier cosa (OWASP, File Upload Cheat Sheet).
        TipoImagen tipo = TipoImagen.detectar(contenido);
        if (tipo == null) {
            throw new BusinessRuleException("El archivo no es una imagen JPEG, PNG o WEBP.");
        }

        LocalDate hoy = LocalDate.now(LIMA);
        String clave = "vouchers/%d/%02d/control-%d/%s.%s".formatted(
                hoy.getYear(), hoy.getMonthValue(), controlId, UUID.randomUUID(), tipo.extension);
        // Primero el archivo y despues la fila: si falla el almacen no queda una evidencia que apunte
        // a nada. Si falla la base despues, queda un archivo huerfano en el almacen, que es el error
        // menos grave de los dos (no se pierde ni se inventa evidencia).
        almacen.guardar(clave, contenido, tipo.mime);

        EvidenciaCalidad evidencia = new EvidenciaCalidad();
        evidencia.setControlCalidad(control);
        evidencia.setTipo(TipoEvidencia.VOUCHER);
        evidencia.setEstado(EstadoEvidencia.UPLOADED);
        evidencia.setClaveArchivo(clave);
        evidencia.setTipoContenido(tipo.mime);
        evidencia.setTamanoBytes((long) contenido.length);
        evidencia.setSha256(sha256(contenido));
        evidencia.setTextoOcr(recortar(datos.textoOcr(), LARGO_MAXIMO_TEXTO_OCR));
        evidencia.setValoresOcr(recortar(datos.valoresOcr(), LARGO_MAXIMO_VALORES_OCR));
        evidencia.setConfianzaOcr(datos.confianzaOcr());
        evidencia.setVerificadoPor(usuario);
        evidencia.setClientUuid(uuid);
        // La columna url_archivo es obligatoria desde V1; para los archivos propios apunta al
        // endpoint que los sirve con autorizacion (nunca a MinIO directo).
        evidencia.setUrlArchivo("pendiente");
        evidencia = evidenciaRepository.save(evidencia);
        evidencia.setUrlArchivo("/api/v1/calidad/evidencias/" + evidencia.getId() + "/archivo");

        auditoriaService.registrar(
                "Voucher", "Calidad", "Control " + controlId + " · " + contenido.length / 1024 + " KB");
        return evidencia;
    }

    @Transactional(readOnly = true)
    public Archivo archivo(Long evidenciaId) {
        EvidenciaCalidad evidencia = evidenciaRepository
                .findById(evidenciaId)
                .orElseThrow(() -> NotFoundException.of("Evidencia", evidenciaId));
        if (evidencia.getClaveArchivo() == null) {
            // Evidencia antigua que solo guardaba una URL externa: no hay archivo propio que servir.
            throw NotFoundException.of("Archivo de la evidencia", evidenciaId);
        }
        try {
            return new Archivo(almacen.leer(evidencia.getClaveArchivo()), evidencia.getTipoContenido());
        } catch (AlmacenArchivos.ArchivoNoEncontradoException e) {
            throw NotFoundException.of("Archivo de la evidencia", evidenciaId);
        }
    }

    private static String normalizarUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        String limpio = uuid.trim();
        if (limpio.length() > 64 || !limpio.matches("[A-Za-z0-9-]+")) {
            throw new BusinessRuleException("Identificador de subida invalido.");
        }
        return limpio;
    }

    private static String recortar(String texto, int largo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return texto.length() > largo ? texto.substring(0, largo) : texto;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    enum TipoImagen {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        final String mime;
        final String extension;

        TipoImagen(String mime, String extension) {
            this.mime = mime;
            this.extension = extension;
        }

        /** Por los primeros bytes del archivo ("numeros magicos"). */
        static TipoImagen detectar(byte[] b) {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                    && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
                return PNG;
            }
            if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
                return WEBP;
            }
            return null;
        }
    }
}
