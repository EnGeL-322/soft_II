package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * El plan operativo de un dia cuenta lo recogido ese dia en hora de Lima. Cortado en UTC, lo
 * registrado despues de las 19:00 aparecia en el plan del dia siguiente.
 */
@SpringBootTest
@Transactional
class PlanOperativoDiaLimaTests {

    // 2035: sin recojos sembrados por DemoDataSeeder ese dia.
    private static final LocalDate DIA = LocalDate.of(2035, 3, 12);

    @Autowired
    private RutaService rutaService;

    @Autowired
    private RutaRepository rutaRepository;

    @Autowired
    private RecojoRepository recojoRepository;

    @Autowired
    private ProductorRepository productorRepository;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    @Test
    void loRecogidoALas20EnLimaCuentaParaEseDia() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta noche " + UUID.randomUUID());
        ruta.setZona("ZONA-NOCHE");
        ruta = rutaRepository.save(ruta);

        Recojo r = new Recojo();
        r.setClientUuid(UUID.randomUUID().toString());
        r.setProductor(productorRepository.findAll().get(0));
        r.setRuta(ruta);
        r.setFechaHora(DIA.atTime(20, 0).atZone(ZoneId.of("America/Lima")).toInstant());
        r.setLitros(25.0);
        recojoRepository.save(r);

        Long id = ruta.getId();
        assertThat(rutaService.planOperativo(DIA))
                .filteredOn(p -> p.rutaId().equals(id))
                .singleElement()
                .satisfies(p -> assertThat(p.litros()).isEqualTo(25.0));
        assertThat(rutaService.planOperativo(DIA.plusDays(1)))
                .filteredOn(p -> p.rutaId().equals(id))
                .singleElement()
                .satisfies(p -> assertThat(p.litros()).isZero());
    }

    /** Sin plan del dia, la web muestra al acopiador habitual de la ruta, como la app del acopiador. */
    @Test
    void sinAsignacionDelDiaElResponsableEsElAcopiadorHabitual() {
        Ruta ruta = new Ruta();
        ruta.setNombre("Ruta habitual " + UUID.randomUUID());
        ruta.setZona("ZONA-HABITUAL");
        ruta = rutaRepository.save(ruta);

        Acopiador acopiador = new Acopiador();
        acopiador.setNombres("Rosa");
        acopiador.setApellidos("Habitual");
        acopiador.setRuta(ruta);
        acopiador = acopiadorRepository.save(acopiador);

        Long id = ruta.getId();
        Long acopiadorId = acopiador.getId();
        assertThat(rutaService.planOperativo(DIA))
                .filteredOn(p -> p.rutaId().equals(id))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.asignacionId()).isNull();
                    assertThat(p.acopiadorId()).isEqualTo(acopiadorId);
                    assertThat(p.acopiadorNombre()).isEqualTo("Rosa Habitual");
                    assertThat(p.estado()).isEqualTo(EstadoPlanRuta.ASIGNADA);
                });
    }
}
