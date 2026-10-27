package org.ramiro.huata.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.contabilidad.AsientoContableRepository;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.LecheAcopioService;
import org.ramiro.huata.parametros.ReglasLecheService;
import org.ramiro.huata.parametros.ReglasLecheService.ReglasLeche;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.ConciliacionRuta;
import org.ramiro.huata.recojo.ConciliacionRutaRepository;
import org.ramiro.huata.recojo.EstadoVisita;
import org.ramiro.huata.recojo.FuenteRecojo;
import org.ramiro.huata.recojo.RecepcionLecheService;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que la mala calidad cambia fuera de calidad (V33, pedido del cliente del 2026-09-27):
 * el proveedor se retira al llegar al limite configurado, la leche rechazada no queda en el almacen
 * aunque haya llegado a planta, y la leche recibida genera su asiento con el costo configurable.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class MalaCalidadYLecheAptaTests {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    /** Fecha que los datos DEMO no tocan. */
    private static final LocalDate DIA = LocalDate.of(2036, 5, 20);

    @Autowired private CalidadService calidad;
    @Autowired private ReglasLecheService reglas;
    @Autowired private RecepcionLecheService recepcion;
    @Autowired private LecheAcopioService lecheAcopio;
    @Autowired private InventarioService inventario;
    @Autowired private ProductorRepository productores;
    @Autowired private RecojoRepository recojos;
    @Autowired private RutaRepository rutas;
    @Autowired private ConciliacionRutaRepository conciliaciones;
    @Autowired private AsientoContableRepository asientos;

    private Ruta ruta;
    private Productor proveedor;
    private int dia;

    @BeforeEach
    void escenario() {
        ruta = rutas.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        Productor p = new Productor();
        p.setCodigo("QA" + (System.nanoTime() % 100_000_000));
        p.setNombres("Prueba");
        p.setApellidos("Calidad");
        p.setRuta(ruta);
        p.setEstado(EstadoProductor.ACTIVO);
        proveedor = productores.saveAndFlush(p);
        dia = 0;
        reglas.guardar(new ReglasLeche(2, true, true, null));
    }

    /** Una entrega en ruta, un dia distinto cada vez (una entrega por dia por proveedor). */
    private Recojo entrega(double litros) {
        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(proveedor);
        r.setRuta(ruta);
        r.setFuente(FuenteRecojo.RUTA);
        r.setEstadoVisita(EstadoVisita.ACOPIADO);
        r.setLitros(litros);
        r.setFechaHora(DIA.plusDays(dia++).atTime(6, 0).atZone(LIMA).toInstant());
        return recojos.saveAndFlush(r);
    }

    private void control(Recojo r, ResultadoCalidad resultado) {
        calidad.registrarControl(new ControlCalidadRequest(
                r.getId(), null, null, null, 30.0, null, null, null, null, null, null, null, resultado,
                "Analizador QA", "Calidad QA", null, null, null, null, "Prueba"));
    }

    private EstadoProductor estado() {
        return productores.findById(proveedor.getId()).orElseThrow().getEstado();
    }

    private BigDecimal leche() {
        return inventario.saldoMateriaPrima(lecheAcopio.leche().getId());
    }

    // ---- Retiro por mala calidad ----

    @Test
    void conLaSegundaDeteccionElProveedorQuedaRetirado() {
        control(entrega(30), ResultadoCalidad.RECHAZADA);
        assertThat(estado()).isEqualTo(EstadoProductor.ACTIVO);

        control(entrega(30), ResultadoCalidad.RECHAZADA);
        assertThat(estado()).isEqualTo(EstadoProductor.RETIRADO);
    }

    @Test
    void elLimiteDeDeteccionesSeConfigura() {
        reglas.guardar(new ReglasLeche(3, true, true, null));
        control(entrega(30), ResultadoCalidad.RECHAZADA);
        control(entrega(30), ResultadoCalidad.RECHAZADA);
        assertThat(estado()).isEqualTo(EstadoProductor.ACTIVO);

        control(entrega(30), ResultadoCalidad.RECHAZADA);
        assertThat(estado()).isEqualTo(EstadoProductor.RETIRADO);
    }

    @Test
    void conCeroNuncaSeRetiraAutomaticamente() {
        reglas.guardar(new ReglasLeche(0, true, true, null));
        for (int i = 0; i < 4; i++) {
            control(entrega(30), ResultadoCalidad.RECHAZADA);
        }
        assertThat(estado()).isEqualTo(EstadoProductor.ACTIVO);
    }

    @Test
    void siSeConfiguraLaObservadaNoCuentaComoMalaCalidad() {
        reglas.guardar(new ReglasLeche(2, false, true, null));
        control(entrega(30), ResultadoCalidad.OBSERVADA);
        control(entrega(30), ResultadoCalidad.OBSERVADA);
        assertThat(estado()).isEqualTo(EstadoProductor.ACTIVO);

        reglas.guardar(new ReglasLeche(2, true, true, null));
        control(entrega(30), ResultadoCalidad.ACEPTADA); // recalcula con la regla nueva
        assertThat(estado()).isEqualTo(EstadoProductor.RETIRADO);
    }

    // ---- La leche no apta no queda en el almacen ----

    /** Llega a planta, entra; el analisis la rechaza despues: sale sola del almacen. */
    @Test
    void laLecheRechazadaDespuesDeRecibirlaSaleDelAlmacen() {
        Recojo r = entrega(40);
        BigDecimal antes = leche();
        ConciliacionRuta c = new ConciliacionRuta();
        c.setFecha(DIA);
        c.setRuta(ruta);
        c.setLitrosRecibidosPlanta(40.0);
        conciliaciones.saveAndFlush(c);
        recepcion.sincronizarRuta(DIA, ruta.getId());
        assertThat(leche().subtract(antes)).isEqualByComparingTo("40");

        control(r, ResultadoCalidad.RECHAZADA);

        assertThat(leche().subtract(antes)).isEqualByComparingTo("0");
    }

    @Test
    void laObservadaEntraONoSegunLaConfiguracion() {
        reglas.guardar(new ReglasLeche(0, true, false, null));
        Recojo r = entrega(25);
        control(r, ResultadoCalidad.OBSERVADA);
        BigDecimal antes = leche();
        ConciliacionRuta c = new ConciliacionRuta();
        c.setFecha(DIA);
        c.setRuta(ruta);
        c.setLitrosRecibidosPlanta(25.0);
        conciliaciones.saveAndFlush(c);

        recepcion.sincronizarRuta(DIA, ruta.getId());
        assertThat(leche().subtract(antes)).as("observada no entra").isEqualByComparingTo("0");

        reglas.guardar(new ReglasLeche(0, true, true, null));
        recepcion.sincronizarRuta(DIA, ruta.getId());
        assertThat(leche().subtract(antes)).as("observada entra").isEqualByComparingTo("25");
    }

    // ---- Costo configurable y asiento ----

    @Test
    void elCostoConfiguradoSeUsaYLaRecepcionGeneraSuAsiento() {
        reglas.guardar(new ReglasLeche(2, true, true, new BigDecimal("2.10")));
        entrega(10);
        long asientosAntes = asientos.findAll().stream()
                .filter(a -> a.getOrigenTipo() == OrigenAsiento.RECEPCION_LECHE)
                .count();
        ConciliacionRuta c = new ConciliacionRuta();
        c.setFecha(DIA);
        c.setRuta(ruta);
        c.setLitrosRecibidosPlanta(10.0);
        conciliaciones.saveAndFlush(c);

        recepcion.sincronizarRuta(DIA, ruta.getId());

        assertThat(lecheAcopio.leche().getCostoUnitarioReferencial()).isEqualByComparingTo("2.10");
        assertThat(asientos.findAll())
                .filteredOn(a -> a.getOrigenTipo() == OrigenAsiento.RECEPCION_LECHE)
                .hasSize((int) asientosAntes + 1)
                .anySatisfy(a -> assertThat(a.getGlosa()).contains("Leche recibida en planta: 10 L"));
    }

    @Test
    void sinCostoConfiguradoSeUsaLaTarifaDelDia() {
        reglas.guardar(new ReglasLeche(2, true, true, null));
        assertThat(lecheAcopio.costoPorLitro(DIA)).isNotNull();
    }
}
