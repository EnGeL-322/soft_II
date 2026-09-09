package org.ramiro.huata;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.calidad.*;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.*;
import org.ramiro.huata.reportes.ReporteEventosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReporteEventosTests {
    @Autowired ReporteEventosService reportes;
    @Autowired RecojoRepository recojos;
    @Autowired ProductorRepository productores;
    @Autowired ControlCalidadRepository controles;

    @Test void usaFechaDeLimaYNoDuplicaLitrosEnCalidad() {
        var r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(productores.findAll().get(0));
        r.setFechaHora(Instant.parse("2025-01-02T04:59:59Z"));
        r.setLitros(12.5);
        recojos.save(r);
        var c = new ControlCalidad();
        c.setRecojo(r); c.setResultado(ResultadoCalidad.OBSERVADA); controles.save(c);
        var fecha = LocalDate.of(2025, 1, 1);
        var eventos = reportes.listar(fecha, fecha);
        assertEquals(2, eventos.size());
        assertEquals(12.5, eventos.stream().filter(e -> e.litros() != null).mapToDouble(e -> e.litros()).sum());
        assertTrue(eventos.stream().filter(e -> e.tipo().equals("Calidad")).allMatch(e -> e.litros() == null));
        assertTrue(reportes.listar(fecha.plusDays(1), fecha.plusDays(1)).isEmpty());
    }

    @Test void rechazaFechasInvertidas() {
        assertThrows(BusinessRuleException.class, () -> reportes.listar(LocalDate.of(2025, 2, 2), LocalDate.of(2025, 2, 1)));
    }
}
