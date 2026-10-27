package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * "Pintar ruta del dia" contra PostgreSQL real, no contra H2.
 *
 * <p><b>Por que existe ademas de {@code TrazadoAcopiadorServiceTests}.</b> El resto de las pruebas
 * corre sobre H2 con {@code ddl-auto=create-drop}: ahi no existen ni los indices unicos parciales ni
 * los CHECK de V27, y H2 tolera bindings que PostgreSQL rechaza. Es decir, las garantias en las que
 * se apoya el diseno —un trazado por acopiador y dia, un solo predeterminado, y que un DIA no pueda
 * quedarse sin fecha— no las prueba ninguna prueba unitaria. Aqui si.
 *
 * <p>Corre en failsafe ({@code mvn verify}), no en {@code mvn test}: necesita Docker.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
    // El perfil "test" apunta a H2; aqui manda el contenedor, con el esquema migrado y validado.
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    // Con datos DEMO: hacen falta un acopiador con cuenta y una ruta con zona para poder pintar.
    "huata.seed.demo-data=true"
})
class TrazadoAcopiadorPostgresIT {

    /** Misma version mayor que el docker-compose (postgres:17-alpine), igual que MigracionesFlywayIT. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final LocalDate DIA = LocalDate.of(2026, 2, 10);

    @Autowired
    private TrazadoAcopiadorService service;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    @Autowired
    private DataSource dataSource;

    private String usuarioDeUnAcopiador() {
        return acopiadorRepository.findAll().stream()
                .filter(Acopiador::isActivo)
                .filter(a -> a.getUsuario() != null && a.getRuta() != null)
                .map(a -> a.getUsuario().getUsername())
                .findFirst()
                .orElseThrow();
    }

    private static List<TrazadoPuntoRequest> trazo(int puntos) {
        return java.util.stream.IntStream.range(0, puntos)
                .mapToObj(i -> new TrazadoPuntoRequest(i, -15.65 - i * 0.001, -69.98 - i * 0.001))
                .toList();
    }

    /**
     * El camino completo sobre PostgreSQL: pintar, listar por fecha, predeterminar y listar los
     * predeterminados.
     *
     * <p>Lo que de verdad se vigila aqui es la consulta de predeterminados. Antes filtraba con
     * {@code (:fecha is null or t.fecha = :fecha)} y se llamaba con null: H2 lo acepta, y PostgreSQL
     * puede responder "could not determine data type of parameter" porque tiene que inferir el tipo de
     * un bind sin valor. Habria sido un 500 que solo aparece desplegado.
     */
    @Test
    @Transactional
    void elCaminoCompletoFuncionaSobrePostgres() {
        String usuario = usuarioDeUnAcopiador();

        var pintado = service.guardarDelDia(usuario, new TrazadoDiaRequest(DIA, false, "Camino cerrado", trazo(4)));
        assertThat(pintado.puntos()).hasSize(4);

        assertThat(service.pintadosDelDia(DIA))
                .anySatisfy(t -> assertThat(t.id()).isEqualTo(pintado.id()));

        var predeterminado = service.predeterminar(pintado.id(), "admin@huata.pe");
        assertThat(predeterminado.tipo()).isEqualTo(TipoTrazado.PREDETERMINADO);
        assertThat(predeterminado.fecha()).isNull();

        // La consulta sin filtro de fecha: es la que rompia.
        assertThat(service.predeterminados())
                .anySatisfy(t -> assertThat(t.id()).isEqualTo(predeterminado.id()));

        // Y el que no tiene trazado propio sigue viendo el de su ruta como respaldo.
        assertThat(service.miTrazado(usuario, DIA).predeterminado()).isNotNull();
    }

    /**
     * El indice unico parcial de V27, que en H2 no existe. Es lo que garantiza que volver a pintar
     * corrija el dibujo del dia en vez de crear una segunda fila que dejaria a la pantalla eligiendo
     * al azar cual es "el" recorrido de ese acopiador.
     */
    @Test
    void laBaseNoAdmiteDosTrazadosDelMismoAcopiadorYDia() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long acopiadorId = acopiadorRepository.findAll().stream()
                .filter(a -> a.getRuta() != null)
                .findFirst()
                .orElseThrow()
                .getId();
        Long rutaId = acopiadorRepository.findById(acopiadorId).orElseThrow().getRuta().getId();

        jdbc.update(
                "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, fecha, sigue_ruta_designada, activo)"
                        + " values (?, ?, 'DIA', date '2026-03-01', true, true)",
                acopiadorId,
                rutaId);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, fecha, sigue_ruta_designada, activo)"
                                + " values (?, ?, 'DIA', date '2026-03-01', true, true)",
                        acopiadorId,
                        rutaId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("delete from trazados_acopiador where acopiador_id = ? and fecha = date '2026-03-01'", acopiadorId);
    }

    /**
     * El otro indice parcial: un solo predeterminado por acopiador. Sin el, "asignar como
     * predeterminada" dos veces dejaria dos recorridos habituales y la app no sabria cual usar.
     */
    @Test
    void laBaseNoAdmiteDosPredeterminadosDelMismoAcopiador() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long acopiadorId = acopiadorRepository.findAll().stream()
                .filter(a -> a.getRuta() != null)
                .findFirst()
                .orElseThrow()
                .getId();
        Long rutaId = acopiadorRepository.findById(acopiadorId).orElseThrow().getRuta().getId();
        jdbc.update("delete from trazado_acopiador_puntos where trazado_id in"
                + " (select id from trazados_acopiador where acopiador_id = ? and tipo = 'PREDETERMINADO')", acopiadorId);
        jdbc.update("delete from trazados_acopiador where acopiador_id = ? and tipo = 'PREDETERMINADO'", acopiadorId);

        jdbc.update(
                "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, sigue_ruta_designada, activo)"
                        + " values (?, ?, 'PREDETERMINADO', true, true)",
                acopiadorId,
                rutaId);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, sigue_ruta_designada, activo)"
                                + " values (?, ?, 'PREDETERMINADO', true, true)",
                        acopiadorId,
                        rutaId))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("delete from trazados_acopiador where acopiador_id = ? and tipo = 'PREDETERMINADO'", acopiadorId);
    }

    /**
     * El CHECK que sostiene que {@code fecha} sea nula solo en los predeterminados. Es lo que hace
     * seguro tener una columna opcional obligatoria para un tipo: la base no admite una fila a medio
     * camino entre los dos, aunque un dia alguien escriba el servicio de otra forma.
     */
    @Test
    void laBaseRechazaUnTrazadoDelDiaSinFechaYUnPredeterminadoConFecha() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Long acopiadorId = acopiadorRepository.findAll().stream()
                .filter(a -> a.getRuta() != null)
                .findFirst()
                .orElseThrow()
                .getId();
        Long rutaId = acopiadorRepository.findById(acopiadorId).orElseThrow().getRuta().getId();

        assertThatThrownBy(() -> jdbc.update(
                        "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, fecha, sigue_ruta_designada, activo)"
                                + " values (?, ?, 'DIA', null, true, true)",
                        acopiadorId,
                        rutaId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                        "insert into trazados_acopiador (acopiador_id, ruta_id, tipo, fecha, sigue_ruta_designada, activo)"
                                + " values (?, ?, 'PREDETERMINADO', date '2026-03-02', true, true)",
                        acopiadorId,
                        rutaId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
