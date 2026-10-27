package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Puntos establecidos" (pedido del cliente, 2026-09-30): puntos de referencia que administracion
 * define a mano en el mapa, por ruta, con acopiador y proveedor asociados opcionales.
 *
 * <p>Lo que importa fijar aqui: que desactivar sea baja lógica (reactivar usa cambiarEstado, no editar),
 * que las coordenadas fuera de rango se rechacen también en el
 * servicio (no solo en la anotacion del DTO), y que "lo que ve el acopiador en su app" combine su
 * ruta del dia con los puntos asignados directamente a el, sin duplicar.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "acopiador.demo@huata.pe")
class PuntoEstablecidoServiceTests {

    private static final String USUARIO_ACOPIADOR = "acopiador.demo@huata.pe";

    @Autowired
    private PuntoEstablecidoService service;

    @Autowired
    private PuntoEstablecidoRepository repository;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private org.ramiro.huata.productores.ProductorUbicacionService ubicaciones;

    @Autowired
    private RutaService rutas;

    private Acopiador acopiador;
    private Ruta ruta;
    private Productor proveedor;

    @BeforeEach
    void escenario() {
        acopiador = acopiadorRepository.findByUsuario_UsernameIgnoreCase(USUARIO_ACOPIADOR).orElseThrow();
        ruta = acopiador.getRuta();
        proveedor = productorRepository.findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(ruta.getId())
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private PuntoEstablecidoRequest peticion(String nombre, Double lat, Double lon, Long acopiadorId, Long proveedorId) {
        return new PuntoEstablecidoRequest(nombre, lat, lon, ruta.getId(), acopiadorId, proveedorId, "Prueba");
    }

    @Test
    void alUbicarUnProveedorConPuntoYaTieneUbicacionYEsVisibleEnElSnapshotMovil() {
        PuntoEstablecido creado = service.crear(peticion("Entrega", -15.65, -69.98, null, proveedor.getId()));
        Long id = proveedor.getId();

        assertThat(ubicaciones.ultimasPorProductor(List.of(id)).get(id).getLatitud()).isEqualTo(-15.65);
        assertThat(rutas.proveedoresDeRuta(ruta.getId())).filteredOn(p -> p.id().equals(id))
                .singleElement().satisfies(p -> assertThat(p.latitud()).isEqualTo(-15.65));
        assertThat(service.snapshotDeMiAcopiador(USUARIO_ACOPIADOR).puntos()).extracting(PuntoEstablecidoResponse::id).contains(creado.getId());
    }

    @Test
    void moverElPuntoActualizaLaUbicacionDelProveedorSinCambiarSuIdSeleccionable() {
        PuntoEstablecido creado = service.crear(peticion("Entrega", -15.65, -69.98, null, proveedor.getId()));
        Long id = creado.getId();
        service.actualizar(id, peticion("Entrega", -15.70, -70.00, null, proveedor.getId()));

        assertThat(ubicaciones.ultimasPorProductor(List.of(proveedor.getId())).get(proveedor.getId()).getLatitud()).isEqualTo(-15.70);
        assertThat(service.deMiAcopiador(USUARIO_ACOPIADOR)).filteredOn(p -> p.id().equals(id))
                .singleElement().satisfies(p -> assertThat(p.latitud()).isEqualTo(-15.70));
    }

    @Test
    void noAceptaUnPuntoSinUbicacionNiCoordenadasNoFinitas() {
        assertThatThrownBy(() -> service.crear(peticion("Sin ubicar", null, -70.0, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.crear(peticion("No finita", Double.NaN, -70.0, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void creaUnPuntoYApareceEnElListadoDeSuRuta() {
        PuntoEstablecido creado = service.crear(peticion("Cruce principal", -15.65, -69.98, null, null));

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getCreatedBy()).isEqualTo(USUARIO_ACOPIADOR);
        assertThat(service.listar(ruta.getId())).extracting(PuntoEstablecidoResponse::id).contains(creado.getId());
    }

    @Test
    void rechazaLatitudFueraDeRangoAunqueLlegueSinPasarPorElControlador() {
        assertThatThrownBy(() -> service.crear(peticion("Fuera de rango", 95.0, -69.98, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rechazaLongitudFueraDeRangoAunqueLlegueSinPasarPorElControlador() {
        assertThatThrownBy(() -> service.crear(peticion("Fuera de rango", -15.65, 200.0, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void editarCambiaLosDatosYQuedaTrazado() {
        PuntoEstablecido creado = service.crear(peticion("Nombre original", -15.65, -69.98, null, null));

        PuntoEstablecido editado = service.actualizar(
                creado.getId(), peticion("Nombre corregido", -15.66, -69.99, null, null));

        assertThat(editado.getNombre()).isEqualTo("Nombre corregido");
        assertThat(editado.getLatitud()).isEqualTo(-15.66);
    }

    @Test
    void desactivarEsBajaLogicaYYaNoApareceEnElListado() {
        PuntoEstablecido creado = service.crear(peticion("A desactivar", -15.65, -69.98, null, null));

        service.desactivar(creado.getId());

        assertThat(service.listar(ruta.getId())).extracting(PuntoEstablecidoResponse::id)
                .doesNotContain(creado.getId());
        // Baja logica: la fila sigue existiendo (no se borra fisicamente), igual que el resto del
        // dominio (BaseEntity.activo).
        assertThat(repository.findById(creado.getId())).isPresent();
    }

    @Test
    void noPermiteOperarSobreUnPuntoYaDesactivado() {
        PuntoEstablecido creado = service.crear(peticion("A desactivar", -15.65, -69.98, null, null));
        service.desactivar(creado.getId());

        assertThatThrownBy(() -> service.actualizar(creado.getId(), peticion("x", -15.65, -69.98, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void elAcopiadorVeLosPuntosDeSuRutaDelDia() {
        service.crear(peticion("De la ruta", -15.65, -69.98, null, null));

        List<PuntoEstablecidoResponse> vistos = service.deMiAcopiador(USUARIO_ACOPIADOR);

        assertThat(vistos).extracting(PuntoEstablecidoResponse::nombre).contains("De la ruta");
    }

    @Test
    void elAcopiadorVeTambienLosAsignadosDirectamenteAElAunqueSeanDeOtraRuta() {
        Ruta otraRuta = acopiadorRepository.findAll().stream()
                .map(Acopiador::getRuta)
                .filter(r -> r != null && !r.getId().equals(ruta.getId()))
                .findFirst()
                .orElseThrow();
        service.crear(new PuntoEstablecidoRequest(
                "Asignado directo", -15.70, -70.00, otraRuta.getId(), acopiador.getId(), null, null));

        List<PuntoEstablecidoResponse> vistos = service.deMiAcopiador(USUARIO_ACOPIADOR);

        assertThat(vistos).extracting(PuntoEstablecidoResponse::nombre).contains("Asignado directo");
    }

    @Test
    void unPuntoDeOtraRutaSinAsignacionDirectaNoApareceParaEsteAcopiador() {
        Ruta otraRuta = acopiadorRepository.findAll().stream()
                .map(Acopiador::getRuta)
                .filter(r -> r != null && !r.getId().equals(ruta.getId()))
                .findFirst()
                .orElseThrow();
        service.crear(new PuntoEstablecidoRequest(
                "Ajeno", -15.70, -70.00, otraRuta.getId(), null, null, null));

        List<PuntoEstablecidoResponse> vistos = service.deMiAcopiador(USUARIO_ACOPIADOR);

        assertThat(vistos).extracting(PuntoEstablecidoResponse::nombre).doesNotContain("Ajeno");
    }

    @Test
    void puedeAsociarseAUnProveedorParaLaReglaDeCercania() {
        PuntoEstablecido creado = service.crear(new PuntoEstablecidoRequest(
                "Parada del proveedor", -15.65, -69.98, ruta.getId(), null, proveedor.getId(), null));

        assertThat(creado.getProveedor()).isNotNull();
        assertThat(creado.getProveedor().getId()).isEqualTo(proveedor.getId());
    }

    /**
     * La app movil pinta el punto establecido con el numero de su fila en la ruta del dia y decide si
     * puede registrar el recojo con estos dos campos. Antes no se mandaban, asi que el mapa dibujaba
     * un circulo anonimo aunque su proveedor si estuviera en la lista (bug reportado 2026-10-06).
     */
    @Test
    void elPuntoLlegaConElEstadoYElOrdenDeSuProveedor() {
        PuntoEstablecido creado = service.crear(new PuntoEstablecidoRequest(
                "Parada del proveedor", -15.65, -69.98, ruta.getId(), null, proveedor.getId(), null));

        PuntoEstablecidoResponse visto = porId(service.deMiAcopiador(USUARIO_ACOPIADOR), creado.getId());

        assertThat(visto.proveedorId()).isEqualTo(proveedor.getId());
        assertThat(visto.proveedorEstado()).isEqualTo(proveedor.getEstado());
        assertThat(visto.proveedorOrdenEnRuta()).isEqualTo(proveedor.getOrdenEnRuta());
    }

    /** Un punto sin proveedor es solo una referencia del mapa: no inventa estado ni numero. */
    @Test
    void unPuntoSinProveedorLlegaSinEstadoNiOrden() {
        PuntoEstablecido creado = service.crear(peticion("Solo referencia", -15.65, -69.98, null, null));

        PuntoEstablecidoResponse visto = porId(service.deMiAcopiador(USUARIO_ACOPIADOR), creado.getId());

        assertThat(visto.proveedorId()).isNull();
        assertThat(visto.proveedorEstado()).isNull();
        assertThat(visto.proveedorOrdenEnRuta()).isNull();
    }

    private PuntoEstablecidoResponse porId(List<PuntoEstablecidoResponse> puntos, Long id) {
        return puntos.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void notFoundSiLaRutaNoExiste() {
        assertThatThrownBy(() -> service.crear(new PuntoEstablecidoRequest(
                "x", -15.65, -69.98, -1L, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
