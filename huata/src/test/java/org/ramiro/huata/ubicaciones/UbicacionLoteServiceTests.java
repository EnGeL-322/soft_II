package org.ramiro.huata.ubicaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envio por lotes del recorrido que el movil guardo sin cobertura (RF-17, RF-18, RN-18).
 *
 * <p>Lo que importa aqui no es que se guarden puntos, sino las dos cosas que hacen utilizable un
 * recorrido reconstruido: que conserve la hora en que se paso por cada sitio (y no la del envio) y
 * que reintentar un lote cortado a medias no lo duplique.
 */
@SpringBootTest
@Transactional
class UbicacionLoteServiceTests {

    @Autowired
    private UbicacionGpsService service;

    @Autowired
    private UbicacionGpsRepository repository;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    /** Un acopiador demo con cuenta, que es lo que exige el servicio para resolver quien reporta. */
    private String usuarioDeUnAcopiador() {
        return acopiadorRepository.findAll().stream()
                .filter(Acopiador::isActivo)
                .filter(a -> a.getUsuario() != null)
                .map(a -> a.getUsuario().getUsername())
                .findFirst()
                .orElseThrow();
    }

    private Long acopiadorDe(String username) {
        return acopiadorRepository.findByUsuario_UsernameIgnoreCase(username).orElseThrow().getId();
    }

    private static UbicacionRequest punto(double lat, Instant fecha) {
        return new UbicacionRequest(lat, -69.9, 8.0, 1.2, fecha);
    }

    /**
     * Los datos DEMO ya siembran recorrido reciente para los acopiadores, asi que estas pruebas
     * trabajan en una ventana de hace 30 dias que el seeder no toca: lo que se verifica es lo que
     * inserta el lote, no lo que habia antes.
     */
    private static Instant momentoAislado(long minutosDentroDeLaVentana) {
        return Instant.now()
                .minus(30, ChronoUnit.DAYS)
                .plus(minutosDentroDeLaVentana, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    @WithMockUser
    void elLoteConservaLaHoraEnQueSePasoPorCadaPunto() {
        String usuario = usuarioDeUnAcopiador();
        Instant primero = momentoAislado(0);
        Instant segundo = momentoAislado(60);

        UbicacionLoteResponse resumen = service.registrarLote(
                usuario, new UbicacionLoteRequest(List.of(punto(-15.61, primero), punto(-15.62, segundo))));

        assertThat(resumen.recibidos()).isEqualTo(2);
        assertThat(resumen.duplicados()).isZero();
        List<UbicacionGps> guardadas = repository.findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(
                acopiadorDe(usuario), primero.minusSeconds(1), segundo.plusSeconds(1));
        assertThat(guardadas).extracting(UbicacionGps::getFecha).containsExactly(primero, segundo);
    }

    @Test
    @WithMockUser
    void reenviarElMismoLoteNoDuplicaElRecorrido() {
        String usuario = usuarioDeUnAcopiador();
        Instant momento = momentoAislado(120);
        var lote = new UbicacionLoteRequest(List.of(punto(-15.61, momento)));
        service.registrarLote(usuario, lote);

        UbicacionLoteResponse reintento = service.registrarLote(usuario, lote);

        assertThat(reintento.recibidos()).isZero();
        assertThat(reintento.duplicados()).isEqualTo(1);
        assertThat(repository.findByAcopiadorIdAndFechaBetweenOrderByFechaAsc(
                        acopiadorDe(usuario), momento.minusSeconds(1), momento.plusSeconds(1)))
                .hasSize(1);
    }

    @Test
    @WithMockUser
    void unLoteConElMismoPuntoRepetidoSoloGuardaUno() {
        String usuario = usuarioDeUnAcopiador();
        Instant momento = momentoAislado(180);

        UbicacionLoteResponse resumen = service.registrarLote(
                usuario, new UbicacionLoteRequest(List.of(punto(-15.61, momento), punto(-15.61, momento))));

        assertThat(resumen.recibidos()).isEqualTo(1);
        assertThat(resumen.duplicados()).isEqualTo(1);
    }

    /** Un reloj adelantado dejaria una "ultima ubicacion" que nunca caduca en el panel de seguimiento. */
    @Test
    @WithMockUser
    void unPuntoConFechaFuturaRechazaElLote() {
        String usuario = usuarioDeUnAcopiador();
        Instant futuro = Instant.now().plus(Duration.ofHours(3));

        assertThatThrownBy(() ->
                        service.registrarLote(usuario, new UbicacionLoteRequest(List.of(punto(-15.61, futuro)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("hora del dispositivo");
    }

    @Test
    @WithMockUser
    void unUsuarioSinAcopiadorNoPuedeReportarRecorrido() {
        assertThatThrownBy(() -> service.registrarLote(
                        "admin@huata.pe",
                        new UbicacionLoteRequest(List.of(punto(-15.61, Instant.now().minusSeconds(60))))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @WithMockUser
    void elHistorialDevuelveElRecorridoEnOrdenParaDibujarloEnLaWeb() {
        String usuario = usuarioDeUnAcopiador();
        Instant base = momentoAislado(240);
        service.registrarLote(
                usuario,
                new UbicacionLoteRequest(List.of(
                        punto(-15.63, base.plusSeconds(120)),
                        punto(-15.61, base),
                        punto(-15.62, base.plusSeconds(60)))));

        List<UbicacionResponse> recorrido =
                service.historial(acopiadorDe(usuario), base.minusSeconds(1), base.plusSeconds(121));

        assertThat(recorrido).extracting(UbicacionResponse::latitud).containsExactly(-15.61, -15.62, -15.63);
    }

    /**
     * La ultima ubicacion de cada acopiador sale de una consulta por acopiador (V30), no de cargar la
     * tabla entera y filtrar en memoria. Lo que se fija es el resultado: uno por acopiador, el mas
     * reciente, aunque haya muchos puntos anteriores.
     */
    @Test
    @WithMockUser
    void lasUltimasUbicacionesTraenElPuntoMasRecienteDeCadaAcopiador() {
        String usuario = usuarioDeUnAcopiador();
        Long acopiadorId = acopiadorDe(usuario);
        Instant masReciente = Instant.now().minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        service.registrarLote(usuario, new UbicacionLoteRequest(List.of(
                punto(-15.601, masReciente.minus(20, ChronoUnit.SECONDS)),
                punto(-15.602, masReciente.minus(10, ChronoUnit.SECONDS)),
                punto(-15.603, masReciente))));

        List<UbicacionResponse> ultimas = service.ultimas();

        assertThat(ultimas).extracting(UbicacionResponse::acopiadorId).doesNotHaveDuplicates();
        assertThat(ultimas)
                .filteredOn(u -> u.acopiadorId().equals(acopiadorId))
                .singleElement()
                .satisfies(u -> assertThat(u.latitud()).isEqualTo(-15.603));
    }
}
