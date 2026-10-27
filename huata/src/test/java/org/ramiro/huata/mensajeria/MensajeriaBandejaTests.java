package org.ramiro.huata.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La bandeja de mensajes en la pantalla de inicio del proveedor (APK): muestra cuantos mensajes
 * nuevos le mando la planta (Secretaria o Administracion, uno a uno o por aviso masivo) y los ultimos,
 * <b>sin marcarlos como leidos</b>. Solo abrir la conversacion los marca.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "secretaria.demo@huata.pe")
class MensajeriaBandejaTests {

    private static final String PROVEEDOR = "proveedor.demo@huata.pe";
    private static final String SECRETARIA = "secretaria.demo@huata.pe";

    @Autowired
    private MensajeriaService service;

    @Autowired
    private ProductorRepository productorRepository;

    private Productor productor;

    @BeforeEach
    void escenario() {
        productor = productorRepository.findByUsuario_UsernameIgnoreCase(PROVEEDOR).orElseThrow();
        // Se parte de la conversacion leida para que el conteo de "nuevos" dependa solo de esta prueba.
        service.miConversacion(PROVEEDOR);
    }

    private void secretariaEscribe(String texto) {
        service.enviarAProveedor(productor.getId(), new MensajeriaDtos.EnviarMensajeRequest(texto), SECRETARIA);
    }

    @Test
    void cuentaLosMensajesNuevosQueMandoLaPlanta() {
        secretariaEscribe("Mañana no hay recojo por feriado.");
        secretariaEscribe("El pago sale el viernes.");

        MensajeriaDtos.ConversacionDetalleResponse bandeja = service.miBandeja(PROVEEDOR);

        assertThat(bandeja.conversacion().mensajesSinLeer()).isEqualTo(2);
        assertThat(bandeja.conversacion().ultimoMensaje()).isEqualTo("El pago sale el viernes.");
        assertThat(bandeja.mensajes()).last().satisfies(m -> {
            assertThat(m.mio()).isFalse();
            assertThat(m.cuerpo()).isEqualTo("El pago sale el viernes.");
        });
    }

    /** Mostrar el resumen no es abrir la conversacion: si marcara, "nuevos" siempre diria cero. */
    @Test
    void verLaBandejaNoMarcaLosMensajesComoLeidos() {
        secretariaEscribe("Aviso de prueba.");

        service.miBandeja(PROVEEDOR);

        assertThat(service.miBandeja(PROVEEDOR).conversacion().mensajesSinLeer()).isEqualTo(1);
    }

    @Test
    void abrirLaConversacionLosMarcaYLaBandejaQuedaEnCero() {
        secretariaEscribe("Aviso de prueba.");

        service.miConversacion(PROVEEDOR);

        assertThat(service.miBandeja(PROVEEDOR).conversacion().mensajesSinLeer()).isZero();
    }

    @Test
    void traeComoMaximoLosTresUltimosMensajes() {
        for (int i = 1; i <= 5; i++) {
            secretariaEscribe("Mensaje " + i);
        }

        MensajeriaDtos.ConversacionDetalleResponse bandeja = service.miBandeja(PROVEEDOR);

        assertThat(bandeja.mensajes()).extracting(MensajeriaDtos.MensajeResponse::cuerpo)
                .containsExactly("Mensaje 3", "Mensaje 4", "Mensaje 5");
        assertThat(bandeja.conversacion().mensajesSinLeer()).isEqualTo(5);
    }

    @Test
    void soloUnProveedorTieneBandeja() {
        assertThatThrownBy(() -> service.miBandeja(SECRETARIA))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no es de proveedor");
    }
}
