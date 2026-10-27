package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.parametros.ReglasAcopioService;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.rutas.ZonaPoligono;
import org.ramiro.huata.rutas.ZonaPoligonoPunto;
import org.ramiro.huata.rutas.ZonaPoligonoPuntoRepository;
import org.ramiro.huata.rutas.ZonaPoligonoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La geocerca del acopio (no registrar leche fuera del limite de la zona) se puede apagar para una
 * demostracion sin perder la regla (V57, pedido del cliente 2026-10-25), y vuelve a regir al
 * encenderla.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class GeocercaAcopioTests {

    @Autowired private RecojoService recojoService;
    @Autowired private ReglasAcopioService reglasAcopio;
    @Autowired private RutaRepository rutaRepository;
    @Autowired private ZonaPoligonoRepository poligonos;
    @Autowired private ZonaPoligonoPuntoRepository vertices;
    @Autowired private ProductorRepository productorRepository;
    @Autowired private AcopiadorRepository acopiadorRepository;

    private Ruta ruta;
    private Productor proveedor;
    private Acopiador acopiador;

    @BeforeEach
    void zonaConLimite() {
        String zona = "ZONA GEOCERCA " + System.nanoTime();
        ruta = new Ruta();
        ruta.setNombre("Ruta " + zona);
        ruta.setZona(zona);
        ruta = rutaRepository.save(ruta);

        ZonaPoligono poligono = new ZonaPoligono();
        poligono.setZona(zona);
        poligono = poligonos.save(poligono);
        int secuencia = 1;
        for (double[] v : List.of(
                new double[] {-15.60, -70.10}, new double[] {-15.60, -70.00},
                new double[] {-15.70, -70.00}, new double[] {-15.70, -70.10})) {
            ZonaPoligonoPunto p = new ZonaPoligonoPunto();
            p.setZonaPoligono(poligono);
            p.setSecuencia(secuencia++);
            p.setLatitud(v[0]);
            p.setLongitud(v[1]);
            vertices.save(p);
        }

        acopiador = new Acopiador();
        acopiador.setNombres("Acopiador");
        acopiador.setApellidos("Geocerca");
        acopiador.setRuta(ruta);
        acopiador = acopiadorRepository.save(acopiador);

        proveedor = productorRepository.findAll().stream().filter(Productor::isActivo).findFirst().orElseThrow();
        proveedor.setRuta(ruta);
        proveedor.setZonaPrincipal(zona);
        proveedor.setEstado(EstadoProductor.ACTIVO);
        proveedor = productorRepository.save(proveedor);
    }

    /** Un recojo en ruta con el GPS en Juliaca, lejos del limite de la zona. */
    private Recojo registrarFueraDeLaZona(String dia) {
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(),
                proveedor.getId(),
                acopiador.getId(),
                ruta.getId(),
                Instant.parse(dia + "T12:00:00Z"),
                40.0,
                null,
                FuenteRecojo.RUTA,
                null,
                null,
                null,
                null,
                null,
                null,
                -15.49,
                -70.13,
                null));
    }

    @Test
    void conLaGeocercaEncendidaNoSeRegistraLecheFueraDeLaZona() {
        assertThat(reglasAcopio.vigentes().exigirGeocerca()).as("nace encendida").isTrue();

        assertThatThrownBy(() -> registrarFueraDeLaZona("2035-07-01"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("fuera de la zona");
    }

    @Test
    void apagadaParaUnaDemostracionSePuedeAcopiarYAlEncenderlaVuelveARegir() {
        reglasAcopio.guardar(new ReglasAcopioService.ReglasAcopio(false, false, 200));

        Recojo recojo = registrarFueraDeLaZona("2035-07-02");
        assertThat(recojo.getId()).isNotNull();

        reglasAcopio.guardar(new ReglasAcopioService.ReglasAcopio(true, true, 200));
        assertThatThrownBy(() -> registrarFueraDeLaZona("2035-07-03"))
                .isInstanceOf(BusinessRuleException.class);
    }
}
