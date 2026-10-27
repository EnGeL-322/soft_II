package org.ramiro.huata.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.ramiro.huata.common.LimitadorDeConsultas;
import org.ramiro.huata.common.DemasiadasSolicitudesException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limita cuantas veces por minuto se puede consultar el portal publico desde un mismo origen.
 *
 * <p>Se aplica solo a la consulta por codigo de proveedor, que es la unica enumerable: las de zonas e
 * incidencias devuelven el mismo resumen del dia pregunte quien pregunte, asi que repetirlas no revela
 * nada que la primera no revelara ya.
 */
@Component
public class LimiteConsultaPublicaInterceptor implements HandlerInterceptor {

    private final LimitadorDeConsultas limitador;

    LimiteConsultaPublicaInterceptor(
            @Value("${huata.portal.consultas-por-minuto:20}") int consultasPorMinuto) {
        this.limitador = new LimitadorDeConsultas(consultasPorMinuto, Clock.systemUTC());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String origen = origenDe(request);
        if (limitador.permitir(origen)) {
            return true;
        }
        response.setHeader("Retry-After", String.valueOf(limitador.segundosParaReintentar(origen)));
        throw new DemasiadasSolicitudesException(
                "Demasiadas consultas seguidas. Espera un momento antes de volver a intentar.");
    }

    /**
     * Quien pregunta de verdad.
     *
     * <p><b>Detalle que decide si esto sirve o estorba:</b> el backend esta detras de nginx, asi que
     * {@code getRemoteAddr()} devuelve siempre la IP del proxy. Contando por ahi, todo el mundo
     * compartiria un unico cupo y el primer abusador dejaria sin portal a los demas — el limitador se
     * convertiria en la forma de tumbar el servicio. nginx envia {@code X-Real-IP} y
     * {@code X-Forwarded-For} (ver huata-web/nginx.conf); se usa el primero disponible.
     *
     * <p><b>El orden no es arbitrario.</b> nginx pone {@code X-Real-IP} con {@code $remote_addr},
     * <i>sobreescribiendo</i> lo que mandara el cliente, asi que detras del proxy esa cabecera no se
     * puede falsificar (comprobado: mandar una X-Real-IP inventada no devuelve el cupo).
     * {@code X-Forwarded-For}, en cambio, se construye con {@code $proxy_add_x_forwarded_for}, que
     * <i>anade</i> a lo que venia: su primer elemento si es falsificable. Por eso es solo el respaldo
     * para el caso en que el backend quedara expuesto sin nginx delante.
     *
     * <p>Aun asi esto no es un control de acceso: es una barrera contra recoleccion masiva, no una
     * identificacion. Quien disponga de muchas direcciones sigue pudiendo repartir los intentos.
     */
    private static String origenDe(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        String reenviado = request.getHeader("X-Forwarded-For");
        if (reenviado != null && !reenviado.isBlank()) {
            // El primero de la lista es el cliente original; el resto son los proxies del camino.
            return reenviado.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
