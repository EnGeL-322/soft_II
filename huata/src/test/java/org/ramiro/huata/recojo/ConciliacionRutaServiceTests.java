package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recolectado en el camino vs. recibido en planta (RF-53, RF-54).
 *
 * <p>Lo que se fija aqui son las tres formas en que esta pantalla puede mentir: cortar la jornada en
 * una zona horaria distinta de la que usa el recojo (y perder los acopios de la tarde), sumar como
 * "recolectado en ruta" una entrega que el proveedor llevo el mismo a planta, y hacer desaparecer una
 * ruta cuyo acopiador solo registro visitas sin leche.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "calidad.demo@huata.pe")
class ConciliacionRutaServiceTests {

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


    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired
    private ConciliacionRutaService service;

    @Autowired
    private RecojoService recojoService;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private ProductorRepository productorRepository;

    private Ruta ruta;
    private List<Productor> proveedores;

    /**
     * Se trabaja sobre una fecha de 2035 que los datos DEMO no tocan: lo que se mide es lo que
     * registran estas pruebas, no el historial sembrado. Y proveedores distintos por caso, porque el
     * servicio solo admite una entrega diaria por proveedor.
     */
    private static final LocalDate DIA = LocalDate.of(2035, 7, 10);

    @BeforeEach
    void escenario() {
        ruta = rutaRepository.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        proveedores = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .limit(4)
                .toList();
    }

    private Recojo acopiar(Productor p, double litros, String horaLocal, FuenteRecojo fuente) {
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(),
                p.getId(),
                fuente == FuenteRecojo.PLANTA ? null : acopiadorDePrueba(),
                ruta.getId(),
                DIA.atTime(Integer.parseInt(horaLocal.substring(0, 2)), Integer.parseInt(horaLocal.substring(3)))
                        .atZone(LIMA)
                        .toInstant(),
                litros,
                6.4,
                fuente,
                null,
                null,
                null,
                null,
                EstadoVisita.ACOPIADO,
                null,
                null,
                null,
                null));
    }

    private ConciliacionRutaResponse fila() {
        return service.deFechaYRuta(DIA, ruta.getId()).orElseThrow();
    }

    /**
     * El corte del dia. Un acopio de las 19:30 de Lima son las 00:30 UTC del dia siguiente: con el
     * corte en UTC esta fila salia vacia y el litraje aparecia en la conciliacion de mañana.
     */
    @Test
    void elAcopioDeLaTardeCuentaEnElDiaEnQueOcurrio() {
        acopiar(proveedores.get(0), 40.0, "19:30", FuenteRecojo.RUTA);

        assertThat(fila().litrosEnRuta()).isEqualTo(40.0);
        assertThat(service.deFechaYRuta(DIA.plusDays(1), ruta.getId()).orElseThrow().litrosEnRuta())
                .isZero();
    }

    /** Lo primero que pide la pantalla: cuanto se acopio en el camino y en que proveedores. */
    @Test
    void sumaLoRecolectadoEnElCaminoYDetallaCadaParada() {
        acopiar(proveedores.get(0), 15.5, "05:10", FuenteRecojo.RUTA);
        acopiar(proveedores.get(1), 24.0, "05:40", FuenteRecojo.RUTA);

        ConciliacionRutaResponse fila = fila();

        assertThat(fila.litrosEnRuta()).isEqualTo(39.5);
        assertThat(fila.totalRecolectado()).isEqualTo(39.5);
        assertThat(fila.proveedoresAcopiados()).isEqualTo(2);
        assertThat(fila.detalle()).extracting(ConciliacionVisitaResponse::litros).containsExactly(15.5, 24.0);
        assertThat(fila.detalle()).allSatisfy(v -> assertThat(v.productorCodigo()).isNotBlank());
        // El detalle va en orden de captura: es el recorrido, no un ranking.
        assertThat(fila.ultimoAcopio()).isEqualTo(fila.detalle().get(1).fechaHora());
    }

    /**
     * Una entrega que el proveedor llevo el mismo a planta no viajo en la movilidad. Contarla como
     * recolectada en ruta inventaria un faltante igual a esos litros.
     */
    @Test
    void laEntregaDirectaEnPlantaNoCuentaComoRecolectadaEnElCamino() {
        acopiar(proveedores.get(0), 30.0, "05:10", FuenteRecojo.RUTA);
        acopiar(proveedores.get(1), 20.0, "08:00", FuenteRecojo.PLANTA);

        service.registrar(new ConciliacionRutaRequest(DIA, ruta.getId(), null, 30.0, null));
        ConciliacionRutaResponse fila = fila();

        assertThat(fila.litrosEnRuta()).isEqualTo(30.0);
        assertThat(fila.litrosEntregadosEnPlanta()).isEqualTo(20.0);
        assertThat(fila.totalRecolectado()).isEqualTo(50.0);
        assertThat(fila.diferencia()).isZero();
    }

    /** Positivo = llego menos a planta de lo que se cargo en el camino. */
    @Test
    void laDiferenciaEsLoQueSeCargoMenosLoQueLlego() {
        acopiar(proveedores.get(0), 100.0, "05:10", FuenteRecojo.RUTA);

        service.registrar(new ConciliacionRutaRequest(DIA, ruta.getId(), null, 96.5, "Derrame"));
        ConciliacionRutaResponse fila = fila();

        assertThat(fila.litrosRecibidosPlanta()).isEqualTo(96.5);
        assertThat(fila.diferencia()).isEqualTo(3.5);
        assertThat(fila.observacion()).isEqualTo("Derrame");
    }

    /** Quien recibe es la cuenta que registra (aqui calidad.demo), no un nombre escrito a mano. */
    @Test
    void elResponsableEsLaCuentaQueRegistra() {
        acopiar(proveedores.get(0), 40.0, "05:10", FuenteRecojo.RUTA);

        service.registrar(new ConciliacionRutaRequest(DIA, ruta.getId(), null, 40.0, "  "));

        assertThat(fila().responsable()).isEqualTo("Responsable Calidad Demo");
        assertThat(fila().observacion()).isNull();
    }

    /** Mientras planta no anota su total, la diferencia es desconocida y no cero. */
    @Test
    void sinTotalDePlantaLaDiferenciaQuedaEnBlanco() {
        acopiar(proveedores.get(0), 55.0, "05:10", FuenteRecojo.RUTA);

        assertThat(fila().litrosRecibidosPlanta()).isNull();
        assertThat(fila().diferencia()).isNull();
    }

    /**
     * Una ruta donde el acopiador solo marco visitas sin leche tiene que seguir en la lista: es
     * justamente lo que explica una recoleccion baja. Antes el filtro pedia litros &gt; 0 y la
     * ocultaba.
     */
    @Test
    void laRutaSigueVisibleAunqueSoloHayaVisitasSinLeche() {
        recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(),
                proveedores.get(0).getId(),
                acopiadorDePrueba(),
                ruta.getId(),
                DIA.atTime(5, 20).atZone(LIMA).toInstant(),
                0.0,
                null,
                FuenteRecojo.RUTA,
                null,
                null,
                null,
                null,
                EstadoVisita.NO_ENTREGADO,
                "Vaca enferma",
                null,
                null,
                null));

        ConciliacionRutaResponse fila = fila();
        assertThat(fila.litrosEnRuta()).isZero();
        assertThat(fila.proveedoresNoEntregaron()).isEqualTo(1);
        assertThat(fila.detalle()).singleElement().satisfies(v -> {
            assertThat(v.estadoVisita()).isEqualTo(EstadoVisita.NO_ENTREGADO);
            assertThat(v.motivoNoEntrega()).isEqualTo("Vaca enferma");
        });
        assertThat(service.deFecha(DIA)).anySatisfy(f -> assertThat(f.rutaId()).isEqualTo(ruta.getId()));
    }

    /** Un recojo cancelado deja de contar: si no, la conciliacion muestra leche ya anulada. */
    @Test
    void elRecojoCanceladoDejaDeSumar() {
        Recojo r = acopiar(proveedores.get(0), 70.0, "05:10", FuenteRecojo.RUTA);
        assertThat(fila().litrosEnRuta()).isEqualTo(70.0);

        recojoService.cancelar(r.getId(), new RecojoCancelRequest("Leche cortada"));

        assertThat(fila().litrosEnRuta()).isZero();
        assertThat(fila().detalle()).isEmpty();
    }

    @Autowired
    private org.ramiro.huata.reportes.OperacionService operacion;

    /**
     * La leche que el proveedor llevo el mismo a planta ya esta en planta: no hay llegada que anotar.
     * Antes la ruta del proveedor salia "por recibir" en Recepcion de rutas pidiendo una llegada que
     * ningun camion hizo (correccion del 2026-10-26).
     */
    @Test
    void unaRutaConSoloEntregasDirectasNoPideRegistrarLlegada() {
        acopiar(proveedores.get(0), 25.0, "08:00", FuenteRecojo.PLANTA);

        assertThat(service.deFecha(DIA)).noneSatisfy(f -> assertThat(f.rutaId()).isEqualTo(ruta.getId()));
        assertThat(service.deFechaYRuta(DIA, ruta.getId())).get()
                .satisfies(f -> assertThat(f.requiereRecepcion()).isFalse());
        // Y el cierre del dia ya la da por recibida: no la exige como ruta pendiente.
        var totales = operacion.totales(DIA);
        assertThat(totales.rutasSinRecibir()).doesNotContain(ruta.getNombre());
        assertThat(totales.recibidoPlanta()).isEqualTo(totales.recogido());
    }

    /** En una ruta con camion, el detalle y los conteos son de lo que trajo el camion. */
    @Test
    void elDetalleDeLaRutaEsSoloLoQueTrajoElCamion() {
        acopiar(proveedores.get(0), 30.0, "05:10", FuenteRecojo.RUTA);
        acopiar(proveedores.get(1), 20.0, "08:00", FuenteRecojo.PLANTA);

        ConciliacionRutaResponse fila = fila();

        assertThat(fila.requiereRecepcion()).isTrue();
        assertThat(fila.proveedoresAcopiados()).isEqualTo(1);
        assertThat(fila.detalle()).singleElement().satisfies(v -> assertThat(v.fuente()).isNotEqualTo(FuenteRecojo.PLANTA));
        assertThat(fila.litrosEntregadosEnPlanta()).isEqualTo(20.0);
    }

    /** Sin nada registrado la ruta no aparece: una fila de ceros solo estorba. */
    @Test
    void unaRutaSinMovimientoNoSaleEnLaLista() {
        assertThat(service.deFecha(DIA)).noneSatisfy(f -> assertThat(f.rutaId()).isEqualTo(ruta.getId()));
    }
}
