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

    @Test void calculaJornadaConLimitesDeLimaYSinDuplicarObservada() {
        var fecha = LocalDate.of(2026, 8, 20);
        var tarifa = new org.ramiro.huata.liquidaciones.TarifaLeche();
        tarifa.setVigenteDesde(fecha);
        tarifa.setPrecioNormal(1.75);
        tarifa.setPrecioObservada(1.30);
        tarifas.save(tarifa);
        recepcion("2026-08-20T05:00:00Z", 100, ResultadoCalidad.ACEPTADA);
        recepcion("2026-08-21T04:59:59Z", 20, ResultadoCalidad.OBSERVADA);
        recepcion("2026-08-20T12:00:00Z", 10, ResultadoCalidad.RECHAZADA);
        recepcion("2026-08-21T05:00:00Z", 500, ResultadoCalidad.ACEPTADA);
        var cancelada = recepcion("2026-08-20T10:00:00Z", 800, ResultadoCalidad.ACEPTADA);
        cancelada.setEstado(EstadoRecojo.CANCELLED); recojos.save(cancelada);
        var d = operacion.resumen(fecha);
        assertEquals(120, d.aceptados(), .001);
        assertEquals(20, d.observados(), .001);
        assertEquals(10, d.rechazados(), .001);
        assertEquals(2, d.recepcionesAceptadas());
        assertEquals(201, d.compraEstimada(), .001);
        assertEquals(1, d.proveedores());
    }

    @Test void validaYPersisteCierreYReapertura() {
        var fecha = LocalDate.of(2026, 8, 21);
        recepcion("2026-08-21T12:00:00Z", 100, ResultadoCalidad.ACEPTADA);
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, -1, ""));
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, 98, ""));
        assertTrue(operacion.cerrar(fecha, 98, "Diferencia documentada de medición").isCerrado());
        assertEquals(100, operacion.resumen(fecha).cierre().getLitrosSistema());
        assertEquals(98, operacion.resumen(fecha).cierre().getLitrosPlanta());
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, 100, ""));
        assertFalse(operacion.reabrir(fecha).isCerrado());
        assertTrue(operacion.cerrar(fecha, 100, "Conciliado").isCerrado());
    }

    @Test void impideCerrarConControlesPendientes() {
        var fecha = LocalDate.of(2026, 8, 22);
        recepcion("2026-08-22T12:00:00Z", 50, null);
        assertEquals(1, operacion.resumen(fecha).pendientesCalidad());
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(fecha, 50, "Pendiente"));
        assertThrows(ResponseStatusException.class, () -> operacion.cerrar(LocalDate.now().plusDays(1), 0, ""));
    }
}
