package org.ramiro.huata;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.ramiro.huata.usuarios.CatalogoVistas;
import org.ramiro.huata.usuarios.MatrizPermisos;
import org.ramiro.huata.usuarios.Rol;
import org.ramiro.huata.usuarios.RolRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifica que las migraciones de Flyway y el mapeo JPA no se separen.
 *
 * <p>Es el unico test que ejercita el esquema real: el resto corre sobre H2 con ddl-auto=create-drop
 * (ver application-test.properties), donde las migraciones ni siquiera se ejecutan.
 *
 * <p>La comprobacion importante no es una asercion, es que el contexto arranque: con
 * ddl-auto=validate, Hibernate compara cada entidad contra el esquema consolidado B46 y aborta
 * el arranque si falta una tabla, sobra una columna o cambio un tipo. Es decir: si alguien toca una
 * entidad y se olvida de escribir la migracion, este test se pone en rojo.
 *
 * <p>Corre en la fase de integracion (maven-failsafe, sufijo IT), no en {@code mvn test}, para que
 * la compilacion siga funcionando en una maquina sin Docker.
 */
@Testcontainers
@SpringBootTest
@TestPropertySource(properties = {
    // El perfil "test" apunta a H2; aqui mandan estas tres.
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    // Sin datos DEMO: lo que se valida es el esquema migrado, no el seeder.
    "huata.seed.demo-data=false"
})
class MigracionesFlywayIT {

    /**
     * Misma version mayor que el docker-compose (postgres:17-alpine). Si aqui se probara contra otra
     * version que en produccion, el test dejaria de decir lo que dice que dice.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RolRepository rolRepository;

    @Test
    void elEsquemaMigradoCoincideConLasEntidades() {
        // Arrange: si el contexto llego hasta aqui, ddl-auto=validate ya comparo entidades y esquema.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Act
        Set<String> tablas = new TreeSet<>(jdbc.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = 'public' and table_name <> 'flyway_schema_history'",
                String.class));

        // Assert: se comparan NOMBRES y no un conteo. Con un numero la asercion envejece en
        // silencio —asi quedo en 23 cuando V7 anadio medicion_instrumento— y cuando por fin falla
        // no dice que tabla sobra o falta.
        //
        // Aun asi la lista se quedo 23 tablas atras (V13 produccion/compras, V14 ubicaciones, V19-V20
        // poligonos, V21-V23 contabilidad...) y nadie lo vio: este test corre en failsafe ("mvn
        // verify"), no en "mvn test", asi que una compilacion normal lo salta. Si se toca el
        // esquema, hay que correr "mvn verify" —no basta con que "mvn test" este en verde.
        assertThat(tablas)
                .containsExactlyInAnyOrder(
                        "acopiadores", "ajustes_inventario", "ajustes_liquidacion", "asiento_lineas", "asientos_contables", "asignaciones_ruta",
                        "cierres_acopio", "codigos_verificacion", "compra_items", "compras", "conciliaciones_ruta",
                        "controles_calidad", "conversaciones_proveedor", "evidencias_calidad", "incidencias_calidad", "liquidacion_detalles",
                        "liquidaciones", "lote_consumo", "lote_mano_obra", "lotes_queso", "materias_primas",
                        "mensajes_proveedor",
                        "medicion_instrumento", "modulos", "movimientos_inventario", "notificaciones", "parametros_calidad",
                        "parametros_negocio", "permisos", "plan_cuentas", "productor_ubicaciones", "productores", "productos",
                        "proveedores_compra", "puntos_establecidos", "quesos", "receta_items", "recetas_queso", "recojos", "registros_auditoria",
                        "rol_permisos", "roles", "ruta_puntos", "rutas", "solicitudes_cambio_zona", "tarifas_leche",
                        "tarifas_queso", "trabajadores_produccion", "trazado_acopiador_puntos", "trazados_acopiador",
                        "ubicaciones_gps", "usuario_permisos", "usuario_roles", "usuarios",
                        "ventas_queso", "vista_acciones", "vistas", "zona_poligono_puntos", "zona_poligono_versiones", "zona_poligonos");
    }

    @Test
    void flywayRegistraLaMigracionConsolidadaSinErrores() {
        // Arrange
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Act
        Integer fallidas = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        List<String> aplicadas = jdbc.queryForList(
                "select version || ':' || script from flyway_schema_history where success = true"
                        + " order by installed_rank",
                String.class);

        // Assert
        assertThat(fallidas).isZero();
        // Una base nueva aplica primero la consolidada (B46 ya incluye V1..V50) y despues solo las
        // migraciones posteriores (V51, V52, ...), como indica la cabecera de B46.
        assertThat(aplicadas).first().isEqualTo("46:B46__esquema_consolidado.sql");
        assertThat(aplicadas.subList(1, aplicadas.size()))
                .allSatisfy(m -> assertThat(Integer.parseInt(m.substring(0, m.indexOf(':')))).isGreaterThan(50));
    }

    @Test
    void losRolesBaseVienenDeLaMigracionYNoDelSeederDemo() {
        // Arrange: el contexto arranco con huata.seed.demo-data=false, asi que el seeder no corrio.

        // Act
        long total = rolRepository.count();

        // Assert: los roles existen igual, porque los siembran las migraciones.
        //
        // Se compara contra MatrizPermisos, que es la fuente de verdad declarada en Java, y no
        // contra un numero: el literal decia 8 desde V8 y la base ya iba por 11 (PROVEEDOR,
        // PRODUCTOR_QUESO, SECRETARIA...), asi que el test solo servia para romperse el dia que
        // alguien por fin ejecutara "mvn verify". Atado asi, sumar un rol no vuelve a envejecerlo.
        assertThat(total).isEqualTo(MatrizPermisos.POR_ROL.size());
        assertThat(MatrizPermisos.POR_ROL.keySet())
                .allSatisfy(nombre -> assertThat(rolRepository.findByNombre(nombre)).isPresent());
        assertThat(rolRepository.findByNombre(Rol.ADMINISTRADOR)).isPresent();
    }

    @Test
    void elCatalogoDePermisosSembradoCoincideConElDeclaradoEnJava() {
        // Arrange
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Act
        Set<String> enBase = new TreeSet<>(jdbc.queryForList("select codigo from permisos", String.class));

        // Assert
        assertThat(enBase).isEqualTo(new TreeSet<>(MatrizPermisos.CATALOGO.keySet()));
    }

    /**
     * La matriz rol -> permiso esta escrita dos veces: en V8 (para dev, prod y este test) y en
     * MatrizPermisos (para el perfil "test" sobre H2, donde Flyway no corre). La duplicacion solo es
     * aceptable si no puede derivar en silencio; esto es lo que lo impide.
     */
    @Test
    void laMatrizRolPermisoDeLaMigracionCoincideConLaDeclaradaEnJava() {
        // Arrange
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Act
        Map<String, Set<String>> enBase = jdbc
                .queryForList(
                        "select r.nombre as rol, p.codigo as permiso from rol_permisos rp "
                                + "join roles r on r.id = rp.rol_id "
                                + "join permisos p on p.id = rp.permiso_id")
                .stream()
                .collect(Collectors.groupingBy(
                        fila -> (String) fila.get("rol"),
                        Collectors.mapping(fila -> (String) fila.get("permiso"), Collectors.toCollection(TreeSet::new))));

        // Assert: se compara rol por rol para que el fallo diga cual difiere, no solo que difieren.
        MatrizPermisos.POR_ROL.forEach((rol, esperados) -> assertThat(enBase.getOrDefault(rol, Set.of()))
                .as("permisos del rol %s", rol)
                .isEqualTo(new TreeSet<>(esperados)));
        assertThat(enBase.keySet()).isSubsetOf(MatrizPermisos.POR_ROL.keySet());
    }
    /** V46 siembra roles con etiqueta; deben ser las mismas que declara MatrizPermisos.ROLES. */
    @Test
    void lasEtiquetasDeLosRolesPredefinidosCoincidenConLasDeclaradasEnJava() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Map<String, String> enBase = jdbc.queryForList("select nombre, etiqueta from roles where sistema").stream()
                .collect(Collectors.toMap(f -> (String) f.get("nombre"), f -> (String) f.get("etiqueta")));

        Map<String, String> enJava = MatrizPermisos.ROLES.stream()
                .collect(Collectors.toMap(MatrizPermisos.DefinicionRol::codigo, MatrizPermisos.DefinicionRol::etiqueta));
        assertThat(enBase).isEqualTo(enJava);
    }

    /**
     * El catalogo de vistas esta escrito dos veces, en V46 y en CatalogoVistas (para H2). Igual que la
     * matriz de permisos, la duplicacion solo es aceptable si no puede derivar en silencio.
     */
    @Test
    void elCatalogoDeVistasDeLaMigracionCoincideConElDeclaradoEnJava() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Set<String> enBase = new TreeSet<>(jdbc.queryForList(
                "select v.codigo || '|' || m.codigo || '|' || coalesce(v.ruta, '-') || '|' || p.codigo || '|' || pa.codigo"
                        + " || '|' || va.etiqueta from vista_acciones va"
                        + " join vistas v on v.id = va.vista_id join modulos m on m.id = v.modulo_id"
                        + " join permisos p on p.id = v.permiso_acceso_id join permisos pa on pa.id = va.permiso_id",
                String.class));

        Set<String> enJava = CatalogoVistas.VISTAS.stream()
                .flatMap(v -> v.acciones().stream().map(a -> v.codigo() + "|" + v.modulo() + "|"
                        + (v.ruta() == null ? "-" : v.ruta()) + "|" + v.permisoAcceso() + "|" + a.permiso() + "|"
                        + a.etiqueta()))
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(enBase).isEqualTo(enJava);
    }
}
