package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.EstadoProductor;
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
 * "Punto de acopio dinámico" + reordenamiento por cercanía (vecino más cercano): cuando el
 * administrador decide un nuevo destino, la ruta del acopiador debe reordenarse para que el
 * recorrido sea más corto, no quedarse en el orden manual original.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ReordenamientoPorCercaniaTests {

    @Autowired
    private RutaService rutaService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private ProductorUbicacionRepository productorUbicacionRepository;

    private Ruta ruta;

    @BeforeEach
    void escenario() {
        ruta = rutaService.crearRuta(new RutaRequest("Ruta cercania " + System.nanoTime(), "Zona cercania", null));
    }

    private Productor proveedorEn(double lat, double lon, int ordenInicial) {
        Productor p = new Productor();
        p.setCodigo("REORD-" + UUID.randomUUID().toString().substring(0, 8));
        p.setNombres("Prov");
        p.setApellidos(String.valueOf(ordenInicial));
        p.setEstado(EstadoProductor.ACTIVO);
        p.setRuta(ruta);
        p.setOrdenEnRuta(ordenInicial);
        p = productorRepository.save(p);

        ProductorUbicacion ubicacion = new ProductorUbicacion();
        ubicacion.setProductor(p);
        ubicacion.setLatitud(lat);
        ubicacion.setLongitud(lon);
        ubicacion.setTipo(TipoUbicacion.CASA);
        ubicacion.setFecha(LocalDate.now());
        productorUbicacionRepository.save(ubicacion);
        return p;
    }

    /**
     * Tres proveedores en fila (A, B, C, de oeste a este). Con orden manual invertido (C, B, A) pero
     * origen en el extremo oeste, el vecino más cercano debe visitar A, luego B, luego C: el orden
     * geográfico, no el manual.
     */
    @Test
    void reordenaPorElVecinoMasCercanoDesdeElOrigenDado() {
        Productor c = proveedorEn(-15.000, -70.000, 1);
        Productor b = proveedorEn(-15.000, -70.010, 2);
        Productor a = proveedorEn(-15.000, -70.020, 3);

        List<ProveedorDeRutaResponse> resultado =
                rutaService.reordenarPorCercania(ruta.getId(), new ReordenarRutaRequest(-15.000, -70.025));

        assertThat(resultado).extracting(ProveedorDeRutaResponse::id)
                .containsExactly(a.getId(), b.getId(), c.getId());
        assertThat(rutaService.proveedoresDeRuta(ruta.getId()))
                .extracting(ProveedorDeRutaResponse::id)
                .containsExactly(a.getId(), b.getId(), c.getId());
    }

    /** Sin origen explícito, se usa el primer punto guardado del trazo de la ruta. */
    @Test
    void sinOrigenExplicitoUsaElPrimerPuntoDelTrazo() {
        Productor lejano = proveedorEn(-15.000, -70.100, 1);
        Productor cercano = proveedorEn(-15.000, -70.001, 2);
        rutaService.reemplazarPuntos(
                ruta.getId(), new RutaPuntosRequest(List.of(new RutaPuntoRequest(1, -15.000, -70.000))));

        List<ProveedorDeRutaResponse> resultado = rutaService.reordenarPorCercania(ruta.getId(), null);

        assertThat(resultado).extracting(ProveedorDeRutaResponse::id).containsExactly(cercano.getId(), lejano.getId());
    }

    @Test
    void sinOrigenNiTrazoFallaConMensajeClaro() {
        proveedorEn(-15.0, -70.0, 1);

        assertThatThrownBy(() -> rutaService.reordenarPorCercania(ruta.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no hay");
    }

    /** Un proveedor sin ubicación registrada no se puede ordenar por distancia: queda al final. */
    @Test
    void unProveedorSinUbicacionQuedaAlFinalSinRomperElCalculo() {
        Productor conUbicacion = proveedorEn(-15.000, -70.010, 2);
        Productor sinUbicacion = new Productor();
        sinUbicacion.setCodigo("REORD-SIN-" + UUID.randomUUID().toString().substring(0, 8));
        sinUbicacion.setNombres("Sin");
        sinUbicacion.setApellidos("Ubicacion");
        sinUbicacion.setEstado(EstadoProductor.ACTIVO);
        sinUbicacion.setRuta(ruta);
        sinUbicacion.setOrdenEnRuta(1);
        sinUbicacion = productorRepository.save(sinUbicacion);

        List<ProveedorDeRutaResponse> resultado =
                rutaService.reordenarPorCercania(ruta.getId(), new ReordenarRutaRequest(-15.000, -70.020));

        assertThat(resultado).extracting(ProveedorDeRutaResponse::id)
                .containsExactly(conUbicacion.getId(), sinUbicacion.getId());
    }

    /**
     * agregarPuntoAcopio agrega el punto SIN borrar los demás y reordena a partir de él: el
     * proveedor mas cercano AL PUNTO NUEVO (no al trazo original) debe visitarse primero.
     */
    @Test
    void agregarPuntoDeAcopioSumaAlTrazoYReordena() {
        rutaService.reemplazarPuntos(
                ruta.getId(), new RutaPuntosRequest(List.of(new RutaPuntoRequest(1, -15.000, -70.000))));
        Productor cercaDelNuevoDestino = proveedorEn(-15.000, -70.100, 1);
        Productor lejosDelNuevoDestino = proveedorEn(-15.000, -70.095, 2);

        PuntoAcopioAgregadoResponse resultado =
                rutaService.agregarPuntoAcopio(ruta.getId(), new PuntoAcopioRequest(-15.000, -70.099));

        assertThat(resultado.puntos()).hasSize(2);
        assertThat(resultado.puntos()).extracting(RutaPuntoResponse::secuencia).containsExactly(1, 2);
        assertThat(resultado.proveedores()).extracting(ProveedorDeRutaResponse::id)
                .containsExactly(cercaDelNuevoDestino.getId(), lejosDelNuevoDestino.getId());
    }
}
