package org.ramiro.huata.verificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * El codigo de 6 digitos que Configuracion exige para cambiar precios.
 *
 * <p>Lo que se protege es concreto: {@code parametros.write} dice quien PUEDE cambiar precios, pero
 * una sesion abierta y sin vigilar tiene ese permiso igual. El codigo comprueba que quien lo esta
 * haciendo es la persona, y por eso importa que no viaje en la respuesta, que sirva una sola vez y
 * que no se pueda adivinar a fuerza de intentos.
 *
 * <p>El envio se sustituye por un doble que guarda el codigo: es la unica forma de conocerlo desde
 * la prueba sin exponerlo en la API, que es justamente lo que no debe pasar.
 */
@SpringBootTest
@Transactional
class VerificacionServiceTests {

    /** Doble del enviador: se queda con el ultimo codigo en vez de mandarlo por correo. */
    static class EnviadorDeMentira extends EnviadorDeCodigo {
        String ultimoCodigo;
        String ultimoDestinatario;

        EnviadorDeMentira() {
            super(null);
        }

        @Override
        public void enviar(String destinatario, String codigo, int minutosVigencia) {
            this.ultimoDestinatario = destinatario;
            this.ultimoCodigo = codigo;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        EnviadorDeMentira enviadorDeMentira() {
            return new EnviadorDeMentira();
        }
    }

    @Autowired
    private VerificacionService verificacionService;

    @Autowired
    private CodigoVerificacionRepository codigoRepository;

    @Autowired
    private EnviadorDeMentira enviador;

    @PersistenceContext
    private EntityManager em;

    @BeforeEach
    void limpiar() {
        codigoRepository.deleteAll();
        enviador.ultimoCodigo = null;
    }

    private String pedirCodigo() {
        verificacionService.solicitar(PropositoVerificacion.PRECIOS);
        return enviador.ultimoCodigo;
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elCodigoTieneSeisDigitosVaAlCorreoDelSolicitanteYNoViajaEnLaRespuesta() {
        VerificacionService.CodigoEmitido emitido = verificacionService.solicitar(PropositoVerificacion.PRECIOS);

        assertThat(enviador.ultimoCodigo).matches("\\d{6}");
        assertThat(enviador.ultimoDestinatario).isEqualTo("admin@huata.pe");
        // La respuesta dice a donde fue y cuando vence; el codigo, jamas.
        assertThat(emitido.destinatarioEnmascarado()).isEqualTo("adm***@huata.pe");
        assertThat(emitido.expiraEn()).isAfter(Instant.now());
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elCodigoNoSeGuardaEnClaro() {
        String codigo = pedirCodigo();

        assertThat(codigoRepository.findAll())
                .singleElement()
                .satisfies(c -> assertThat(c.getCodigoHash()).isNotEqualTo(codigo).startsWith("$2"));
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void elCodigoCorrectoValeUnaSolaVez() {
        String codigo = pedirCodigo();

        verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, codigo); // no lanza

        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, codigo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya no es válido");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void sinCodigoNoSePasa() {
        pedirCodigo();

        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Falta el código");
        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, "   "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Falta el código");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void sinHaberPedidoNingunCodigoNoHayNadaQueValidar() {
        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, "123456"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay ningún código pendiente");
    }

    /** Seis dígitos son un millón de combinaciones: sin tope de intentos se prueban con un script. */
    @Test
    @WithMockUser(username = "admin@huata.pe")
    void aLosCincoIntentosFallidosElCodigoSeQuema() {
        String codigo = pedirCodigo();
        String equivocado = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, equivocado))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no coincide");
        }

        // Y el bueno tampoco vale ya: el codigo quemado no se recupera acertando despues.
        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, codigo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya no es válido");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void unCodigoVencidoNoVale() {
        String codigo = pedirCodigo();
        CodigoVerificacion emitido = codigoRepository.findAll().get(0);
        emitido.setExpiraEn(Instant.now().minus(1, ChronoUnit.MINUTES));
        codigoRepository.save(emitido);

        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, codigo))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("venció");
    }

    /**
     * El código es de quien lo pidió. Si valiera para cualquiera, bastaría con que a una sola persona
     * le llegara un correo para que otra confirmara el cambio.
     */
    @Test
    void elCodigoDeUnUsuarioNoSirveParaOtro() {
        String codigoDelAdmin = comoAdmin(this::pedirCodigo);

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "calidad.demo@huata.pe", "n/a", java.util.List.of()));

        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, codigoDelAdmin))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay ningún código pendiente");
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void pedirOtroCodigoInvalidaElAnterior() {
        String primero = pedirCodigo();
        envejecerElUltimoCodigo();

        String segundo = pedirCodigo();

        assertThat(segundo).isNotEqualTo(primero);
        assertThatThrownBy(() -> verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, primero))
                .isInstanceOf(BusinessRuleException.class);
        verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, segundo); // el nuevo si vale
    }

    @Test
    @WithMockUser(username = "admin@huata.pe")
    void pedirDosCodigosSeguidosSeFrena() {
        pedirCodigo();

        assertThatThrownBy(() -> verificacionService.solicitar(PropositoVerificacion.PRECIOS))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Espere unos segundos");
    }

    @Test
    void elCorreoSeEnmascaraSinPerderElBuzon() {
        assertThat(VerificacionService.enmascarar("secretaria@huata.coop")).isEqualTo("sec***@huata.coop");
        assertThat(VerificacionService.enmascarar("ab@huata.coop")).isEqualTo("a***@huata.coop");
        assertThat(VerificacionService.enmascarar("sin-arroba")).isEqualTo("***");
    }

    /**
     * Retrasa el created_at del ultimo codigo para simular el paso del tiempo sin dormir la prueba.
     * Va por JPQL porque created_at es {@code updatable = false}: la auditoria de la entidad no deja
     * reescribirlo con un save, que es exactamente lo que se quiere en produccion.
     */
    private void envejecerElUltimoCodigo() {
        em.flush();
        em.createQuery("update CodigoVerificacion c set c.createdAt = :momento where c.id = :id")
                .setParameter("momento", Instant.now().minus(5, ChronoUnit.MINUTES))
                .setParameter("id", codigoRepository.findAll().get(0).getId())
                .executeUpdate();
        em.clear();
    }

    private String comoAdmin(java.util.function.Supplier<String> accion) {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin@huata.pe", "n/a", java.util.List.of()));
        return accion.get();
    }
}
