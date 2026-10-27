package org.ramiro.huata.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cuenta consultas por origen dentro de una ventana de un minuto, para que el portal publico no se
 * pueda recorrer a fuerza de intentos. Lo usa tambien "Mi cuenta" para frenar que se adivine la
 * contrasena actual (ahi el origen es el usuario, no la IP).
 *
 * <p><b>Por que hace falta.</b> {@code GET /public/productores/{codigo}/calidad} responde 200 si el
 * codigo existe y 404 si no, asi que probar codigos al azar revela cuales estan registrados. Eso ya
 * estaba anotado como pendiente, pero desde que el codigo del proveedor <b>es su DNI</b> lo que se
 * podria recorrer son documentos de personas. Limitar los intentos no vuelve secreto el dato — lo
 * vuelve caro de recolectar, que es lo que se puede hacer sin quitarle al proveedor la consulta de su
 * propia calidad.
 *
 * <p>Ventana fija y no deslizante a proposito: es la que se puede explicar en una frase ("N por
 * minuto") y la que no obliga a guardar la marca de tiempo de cada peticion. Un abusador puede
 * colocar 2N peticiones a caballo de dos ventanas; para frenar recoleccion masiva da igual, y la
 * simplicidad vale mas aqui que la precision.
 *
 * <p><b>Es por instancia.</b> El contador vive en memoria, asi que con varias replicas cada una
 * permitiria su propio cupo. El despliegue actual levanta un solo backend (ver docker-compose); si
 * algun dia se escala, esto necesita un contador compartido, igual que el canal de tiempo real.
 */
public class LimitadorDeConsultas {

    private static final Duration VENTANA = Duration.ofMinutes(1);

    /**
     * Techo de origenes vigilados a la vez. Sin el, quien rote direcciones haria crecer el mapa sin
     * limite y el limitador seria a su vez la forma de tumbar el servidor.
     */
    private static final int MAX_ORIGENES = 10_000;

    private final int maximoPorVentana;
    private final Clock reloj;
    private final Map<String, Contador> porOrigen = new ConcurrentHashMap<>();

    public LimitadorDeConsultas(int maximoPorVentana, Clock reloj) {
        this.maximoPorVentana = maximoPorVentana;
        this.reloj = reloj;
    }

    /**
     * Registra un intento de {@code origen} y dice si se le permite.
     *
     * <p>Devolver false no es un error del cliente legitimo: un proveedor consultando su propia
     * calidad hace una peticion, no cincuenta.
     */
    public boolean permitir(String origen) {
        if (maximoPorVentana <= 0) {
            return true; // Limite desactivado por configuracion.
        }
        Instant ahora = reloj.instant();
        if (porOrigen.size() >= MAX_ORIGENES) {
            purgarVencidos(ahora);
        }
        Contador contador = porOrigen.compute(origen, (clave, actual) ->
                actual == null || actual.venceAntesDe(ahora) ? new Contador(ahora.plus(VENTANA)) : actual);
        return contador.intentos.incrementAndGet() <= maximoPorVentana;
    }

    /** Segundos que faltan para que el origen vuelva a tener cupo; sirve para la cabecera Retry-After. */
    public long segundosParaReintentar(String origen) {
        Contador contador = porOrigen.get(origen);
        if (contador == null) {
            return 0;
        }
        long faltan = Duration.between(reloj.instant(), contador.fin).getSeconds();
        return Math.max(faltan, 1);
    }

    private void purgarVencidos(Instant ahora) {
        porOrigen.values().removeIf(c -> c.venceAntesDe(ahora));
        if (porOrigen.size() >= MAX_ORIGENES) {
            // Siguen siendo demasiados y todos vigentes: se empieza de cero. Prefiero perder el conteo
            // un momento a quedarme sin memoria por sostenerlo.
            porOrigen.clear();
        }
    }

    private static final class Contador {
        private final Instant fin;
        private final AtomicInteger intentos = new AtomicInteger();

        private Contador(Instant fin) {
            this.fin = fin;
        }

        private boolean venceAntesDe(Instant ahora) {
            return !ahora.isBefore(fin);
        }
    }
}
