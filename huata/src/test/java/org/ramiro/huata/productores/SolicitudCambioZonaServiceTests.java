package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@WithMockUser(username = "admin.demo@huata.pe")
class SolicitudCambioZonaServiceTests {

    @Autowired
    private SolicitudCambioZonaService service;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    /**
     * La regla: se puede pedir una zona que es solo UNA PARTE de la zona compuesta de una ruta
     * ("YASIN" dentro de "FAON/YASIN") y la aprobación igual resuelve esa ruta, dejando al proveedor
     * con la zona completa de la ruta y con el acopiador que la recorre.
     *
     * <p>La ruta compuesta la crea la propia prueba. Antes se buscaba en el padrón DEMO la primera
     * zona que tuviera "/", así que la prueba dependía de cómo estuviera sembrada la demo: al
     * alinear las zonas demo con las zonas reales del cliente (ninguna lleva "/") dejó de encontrar
     * nada y fallaba sin que la regla hubiera cambiado.
     */
    @Test
    void aprobarSincronizaZonaRutaYAcopiador() {
        String zona = "FAON/YASIN PRUEBA " + System.nanoTime();

        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta compuesta de prueba " + System.nanoTime());
        ruta.setZona(zona);
        rutaRepository.save(ruta);

        Acopiador acopiador = new Acopiador();
        acopiador.setNombres("Acopiador");
        acopiador.setApellidos("de la ruta compuesta");
        acopiador.setRuta(ruta);
        acopiadorRepository.save(acopiador);

        Productor productor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .findFirst()
                .orElseThrow();
        productor.setRuta(ruta);
        productor.setZonaPrincipal(zona);
        productorRepository.save(productor);

        String zonaSolicitada = zona.split("/")[1].trim();
        var solicitud = service.crear(
                productor.getId(), new SolicitudCambioZonaRequest(zonaSolicitada.toUpperCase(), "Reasignacion de prueba"));

        service.decidir(solicitud.getId(), new SolicitudCambioZonaDecisionRequest(true, "Aprobada"));

        var actualizado = productorRepository.findById(productor.getId()).orElseThrow();
        assertThat(actualizado.getZonaPrincipal()).isEqualTo(zona);
        assertThat(actualizado.getRuta()).isNotNull();
        assertThat(actualizado.getRuta().getId()).isEqualTo(ruta.getId());
        assertThat(actualizado.getAcopiadorResponsable()).isNotNull();
        assertThat(actualizado.getAcopiadorResponsable().getId()).isEqualTo(acopiador.getId());
    }

    /** El selector de la app ofrece las zonas de rutas activas, sin la del propio proveedor. */
    @Test
    void lasZonasSolicitablesSonLasDeRutasActivasMenosLaPropia() {
        String sufijo = " " + System.nanoTime();
        Ruta propia = ruta("ZONA PROPIA" + sufijo);
        ruta("ZONA DESTINO" + sufijo);
        Ruta inactiva = ruta("ZONA INACTIVA" + sufijo);
        inactiva.setActivo(false);
        rutaRepository.save(inactiva);
        Productor productor = proveedorEn(propia);

        assertThat(service.zonasSolicitables(productor.getId()))
                .contains("ZONA DESTINO" + sufijo)
                .doesNotContain("ZONA PROPIA" + sufijo, "ZONA INACTIVA" + sufijo);
    }

    @Test
    void noSePideUnaZonaQueNoTieneRuta() {
        Productor productor = proveedorEn(ruta("ZONA ORIGEN " + System.nanoTime()));

        assertThatThrownBy(() -> service.crear(
                        productor.getId(), new SolicitudCambioZonaRequest("ZONA INVENTADA " + System.nanoTime(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void noSePideLaZonaEnLaQueYaEsta() {
        String zona = "ZONA ACTUAL " + System.nanoTime();
        Productor productor = proveedorEn(ruta(zona));

        assertThatThrownBy(() -> service.crear(productor.getId(), new SolicitudCambioZonaRequest(zona.toLowerCase(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya pertenece");
    }

    /** Misma regla que el IVR: mientras una espera respuesta no se abre otra. */
    @Test
    void conUnaSolicitudPendienteNoSeCreaOtra() {
        String sufijo = " " + System.nanoTime();
        Productor productor = proveedorEn(ruta("ZONA ORIGEN" + sufijo));
        ruta("ZONA B" + sufijo);
        ruta("ZONA C" + sufijo);
        service.crear(productor.getId(), new SolicitudCambioZonaRequest("ZONA B" + sufijo, null));

        assertThatThrownBy(() -> service.crear(productor.getId(), new SolicitudCambioZonaRequest("ZONA C" + sufijo, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("esperando respuesta");
    }

    private Ruta ruta(String zona) {
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta " + zona);
        ruta.setZona(zona);
        return rutaRepository.save(ruta);
    }

    /** Un proveedor activo sin solicitudes previas, ubicado en la zona de esa ruta. */
    private Productor proveedorEn(Ruta ruta) {
        Productor productor = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(p -> service.deProductor(p.getId()).stream()
                        .noneMatch(s -> s.getEstado() == EstadoSolicitudZona.PENDIENTE))
                .findFirst()
                .orElseThrow();
        productor.setRuta(ruta);
        productor.setZonaPrincipal(ruta.getZona());
        return productorRepository.save(productor);
    }
}
