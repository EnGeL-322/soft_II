package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.PuntoEstablecido;
import org.ramiro.huata.rutas.PuntoEstablecidoRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Opcion B1 acordada con el cliente (2026-09-30): la regla de cercania contra
 * {@code ProductorUbicacion} (ver {@link RecojoDistanciaGpsTests}) se mantiene sin cambios, y ADEMAS
 * -- cuando el proveedor tiene un "punto establecido" asociado -- se compara tambien contra ese punto
 * con la misma logica (GeoUtils.distanciaMetros + RECOJO_DISTANCIA_MAXIMA_METROS). Si cualquiera de
 * las dos distancias supera el limite, {@code gpsFueraDeRango} queda en true; el backend nunca
 * rechaza el recojo por esto, solo lo marca.
 *
 * <p>Sin {@code @Transactional} por el mismo motivo que {@code RecojoDistanciaGpsTests}: se prueba el
 * camino real (guardar el punto establecido -> leerlo en otra consulta), no uno que una sesion de
 * prueba abierta pudiera disimular.
 */
@SpringBootTest
@WithMockUser(username = "calidad.demo@huata.pe")
class RecojoDistanciaPuntoEstablecidoTests {

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


    private static final double METROS_POR_GRADO_LAT = 111_320.0;
    private static final double LAT_PUNTO = -15.4000;
    private static final double LON_PUNTO = -70.2000;

    @Autowired
    private RecojoService recojoService;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private PuntoEstablecidoRepository puntoEstablecidoRepository;

    private Productor proveedor;
    private Long puntoId;
    private Long recojoId;

    @BeforeEach
    void escenario() {
        proveedor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(p -> p.getRuta() != null)
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void limpiar() {
        if (recojoId != null) recojoRepository.deleteById(recojoId);
        if (puntoId != null) puntoEstablecidoRepository.deleteById(puntoId);
    }

    private void crearPuntoParaElProveedor() {
        Ruta ruta = proveedor.getRuta();
        PuntoEstablecido punto = new PuntoEstablecido();
        punto.setNombre("Punto de prueba");
        punto.setLatitud(LAT_PUNTO);
        punto.setLongitud(LON_PUNTO);
        punto.setRuta(ruta);
        punto.setProveedor(proveedor);
        puntoId = puntoEstablecidoRepository.save(punto).getId();
    }

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
                "Prueba de distancia al punto establecido",
                lat,
                lon,
                null);
    }

    @Test
    void sinPuntoEstablecidoNoQuedaMarcadoPorEsteMotivo() {
        Recojo r = recojoService.registrar(peticion(LAT_PUNTO, LON_PUNTO));
        recojoId = r.getId();

        assertThat(r.getDistanciaPuntoEstablecidoM()).isNull();
    }

    @Test
    void dentroDelLimiteDelPuntoEstablecidoNoQuedaMarcado() {
        crearPuntoParaElProveedor();
        double latCerca = LAT_PUNTO + (10.0 / METROS_POR_GRADO_LAT);

        Recojo r = recojoService.registrar(peticion(latCerca, LON_PUNTO));
        recojoId = r.getId();

        assertThat(r.getDistanciaPuntoEstablecidoM()).isNotNull().isCloseTo(10.0, within(2.0));
        assertThat(r.isGpsFueraDeRango()).isFalse();
    }

    @Test
    void fueraDelLimiteDelPuntoEstablecidoSeGuardaIgualPeroQuedaMarcado() {
        crearPuntoParaElProveedor();
        double latLejos = LAT_PUNTO + (500.0 / METROS_POR_GRADO_LAT);

        Recojo r = recojoService.registrar(peticion(latLejos, LON_PUNTO));
        recojoId = r.getId();

        assertThat(recojoRepository.findById(r.getId())).isPresent();
        assertThat(r.getDistanciaPuntoEstablecidoM()).isNotNull().isCloseTo(500.0, within(5.0));
        assertThat(r.isGpsFueraDeRango()).isTrue();
    }

    @Test
    void sinGpsDelAcopiadorNoSeCalculaNiSiquieraConPuntoEstablecido() {
        crearPuntoParaElProveedor();

        Recojo r = recojoService.registrar(peticion(null, null));
        recojoId = r.getId();

        assertThat(r.getDistanciaPuntoEstablecidoM()).isNull();
        assertThat(r.isGpsFueraDeRango()).isFalse();
    }
}
