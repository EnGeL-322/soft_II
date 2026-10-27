package org.ramiro.huata.verificacion;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.usuarios.Usuario;
import org.ramiro.huata.usuarios.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Codigo de 6 digitos por correo para confirmar cambios sensibles de Configuracion.
 *
 * <p><b>Que anade sobre el permiso.</b> {@code parametros.write} dice quien puede cambiar precios;
 * esto comprueba que quien los esta cambiando es esa persona y no una sesion abierta que quedo sin
 * vigilar. Son dos preguntas distintas y hacen falta las dos.
 *
 * <p><b>El codigo va al correo del propio solicitante</b>, no a un buzon compartido de la
 * cooperativa. El prototipo dibuja un campo "Correo de verificacion" configurable, pero un buzon al
 * que llegan todos los codigos y al que entra todo el mundo no verifica a nadie: cualquiera con
 * acceso a ese correo podria confirmar un cambio hecho por otro.
 */
@Service
@RequiredArgsConstructor
public class VerificacionService {

    private final CodigoVerificacionRepository codigoRepository;
    private final UsuarioRepository usuarioRepository;
    private final EnviadorDeCodigo enviador;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    /**
     * Seis digitos son un millon de combinaciones: suficiente con caducidad corta y tope de intentos,
     * insuficiente sin ellos. Por eso las dos cosas de abajo no son ajustes, son parte del diseno.
     */
    private static final int MAX_INTENTOS = 5;

    /** Evita que pulsar "Enviar código" tres veces genere tres correos y confunda cual vale. */
    private static final Duration ESPERA_ENTRE_ENVIOS = Duration.ofSeconds(30);

    @Value("${huata.verificacion.minutos-vigencia:10}")
    private int minutosVigencia;

    /** Lo que la pantalla necesita saber sin que el codigo salga nunca en la respuesta. */
    public record CodigoEmitido(String destinatarioEnmascarado, Instant expiraEn, int minutosVigencia) {}

    @Transactional
    public CodigoEmitido solicitar(PropositoVerificacion proposito) {
        Usuario usuario = usuarioActual();
        String destino = usuario.getEmail();
        if (destino == null || destino.isBlank()) {
            throw new BusinessRuleException("Su usuario no tiene correo registrado, así que no se le puede enviar el "
                    + "código. Regístrelo en Usuarios antes de cambiar precios.");
        }

        Instant ahora = Instant.now();
        codigoRepository
                .findTopBySolicitanteAndPropositoOrderByIdDesc(usuario.getUsername(), proposito)
                .filter(c -> c.vigente(ahora))
                .ifPresent(previo -> {
                    if (previo.getCreatedAt() != null
                            && previo.getCreatedAt().plus(ESPERA_ENTRE_ENVIOS).isAfter(ahora)) {
                        throw new BusinessRuleException(
                                "Se acaba de enviar un código a su correo. Espere unos segundos antes de pedir otro.");
                    }
                });

        // Los anteriores dejan de valer. Dos codigos vivos a la vez duplican la superficie de acierto
        // y dejan a la persona sin saber cual de los dos correos es el bueno.
        for (CodigoVerificacion vivo :
                codigoRepository.findBySolicitanteAndPropositoAndUsadoFalseAndActivoTrue(
                        usuario.getUsername(), proposito)) {
            vivo.setActivo(false);
            codigoRepository.save(vivo);
        }

        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        CodigoVerificacion nuevo = new CodigoVerificacion();
        nuevo.setProposito(proposito);
        nuevo.setSolicitante(usuario.getUsername());
        nuevo.setDestinatario(destino);
        // Hash, nunca los digitos: mientras esta vigente vale lo mismo que el permiso que protege.
        nuevo.setCodigoHash(passwordEncoder.encode(codigo));
        nuevo.setExpiraEn(ahora.plus(Duration.ofMinutes(minutosVigencia)));
        CodigoVerificacion guardado = codigoRepository.save(nuevo);

        enviador.enviar(destino, codigo, minutosVigencia);
        auditoriaService.registrar(
                "Código de verificación enviado", "Configuración", proposito + " · " + enmascarar(destino));

        return new CodigoEmitido(enmascarar(destino), guardado.getExpiraEn(), minutosVigencia);
    }

    /**
     * Comprueba el codigo y lo quema. Si no cuadra, la operacion no sigue.
     *
     * <p>Se llama desde el controlador, en el mismo sitio donde se comprueba el permiso: son la misma
     * clase de pregunta —quien esta haciendo esto— y no logica de negocio del modulo de precios.
     *
     * <p>Consecuencia asumida: el codigo se consume aunque el guardado posterior falle por otra razon
     * (una validacion, por ejemplo) y hay que pedir uno nuevo. Es preferible a lo contrario, que seria
     * dejar vivo un codigo ya presentado.
     */
    @Transactional
    public void exigirCodigo(PropositoVerificacion proposito, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new BusinessRuleException(
                    "Falta el código de verificación. Pida uno con «Enviar código» y revise su correo.");
        }
        String usuario = usuarioActual().getUsername();
        CodigoVerificacion emitido = codigoRepository
                .findTopBySolicitanteAndPropositoOrderByIdDesc(usuario, proposito)
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay ningún código pendiente para su usuario. Pida uno con «Enviar código»."));

        if (!emitido.vigente(Instant.now())) {
            throw new BusinessRuleException("El código ya no es válido: venció o ya se usó. Pida uno nuevo.");
        }
        if (emitido.getIntentos() >= MAX_INTENTOS) {
            throw new BusinessRuleException("Demasiados intentos fallidos con este código. Pida uno nuevo.");
        }

        if (!passwordEncoder.matches(codigo.trim(), emitido.getCodigoHash())) {
            emitido.setIntentos(emitido.getIntentos() + 1);
            // Al agotarse los intentos el codigo se quema: si no, seis digitos se prueban a fuerza
            // bruta con paciencia y un script.
            if (emitido.getIntentos() >= MAX_INTENTOS) {
                emitido.setActivo(false);
            }
            codigoRepository.save(emitido);
            auditoriaService.registrar(
                    "Código de verificación incorrecto",
                    "Configuración",
                    proposito + " · intento " + emitido.getIntentos() + " de " + MAX_INTENTOS);
            throw new BusinessRuleException("El código no coincide. Le quedan "
                    + Math.max(0, MAX_INTENTOS - emitido.getIntentos()) + " intento(s).");
        }

        emitido.setUsado(true);
        codigoRepository.save(emitido);
    }

    private Usuario usuarioActual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        if (username == null) {
            throw new BusinessRuleException("No hay una sesión activa.");
        }
        return usuarioRepository
                .findByUsernameIgnoreCaseAndActivoTrue(username)
                .orElseThrow(() -> new BusinessRuleException("El usuario de la sesión ya no está activo."));
    }

    /** "secretaria@huata.coop" -> "sec***@huata.coop": confirma el buzon sin publicarlo entero. */
    static String enmascarar(String correo) {
        int arroba = correo.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        String usuario = correo.substring(0, arroba);
        String visible = usuario.length() <= 3 ? usuario.substring(0, 1) : usuario.substring(0, 3);
        return visible + "***" + correo.substring(arroba);
    }
}
