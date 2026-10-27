package org.ramiro.huata.verificacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manda el codigo al correo de quien lo pidio.
 *
 * <p><b>Sin servidor de correo configurado, el codigo se escribe en el log</b> y el envio no falla.
 * Es lo mismo que hace el prototipo ("el envio de correo se simula mostrando el codigo generado; en
 * produccion lo enviara el backend") y permite que dev y las pruebas funcionen sin SMTP. En cuanto
 * {@code spring.mail.host} existe, Spring crea el {@link JavaMailSender} y el correo sale de verdad,
 * sin tocar codigo.
 *
 * <p><b>Las credenciales van por entorno</b> ({@code MAIL_USERNAME}, {@code MAIL_PASSWORD}), nunca en
 * el repositorio. Con Gmail hace falta una contrasena de aplicacion, no la del correo.
 *
 * <p>Si el envio falla, la operacion falla: dar por bueno un codigo que nunca salio dejaria a la
 * persona esperando un correo que no existe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnviadorDeCodigo {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${huata.verificacion.remitente:no-reply@huata.coop}")
    private String remitente;

    /**
     * docker-compose pasa {@code SPRING_MAIL_HOST} VACIO cuando no se configura correo, y para Spring
     * una propiedad vacia sigue estando "definida": crea el JavaMailSender igual, contra un servidor
     * que no existe, y cada pedido de codigo terminaba en 500. Un host en blanco es "sin correo".
     */
    @Value("${spring.mail.host:}")
    private String host;

    public void enviar(String destinatario, String codigo, int minutosVigencia) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || host == null || host.isBlank()) {
            // No es un error: es el modo sin SMTP. Se registra en WARN para que en produccion, donde
            // el correo SI deberia estar configurado, el aviso se vea en los logs.
            log.warn(
                    "Sin servidor de correo configurado (spring.mail.host). Codigo de verificacion para {}: {} "
                            + "(vigencia {} min). Configure SMTP por entorno antes de produccion.",
                    destinatario,
                    codigo,
                    minutosVigencia);
            return;
        }

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject("Huata Lácteos · código de verificación");
        mensaje.setText("Su código para confirmar el cambio de precios es " + codigo + ".\n\n"
                + "Vence en " + minutosVigencia + " minutos y sirve una sola vez.\n"
                + "Si no fue usted quien lo pidió, avise a administración: alguien está intentando "
                + "cambiar los precios con su cuenta.");
        try {
            sender.send(mensaje);
        } catch (MailException e) {
            // Es un fallo del servidor (credenciales o SMTP caido), no de quien pidio el codigo: se
            // registra como ERROR con la causa, y la persona recibe un mensaje que puede entender en
            // vez de "Error interno del servidor". La transaccion de VerificacionService se revierte,
            // asi que no queda vivo un codigo que nunca llego.
            log.error("No se pudo enviar el codigo de verificacion a {}", destinatario, e);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo con el código. El servidor de correo no respondió o está mal "
                            + "configurado: avise al administrador del sistema.");
        }
    }
}
