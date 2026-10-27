package org.ramiro.huata.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.DatosPersonales;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.EstadoIncidencia;
import org.ramiro.huata.calidad.IncidenciaCalidad;
import org.ramiro.huata.calidad.IncidenciaCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PortalService}: calidad por zona (RF-48) e incidencias nombradas del día (RF-49) para el
 * portal público sin autenticación.
 *
 * <p>Regla 0 (no inventar): una zona sin ningún control ese día no debe aparecer, y una incidencia
 * de otro día no debe filtrarse al resumen de hoy.
 */
@SpringBootTest
@Transactional
class PortalServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 16);
    private static final LocalDate AYER = HOY.minusDays(1);

    @Autowired
    private PortalService portalService;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ControlCalidadRepository controlCalidadRepository;

    @Autowired
    private IncidenciaCalidadRepository incidenciaCalidadRepository;

    private Productor nuevoProductor(String zona) {
        Productor p = new Productor();
        p.setCodigo("PUB-" + UUID.randomUUID().toString().substring(0, 6));
        p.setNombres("Demo");
        p.setApellidos("Portal");
        p.setEstado(EstadoProductor.ACTIVO);
        p.setZonaPrincipal(zona);
        return productorRepository.save(p);
    }

    private void controlEnFecha(Productor productor, ResultadoCalidad resultado, LocalDate fecha) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(productor);
        r.setFechaHora(fecha.atTime(10, 0).toInstant(ZoneOffset.UTC));
        r.setLitros(30.0);
        r = recojoRepository.save(r);

        ControlCalidad c = new ControlCalidad();
        c.setRecojo(r);
        c.setResultado(resultado);
        controlCalidadRepository.save(c);
    }

    private void incidenciaEnFecha(Productor productor, String tipo, LocalDate fecha) {
        IncidenciaCalidad i = new IncidenciaCalidad();
        i.setProductor(productor);
        i.setTipo(tipo);
        i.setFecha(fecha.atTime(11, 0).toInstant(ZoneOffset.UTC));
        i.setEstado(EstadoIncidencia.ABIERTA);
        incidenciaCalidadRepository.save(i);
    }

    // ------------------------------------------------------------------------- Zonas del día

    @Test
    void unaZonaSoloConAceptadasSeMuestraApta() {
        Productor p = nuevoProductor("Zona Apta " + UUID.randomUUID());
        controlEnFecha(p, ResultadoCalidad.ACEPTADA, HOY);
        controlEnFecha(p, ResultadoCalidad.ACEPTADA, HOY);

        ZonaCalidadPublica zona = buscarZona(p.getZonaPrincipal());

        assertThat(zona.controles()).isEqualTo(2);
        assertThat(zona.estado()).isEqualTo("APTA");
    }

    @Test
    void unaZonaConObservadaYSinRechazadaQuedaObservada() {
        Productor p = nuevoProductor("Zona Observada " + UUID.randomUUID());
        controlEnFecha(p, ResultadoCalidad.ACEPTADA, HOY);
        controlEnFecha(p, ResultadoCalidad.OBSERVADA, HOY);

        assertThat(buscarZona(p.getZonaPrincipal()).estado()).isEqualTo("OBSERVADA");
    }

    /** RECHAZADA manda: aunque haya más controles aptos, la zona no se declara apta ese día. */
    @Test
    void unaSolaRechazadaMarcaLaZonaComoAdulteracionSinImportarLasDemas() {
        Productor p = nuevoProductor("Zona Mala " + UUID.randomUUID());
        controlEnFecha(p, ResultadoCalidad.ACEPTADA, HOY);
        controlEnFecha(p, ResultadoCalidad.OBSERVADA, HOY);
        controlEnFecha(p, ResultadoCalidad.RECHAZADA, HOY);

        ZonaCalidadPublica zona = buscarZona(p.getZonaPrincipal());

        assertThat(zona.controles()).isEqualTo(3);
        assertThat(zona.rechazadas()).isEqualTo(1);
        assertThat(zona.estado()).isEqualTo("ADULTERACION");
    }

    /** 20:00 en Lima es 01:00 UTC del dia siguiente: cuenta para el dia de Lima, no para el de UTC. */
    @Test
    void unControlDeLaNocheEnLimaCuentaParaEseDiaYNoParaElSiguiente() {
        Productor p = nuevoProductor("Zona Noche " + UUID.randomUUID());
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(p);
        r.setFechaHora(HOY.atTime(20, 0).atZone(PortalService.ZONA_HORARIA).toInstant());
        r.setLitros(30.0);
        ControlCalidad c = new ControlCalidad();
        c.setRecojo(recojoRepository.save(r));
        c.setResultado(ResultadoCalidad.ACEPTADA);
        controlCalidadRepository.save(c);

        assertThat(portalService.zonasDelDia(HOY)).anyMatch(z -> z.zona().equals(p.getZonaPrincipal()));
        assertThat(portalService.zonasDelDia(HOY.plusDays(1))).noneMatch(z -> z.zona().equals(p.getZonaPrincipal()));
    }

    @Test
    void unControlDeOtroDiaNoCuentaParaHoy() {
        Productor p = nuevoProductor("Zona Ayer " + UUID.randomUUID());
        controlEnFecha(p, ResultadoCalidad.RECHAZADA, AYER);

        assertThat(portalService.zonasDelDia(HOY).stream().noneMatch(z -> z.zona().equals(p.getZonaPrincipal())))
                .isTrue();
        assertThat(portalService.zonasDelDia(AYER).stream().anyMatch(z -> z.zona().equals(p.getZonaPrincipal())))
                .isTrue();
    }

    private ZonaCalidadPublica buscarZona(String zona) {
        List<ZonaCalidadPublica> zonas = portalService.zonasDelDia(HOY);
        return zonas.stream().filter(z -> z.zona().equals(zona)).findFirst().orElseThrow();
    }

    // -------------------------------------------------------------------- Incidencias del día

    @Test
    void unaIncidenciaDeHoySePublicaConElCodigoEnmascarado() {
        Productor p = nuevoProductor("Zona Incidencia " + UUID.randomUUID());
        incidenciaEnFecha(p, "Adulteración", HOY);

        List<IncidenciaPublica> incidencias = portalService.incidenciasDelDia(HOY);

        assertThat(incidencias)
                .anySatisfy(i -> {
                    // El codigo va enmascarado: desde que el codigo del proveedor es su DNI, publicarlo
                    // entero en una pantalla sin login seria exponer el documento (seccion 30, RNF-12).
                    assertThat(i.productorCodigo()).isEqualTo(DatosPersonales.enmascarar(p.getCodigo()));
                    assertThat(i.productorCodigo()).doesNotContain(p.getCodigo());
                    assertThat(i.zona()).isEqualTo(p.getZonaPrincipal());
                    assertThat(i.tipo()).isEqualTo("Adulteración");
                });
    }

    @Test
    void unaIncidenciaDeAyerNoApareceEnElResumenDeHoy() {
        Productor p = nuevoProductor("Zona Incidencia Ayer " + UUID.randomUUID());
        incidenciaEnFecha(p, "Agua añadida", AYER);

        assertThat(portalService.incidenciasDelDia(HOY))
                .noneMatch(i -> i.productorCodigo().equals(DatosPersonales.enmascarar(p.getCodigo())));
        assertThat(portalService.incidenciasDelDia(AYER))
                .anyMatch(i -> i.productorCodigo().equals(DatosPersonales.enmascarar(p.getCodigo())));
    }

    @Test
    void elResumenDelDiaTraeZonasEIncidenciasJuntas() {
        Productor p = nuevoProductor("Zona Resumen " + UUID.randomUUID());
        controlEnFecha(p, ResultadoCalidad.RECHAZADA, HOY);
        incidenciaEnFecha(p, "Adulteración", HOY);

        ResumenPublicoHoy resumen = portalService.resumenDelDia(HOY);

        assertThat(resumen.fecha()).isEqualTo(HOY);
        assertThat(resumen.zonas()).anyMatch(z -> z.zona().equals(p.getZonaPrincipal()));
        assertThat(resumen.incidencias())
                .anyMatch(i -> i.productorCodigo().equals(DatosPersonales.enmascarar(p.getCodigo())));
    }

    // ------------------------------------------------------------- Consulta por código (zona)

    @Test
    void laConsultaPorCodigoUsaLaZonaPrincipalVigente() {
        Productor p = nuevoProductor("Zona Vigente " + UUID.randomUUID());

        ConsultaPublica consulta = portalService.consultarPorCodigo(p.getCodigo());

        assertThat(consulta.zona()).isEqualTo(p.getZonaPrincipal());
    }
}
