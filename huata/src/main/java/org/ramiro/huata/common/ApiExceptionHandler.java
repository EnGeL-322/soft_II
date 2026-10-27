package org.ramiro.huata.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ramiro.huata.almacen.AlmacenArchivos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones del dominio a respuestas HTTP consistentes en toda la API.
 *
 * <p>Formato unico de error {@code {timestamp, status, error, message, path}} (DESARROLLO.md
 * seccion 5.3). Los 401/403 que ocurren en la cadena de filtros, antes de llegar a un controlador,
 * los emite {@code RestAuthenticationHandlers} con esta misma forma.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(
            Instant timestamp, int status, String error, String message, String path, Map<String, String> fields) {}

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null, request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), null, request);
    }

    /**
     * 429 y no 422: la peticion es correcta, lo que sobra es el ritmo. El cliente tiene que entender
     * que basta con esperar, y la cabecera Retry-After (que pone el interceptor) le dice cuanto.
     */
    @ExceptionHandler(DemasiadasSolicitudesException.class)
    public ResponseEntity<ApiError> handleDemasiadasSolicitudes(
            DemasiadasSolicitudesException ex, HttpServletRequest request) {
        log.warn("Limite de consultas superado: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), null, request);
    }

    /**
     * 503 y no 500: el almacen de archivos (MinIO) no respondio. La app movil trata el 503 como
     * "reintentar mas tarde" y deja la foto del voucher en su cola en vez de darla por perdida.
     */
    @ExceptionHandler(AlmacenArchivos.AlmacenNoDisponibleException.class)
    public ResponseEntity<ApiError> handleAlmacenNoDisponible(
            AlmacenArchivos.AlmacenNoDisponibleException ex, HttpServletRequest request) {
        log.error("Almacen de archivos no disponible en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "El almacenamiento de archivos no esta disponible. Intenta mas tarde.", null, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleArchivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("Archivo demasiado grande: {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONTENT_TOO_LARGE, "El archivo supera el tamano permitido (8 MB).", null, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Usuario o contrasena incorrectos", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "No tienes permisos para esta accion", null, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Datos invalidos", fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null, request);
    }

    /**
     * Sin este handler, ResponseStatusException (usada p.ej. en OperacionService para 400/409)
     * caería en el catch-all de abajo y siempre respondería 500 en vez de su propio status.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return build(status, message, null, request);
    }

    /**
     * Errores del CLIENTE que Spring lanza antes de entrar al controlador: ruta inexistente, metodo
     * no permitido, Content-Type no soportado, cuerpo ilegible, parametro obligatorio ausente o de
     * tipo equivocado.
     *
     * <p>Sin este handler todos caian en el catch-all y la API respondia 500. Eso tenia dos costes:
     * el cliente no podia distinguir "enviaste algo mal" (que se arregla cambiando la peticion) de
     * "el servidor se rompio" (que no), y cada peticion equivocada -- incluidos los escaneos de
     * bots -- quedaba escrita como ERROR en el log, tapando los fallos reales (RNF-14).
     *
     * <p>El status sale de la propia excepcion cuando implementa {@link ErrorResponse} (Spring ya
     * sabe que 404/405/415 le corresponde); si alguna no lo hiciera, 400 es el supuesto correcto
     * porque todas estas son culpa de la peticion, no del servidor.
     */
    @ExceptionHandler({
        NoResourceFoundException.class,
        NoHandlerFoundException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiError> handleClientError(Exception ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (ex instanceof ErrorResponse respuesta) {
            HttpStatus resuelto = HttpStatus.resolve(respuesta.getStatusCode().value());
            if (resuelto != null) {
                status = resuelto;
            }
        }
        // WARN y sin traza: es un error del cliente, no un fallo del servidor que haya que depurar.
        log.warn("Peticion invalida: {} {} -> {} ({})", request.getMethod(), request.getRequestURI(), status.value(), ex.getClass().getSimpleName());
        return build(status, mensajeDe(ex, status), null, request);
    }

    /**
     * Mensaje util pero sin filtrar interioridades. Se nombra el parametro cuando el problema es un
     * parametro concreto (el cliente lo necesita para corregir la llamada y no revela nada del
     * servidor); en el resto se responde con el texto generico del status.
     */
    private static String mensajeDe(Exception ex, HttpStatus status) {
        if (ex instanceof MissingServletRequestParameterException falta) {
            return "Falta el parametro obligatorio: " + falta.getParameterName();
        }
        if (ex instanceof MethodArgumentTypeMismatchException tipo) {
            return "Valor invalido para el parametro: " + tipo.getName();
        }
        return switch (status) {
            case NOT_FOUND -> "Recurso no encontrado";
            case METHOD_NOT_ALLOWED -> "Metodo no permitido para este recurso";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            default -> "Solicitud invalida";
        };
    }

    /**
     * Catch-all. El detalle de la excepcion va al log del servidor, nunca al cliente: el mensaje de
     * una excepcion no controlada suele filtrar nombres de clase, SQL o rutas internas (OWASP A05,
     * "Security Misconfiguration").
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", null, request);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status, String message, Map<String, String> fields, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(
                        Instant.now(),
                        status.value(),
                        status.getReasonPhrase(),
                        message,
                        request.getRequestURI(),
                        fields));
    }
}
