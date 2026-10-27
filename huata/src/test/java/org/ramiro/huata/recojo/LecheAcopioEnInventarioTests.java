package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.inventario.DocumentoOrigenTipo;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.LecheAcopioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaService;
import org.ramiro.huata.inventario.MovimientoInventarioRepository;
import org.ramiro.huata.inventario.TipoMovimientoInventario;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * La leche del acopio entra al inventario al recibirla en planta (V31, decision del cliente).
 *
 * <p>Lo que se fija: entra lo que planta recibe (no lo recogido), una correccion mueve solo la
 * diferencia, no se puede "des-recibir" leche que ya se uso, la entrega directa del proveedor entra
 * al registrarse y sale si se anula, y el costo es la tarifa de leche del dia.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class LecheAcopioEnInventarioTests {

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
    /** Fecha que los datos DEMO no tocan. */
    private static final LocalDate DIA = LocalDate.of(2036, 3, 12);

    @Autowired private ConciliacionRutaService conciliacion;
    @Autowired private RecojoService recojoService;
    @Autowired private LecheAcopioService lecheAcopio;
    @Autowired private InventarioService inventario;
    @Autowired private MateriaPrimaService materiaPrimaService;
    @Autowired private MovimientoInventarioRepository movimientos;
    @Autowired private TarifaLecheRepository tarifas;
    @Autowired private RutaRepository rutaRepository;
    @Autowired private ProductorRepository productorRepository;

    private Ruta ruta;
    private MateriaPrima leche;
    private BigDecimal saldoInicial;

    @BeforeEach
    void escenario() {
        ruta = rutaRepository.findAll().stream().filter(Ruta::isActivo).findFirst().orElseThrow();
        leche = lecheAcopio.leche();
        saldoInicial = saldo();
    }

    private BigDecimal saldo() {
        return inventario.saldoMateriaPrima(leche.getId());
    }

    private BigDecimal entro() {
        return saldo().subtract(saldoInicial).stripTrailingZeros();
    }

    private void recibirEnPlanta(double litros) {
        conciliacion.registrar(new ConciliacionRutaRequest(DIA, ruta.getId(), null, litros, null));
    }

    private Recojo entregaDirectaEnPlanta(double litros) {
        Productor p = productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .filter(x -> x.getEstado() == EstadoProductor.ACTIVO)
                .findFirst()
                .orElseThrow();
        return recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), p.getId(), null, ruta.getId(),
                DIA.atTime(7, 30).atZone(LIMA).toInstant(), litros, 6.0, FuenteRecojo.PLANTA,
                null, null, null, null, EstadoVisita.ACOPIADO, null, null, null, null));
    }

    @Test
    void existeUnaSolaLecheDelAcopioYEsEnLitros() {
        assertThat(leche.isLecheAcopio()).isTrue();
        assertThat(leche.getUnidadMedida().name()).isEqualTo("LITRO");
    }

    /** Entra lo que planta mide al recibir la ruta, no lo que se recogió en el camino. */
    @Test
    void loRecibidoEnPlantaEntraAlInventario() {
        recibirEnPlanta(120.0);

        assertThat(entro()).isEqualByComparingTo("120");
        assertThat(movimientos.findAll())
                .filteredOn(m -> m.getDocumentoOrigenTipo() == DocumentoOrigenTipo.CONCILIACION)
                .anySatisfy(m -> assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA_ACOPIO));
    }

    /** Corregir el total mueve solo la diferencia: ni duplica ni pierde leche. */
    @Test
    void corregirElTotalRecibidoSoloMueveLaDiferencia() {
        recibirEnPlanta(120.0);
        recibirEnPlanta(100.0);
        assertThat(entro()).isEqualByComparingTo("100");

        recibirEnPlanta(130.0);
        assertThat(entro()).isEqualByComparingTo("130");
    }

    /** Bajar un total cuya leche ya se usó dejaría el almacén en negativo: se rechaza con explicación. */
    @Test
    void noSePuedeBajarLecheQueYaSeUsoEnProduccion() {
        recibirEnPlanta(50.0);
        BigDecimal todo = saldo();
        inventario.registrarAjusteSalida(leche, todo, DIA, DocumentoOrigenTipo.AJUSTE, 1L, "Consumida en prueba");

        assertThatThrownBy(() -> recibirEnPlanta(0.0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya se usó en producción");
    }

    /** La leche que el proveedor lleva él mismo a planta ya está en planta: entra al registrarse. */
    @Test
    void laEntregaDirectaEnPlantaEntraYAlAnularseSale() {
        Recojo recojo = entregaDirectaEnPlanta(35.0);
        assertThat(entro()).isEqualByComparingTo("35");

        recojoService.cancelar(recojo.getId(), new RecojoCancelRequest("Registrada por error"));
        assertThat(entro()).isEqualByComparingTo("0");
    }

    /** La leche de ruta NO entra al recogerla: está en el camión hasta que planta la recibe. */
    @Test
    void laLecheRecogidaEnRutaNoEntraHastaQuePlantaLaRecibe() {
        Productor p = productorRepository.findAll().stream()
                .filter(x -> x.isActivo() && x.getEstado() == EstadoProductor.ACTIVO)
                .findFirst()
                .orElseThrow();
        recojoService.registrar(new RecojoRequest(
                UUID.randomUUID().toString(), p.getId(), acopiadorDePrueba(), ruta.getId(),
                DIA.atTime(6, 0).atZone(LIMA).toInstant(), 40.0, 6.0, FuenteRecojo.RUTA,
                null, null, null, null, EstadoVisita.ACOPIADO, null, null, null, null));

        assertThat(entro()).isEqualByComparingTo("0");
    }

    /** El costo de la leche es lo que se paga al proveedor: la tarifa normal vigente ese día. */
    @Test
    void elCostoDeLaLecheEsLaTarifaDelDia() {
        double tarifa = tarifas.findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(DIA)
                .orElseThrow()
                .getPrecioNormal();

        recibirEnPlanta(10.0);

        assertThat(lecheAcopio.leche().getCostoUnitarioReferencial()).isEqualByComparingTo(BigDecimal.valueOf(tarifa));
    }

    @Test
    void laLecheDelAcopioNoSeDaDeBaja() {
        assertThatThrownBy(() -> materiaPrimaService.inactivar(leche.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede dar de baja");
    }

    @Test
    void cadaFilaDeLaConciliacionTraeSuZona() {
        recibirEnPlanta(10.0);
        List<ConciliacionRutaResponse> filas = conciliacion.deFecha(DIA);
        assertThat(filas).filteredOn(f -> f.rutaId().equals(ruta.getId()))
                .singleElement()
                .satisfies(f -> assertThat(f.zona()).isEqualTo(ruta.getZona()));
    }
}
