package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.parametros.ParametroNegocio;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorUbicacion;
import org.ramiro.huata.productores.ProductorUbicacionRepository;
import org.ramiro.huata.productores.TipoUbicacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pedido del cliente (2026-09-24, ver CLAUDE.md): "el gps no es seguro", asi que un recojo lejos de
 * la ubicacion registrada del proveedor no se rechaza -- se guarda igual, pero queda marcado
 * (RECOJO_DISTANCIA_MAXIMA_METROS, 200m por defecto) para que calidad/administracion lo revise.
 *
 * <p>Va sin {@code @Transactional} a proposito en los casos que dependen de {@link
 * org.ramiro.huata.productores.ProductorUbicacionService#ultimasPorProductor}: esa consulta no
 * necesita una sesion abierta para nada especial, pero se prefiere probar el camino real
 * (guardar -> leer en otra consulta) y no uno que una transaccion de prueba pudiera disimular, el
 * mismo criterio ya aplicado en {@code ReordenamientoPorCercaniaTests}.
 */
@SpringBootTest
@WithMockUser(username = "calidad.demo@huata.pe")
class RecojoDistanciaGpsTests {

    // Un recojo en ruta exige el acopiador que lo hizo (RecojoService, 2026-10-14).
    @org.springframework.beans.factory.annotation.Autowired
    private org.ramiro.huata.acopiadores.AcopiadorRepository acopiadoresDePrueba;

    private Long acopiadorDePrueba() {
        return acopiadoresDePrueba.findByActivoTrueOrderByNombresAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    var a = new org.ramiro.huata.acopiadores.Acopiador();
                    a.setNombres("Acopiador");
                    a.setApellidos("De prueba");
                    return acopiadoresDePrueba.save(a);
                })
                .getId();
    }


    /** 1 grado de latitud son ~111 320 m: offsets pequenos alcanzan para separar "cerca" de "lejos". */
    private static final double METROS_POR_GRADO_LAT = 111_320.0;

    @Autowired
    private RecojoService recojoService;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private ProductorUbicacionRepository ubicacionRepository;

    @Autowired
    private ParametroNegocioRepository parametroNegocioRepository;

    private Productor proveedor;
    private Long ubicacionId;
    private Long recojoId;

    private static final double LAT_PROVEEDOR = -15.5000;
    private static final double LON_PROVEEDOR = -70.1000;

    @BeforeEach
    void escenario() {
        proveedor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .findFirst()
                .orElseThrow();

        ProductorUbicacion ubicacion = new ProductorUbicacion();
        ubicacion.setProductor(proveedor);
        ubicacion.setLatitud(LAT_PROVEEDOR);
        ubicacion.setLongitud(LON_PROVEEDOR);
        ubicacion.setTipo(TipoUbicacion.CASA);
        ubicacion.setFecha(LocalDate.now());
        ubicacionId = ubicacionRepository.save(ubicacion).getId();
    }

    @AfterEach
    void limpiar() {
        if (recojoId != null) {
            recojoRepository.deleteById(recojoId);
        }
        if (ubicacionId != null) {
            ubicacionRepository.deleteById(ubicacionId);
        }
        parametroNegocioRepository.findByClave("RECOJO_DISTANCIA_MAXIMA_METROS").ifPresent(parametroNegocioRepository::delete);
    }

    /**
     * NO_ENTREGADO y no ACOPIADO: la regla "una entrega diaria por proveedor" no aplica a un
     * NO_ENTREGADO (RecojoService.registrar), asi que estas pruebas -- que registran varios recojos
     * del mismo proveedor en el mismo dia -- no chocan con esa regla ni dependen de un dia libre en
     * los ~14 dias de historial que siembra DemoDataSeeder. La distancia se calcula igual sin
     * importar estadoVisita.
     */
    private RecojoRequest peticion(Double lat, Double lon) {
        return new RecojoRequest(
                UUID.randomUUID().toString(),
                proveedor.getId(),
                acopiadorDePrueba(),
                null,
                Instant.now(),
                0.0,
                null,
                null,
                null,
                null,
                null,
                null,
                EstadoVisita.NO_ENTREGADO,
                "Prueba de distancia GPS",
                lat,
                lon,
                null);
    }

    /** El parametro no existe en el perfil test (no corre Flyway); se crea igual que Configuracion lo haria. */
    private void fijarLimiteMetros(String valor) {
        ParametroNegocio parametro =
                parametroNegocioRepository.findByClave("RECOJO_DISTANCIA_MAXIMA_METROS").orElseGet(ParametroNegocio::new);
        parametro.setClave("RECOJO_DISTANCIA_MAXIMA_METROS");
        parametro.setValor(valor);
        parametro.setDescripcion("Prueba");
        parametroNegocioRepository.save(parametro);
    }

    @Test
    void sinGpsDelAcopiadorNoQuedaMarcado() {
        Recojo r = recojoService.registrar(peticion(null, null));
        recojoId = r.getId();

        assertThat(r.getDistanciaProveedorM()).isNull();
        assertThat(r.isGpsFueraDeRango()).isFalse();
    }

    @Test
    void sinUbicacionDelProveedorNoQuedaMarcado() {
        // Proveedor sin ubicacion registrada: se borran TODAS las suyas, no solo la de este
        // escenario. Otras clases de prueba no transaccionales (ProductorUbicacionServiceTests) dejan
        // ubicaciones reales para el primer proveedor activo; no limpiarlas aqui haria que esta
        // prueba dependiera del orden en que corren las clases del modulo.
        ubicacionRepository.deleteAll(ubicacionRepository.findByProductor_IdOrderByFechaDesc(proveedor.getId()));
        ubicacionId = null;

        Recojo r = recojoService.registrar(peticion(LAT_PROVEEDOR, LON_PROVEEDOR));
        recojoId = r.getId();

        assertThat(r.getDistanciaProveedorM()).isNull();
        assertThat(r.isGpsFueraDeRango()).isFalse();
    }

    @Test
    void dentroDelLimiteNoQuedaMarcado() {
        // ~10m al norte del proveedor: muy por debajo del limite por defecto (200m).
        double latCerca = LAT_PROVEEDOR + (10.0 / METROS_POR_GRADO_LAT);

        Recojo r = recojoService.registrar(peticion(latCerca, LON_PROVEEDOR));
        recojoId = r.getId();

        assertThat(r.getDistanciaProveedorM()).isNotNull().isCloseTo(10.0, within(2.0));
        assertThat(r.isGpsFueraDeRango()).isFalse();
    }

    @Test
    void fueraDelLimiteSeGuardaIgualPeroQuedaMarcadoParaRevision() {
        // ~500m al norte del proveedor: por encima del limite por defecto (200m).
        double latLejos = LAT_PROVEEDOR + (500.0 / METROS_POR_GRADO_LAT);

        Recojo r = recojoService.registrar(peticion(latLejos, LON_PROVEEDOR));
        recojoId = r.getId();

        // El recojo se guarda igual: un GPS impreciso o un pin de proveedor mal puesto no debe borrar
        // una entrega real ya pagable.
        assertThat(r.getId()).isNotNull();
        assertThat(recojoRepository.findById(r.getId())).isPresent();
        assertThat(r.getDistanciaProveedorM()).isNotNull().isCloseTo(500.0, within(5.0));
        assertThat(r.isGpsFueraDeRango()).isTrue();
    }

    @Test
    void elLimiteEsConfigurablePorParametro() {
        fijarLimiteMetros("1000");

        // Los mismos ~500m que en la prueba anterior marcaban "fuera de rango" con el limite por
        // defecto (200m) ahora quedan dentro del limite configurado (1000m).
        double lat500m = LAT_PROVEEDOR + (500.0 / METROS_POR_GRADO_LAT);
        Recojo r = recojoService.registrar(peticion(lat500m, LON_PROVEEDOR));
        recojoId = r.getId();

        assertThat(r.isGpsFueraDeRango()).isFalse();
    }

    @Test
    void unParametroInvalidoUsaElValorPorDefecto() {
        fijarLimiteMetros("no-es-un-numero");

        double latLejos = LAT_PROVEEDOR + (500.0 / METROS_POR_GRADO_LAT);
        Recojo r = recojoService.registrar(peticion(latLejos, LON_PROVEEDOR));
        recojoId = r.getId();

        // 500m sigue superando el valor por defecto (200m) al que se cae si el parametro es invalido.
        assertThat(r.isGpsFueraDeRango()).isTrue();
    }
}
