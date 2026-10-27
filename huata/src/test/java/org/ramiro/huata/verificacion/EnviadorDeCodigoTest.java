package org.ramiro.huata.verificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * El envio del codigo de verificacion de precios.
 *
 * <p>El bug que se fija aqui: docker-compose pasa SPRING_MAIL_HOST vacio cuando no hay correo, Spring
 * creaba el JavaMailSender igual y cada pedido de codigo terminaba en 500 "Error interno" — nadie
 * podia cambiar precios, y el mensaje no decia por que.
 */
class EnviadorDeCodigoTest {

    @SuppressWarnings("unchecked")
    private EnviadorDeCodigo enviador(JavaMailSender sender, String host) {
        ObjectProvider<JavaMailSender> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(sender);
        EnviadorDeCodigo e = new EnviadorDeCodigo(proveedor);
        ReflectionTestUtils.setField(e, "remitente", "no-reply@huata.coop");
        ReflectionTestUtils.setField(e, "host", host);
        return e;
    }

    @Test
    void conElHostVacioNoIntentaMandarCorreoYElCodigoVaAlLog() {
        JavaMailSender sender = mock(JavaMailSender.class);

        enviador(sender, "").enviar("admin@huata.pe", "123456", 10);

        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sinServidorDeCorreoTampocoFalla() {
        enviador(null, null).enviar("admin@huata.pe", "123456", 10);
    }

    @Test
    void conCorreoConfiguradoLoManda() {
        JavaMailSender sender = mock(JavaMailSender.class);

        enviador(sender, "smtp.gmail.com").enviar("admin@huata.pe", "123456", 10);

        verify(sender).send(any(SimpleMailMessage.class));
    }

    /** Si el correo falla, la persona recibe un 503 con un mensaje que entiende, no un 500 genérico. */
    @Test
    void siElServidorDeCorreoFallaAvisaConUnMensajeClaro() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailAuthenticationException("Authentication failed"))
                .when(sender)
                .send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> enviador(sender, "smtp.gmail.com").enviar("admin@huata.pe", "123456", 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getReason()).contains("No se pudo enviar el correo");
                });
    }
}
