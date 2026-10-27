package org.ramiro.huata;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.ramiro.huata.reportes.*;
import org.ramiro.huata.recojo.*;
import org.ramiro.huata.calidad.*;
import org.ramiro.huata.productores.ProductorRepository;

@SpringBootTest
@Transactional
class OperacionTests {
    @Autowired OperacionService operacion;
    @Autowired RecojoRepository recojos;
    @Autowired ControlCalidadRepository controles;
    @Autowired ProductorRepository productores;
    @Autowired org.ramiro.huata.liquidaciones.TarifaLecheRepository tarifas;
    @Autowired org.ramiro.huata.rutas.RutaRepository rutas;
    @Autowired org.ramiro.huata.recojo.ConciliacionRutaRepository conciliaciones;

    private org.ramiro.huata.rutas.Ruta ruta() {
        return rutas.findAll().stream().filter(org.ramiro.huata.rutas.Ruta::isActivo).findFirst().orElseThrow();
    }

    /** Recepcion recogida EN RUTA (la que planta tiene que recibir para poder cerrar el dia). */
    private Recojo enRuta(String instant, double litros, ResultadoCalidad resultado) {
        var r = recepcion(instant, litros, resultado);
        r.setRuta(ruta());
        r.setFuente(org.ramiro.huata.recojo.FuenteRecojo.RUTA);
        return recojos.save(r);
    }

    /** Lo que planta anoto como recibido de la ruta ese dia. */
    private void recibido(LocalDate fecha, double litros) {
        var c = new org.ramiro.huata.recojo.ConciliacionRuta();
        c.setFecha(fecha);
        c.setRuta(ruta());
        c.setLitrosRecibidosPlanta(litros);
        conciliaciones.save(c);
    }

    private Recojo recepcion(String instant, double litros, ResultadoCalidad resultado) {
        var r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(productores.findAll().get(0));
        r.setFechaHora(Instant.parse(instant));
        r.setLitros(litros);
        r = recojos.save(r);
        if (resultado != null) {
            var c = new ControlCalidad(); c.setRecojo(r); c.setResultado(resultado); controles.save(c);
        }
        return r;
    }

    // 2035 y no una fecha cercana: DemoDataSeeder siembra una tarifa vigente desde "hoy - 2 meses", y el
    // dia en que esa fecha coincidia con la de esta prueba la tarifa sembrada ganaba el empate.
    @Test void calculaJornadaConLimitesDeLimaYSinDuplicarObservada() {
        var fecha = LocalDate.of(2035, 8, 20);
        var tarifa = new org.ramiro.huata.liquidaciones.TarifaLeche();
        tarifa.setVigenteDesde(fecha);
        tarifa.setPrecioNormal(1.75);
        tarifa.setPrecioObservada(1.30);
        tarifas.save(tarifa);
        recepcion("2035-08-20T05:00:00Z", 100, ResultadoCalidad.ACEPTADA);
        recepcion("2035-08-21T04:59:59Z", 20, ResultadoCalidad.OBSERVADA);
        recepcion("2035-08-20T12:00:00Z", 10, ResultadoCalidad.RECHAZADA);
        recepcion("2035-08-21T05:00:00Z", 500, ResultadoCalidad.ACEPTADA);
        var cancelada = recepcion("2035-08-20T10:00:00Z", 800, ResultadoCalidad.ACEPTADA);
        cancelada.setEstado(EstadoRecojo.CANCELLED); recojos.save(cancelada);
        var d = operacion.resumen(fecha);
        assertEquals(120, d.aceptados(), .001);
        assertEquals(20, d.observados(), .001);
        assertEquals(10, d.rechazados(), .001);
        assertEquals(2, d.recepcionesAceptadas());
        assertEquals(201, d.compraEstimada(), .001);
        assertEquals(1, d.proveedores());
    }

    /**
     * Desde V33 el cierre no pide litros: los calcula de las rutas. Antes habia dos numeros de
     * "planta" (el del cierre y el de la conciliacion por ruta) que podian contradecirse.
     */
    @Test void elCierreCalculaLosLitrosDePlantaDesdeLasRutas() {
        var fecha = LocalDate.of(2026, 8, 21);
        enRuta("2026-08-21T12:00:00Z", 100, ResultadoCalidad.ACEPTADA);

        // Sin el total de planta de la ruta no se puede cerrar, y el mensaje dice cual falta.
        var sinPlanta = assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, ""));
        assertTrue(sinPlanta.getReason().contains(ruta().getNombre()));

        recibido(fecha, 98);
        var t = operacion.totales(fecha);
        assertEquals(100, t.recogido(), .001);
        assertEquals(98, t.recibidoPlanta(), .001);

        // No coinciden: hay que explicarlo.
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, ""));
        assertTrue(operacion.cerrar(fecha, "Diferencia documentada de medición").isCerrado());
        assertEquals(100, operacion.resumen(fecha).cierre().getLitrosSistema());
        assertEquals(98, operacion.resumen(fecha).cierre().getLitrosPlanta());

        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, "Otra vez"));
        assertFalse(operacion.reabrir(fecha).isCerrado());
        assertTrue(operacion.cerrar(fecha, "Conciliado").isCerrado());
    }

    /** La leche que el proveedor llevo el mismo a planta ya esta en planta: suma en los dos lados. */
    @Test void lasEntregasDirectasEnPlantaNoPidenTotalDeRuta() {
        var fecha = LocalDate.of(2026, 8, 23);
        var r = recepcion("2026-08-23T12:00:00Z", 40, ResultadoCalidad.ACEPTADA);
        r.setFuente(org.ramiro.huata.recojo.FuenteRecojo.PLANTA);
        recojos.save(r);

        var t = operacion.totales(fecha);
        assertEquals(40, t.recogido(), .001);
        assertEquals(40, t.recibidoPlanta(), .001);
        assertTrue(t.rutasSinRecibir().isEmpty());
        assertTrue(operacion.cerrar(fecha, "").isCerrado());
    }

    @Test void impideCerrarConControlesPendientes() {
        var fecha = LocalDate.of(2026, 8, 22);
        recepcion("2026-08-22T12:00:00Z", 50, null);
        assertEquals(1, operacion.resumen(fecha).pendientesCalidad());
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, "Pendiente"));
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(LocalDate.now().plusDays(1), ""));
    }
}
