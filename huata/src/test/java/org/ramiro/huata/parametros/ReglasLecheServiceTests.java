package org.ramiro.huata.parametros;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.parametros.ReglasLecheService.ReglasLeche;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la leche (V33). Se guardan como texto en parametros_negocio: lo que se fija aqui es que
 * un valor mal escrito no llega a la regla que retira proveedores, y que sin configurar nada rige la
 * regla vigente (RN-07: retiro a la segunda deteccion).
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ReglasLecheServiceTests {

    @Autowired private ReglasLecheService service;
    @Autowired private ParametroNegocioRepository repository;

    private void escribirCrudo(String clave, String valor) {
        ParametroNegocio p = repository.findByClave(clave).orElseGet(ParametroNegocio::new);
        p.setClave(clave);
        p.setValor(valor);
        repository.saveAndFlush(p);
    }

    @Test
    void sinConfigurarRigeLaReglaVigente() {
        repository.findByClave(ReglasLecheService.DETECCIONES_RETIRO).ifPresent(repository::delete);
        repository.findByClave(ReglasLecheService.COSTO_LECHE).ifPresent(repository::delete);
        repository.flush();

        ReglasLeche r = service.vigentes();

        assertThat(r.deteccionesParaRetiro()).isEqualTo(2);
        assertThat(r.costoLecheLitro()).isNull();
    }

    @Test
    void loGuardadoSeLeeTalCual() {
        service.guardar(new ReglasLeche(3, false, false, new BigDecimal("1.95")));

        assertThat(service.vigentes()).isEqualTo(new ReglasLeche(3, false, false, new BigDecimal("1.95")));
    }

    /** "dos" en vez de 2, o un costo negativo, no pueden llegar a las reglas: rige el valor por defecto. */
    @Test
    void unValorMalEscritoNoLlegaALaRegla() {
        escribirCrudo(ReglasLecheService.DETECCIONES_RETIRO, "dos");
        escribirCrudo(ReglasLecheService.COSTO_LECHE, "-3");
        escribirCrudo(ReglasLecheService.INCLUYE_OBSERVADA, "quizas");

        ReglasLeche r = service.vigentes();

        assertThat(r.deteccionesParaRetiro()).isEqualTo(2);
        assertThat(r.costoLecheLitro()).isNull();
        assertThat(r.observadaEsMalaCalidad()).isTrue();
    }

    @Test
    void dejarElCostoVacioVuelveALaTarifaDelDia() {
        service.guardar(new ReglasLeche(2, true, true, new BigDecimal("2.00")));
        service.guardar(new ReglasLeche(2, true, true, null));

        assertThat(service.vigentes().costoLecheLitro()).isNull();
    }
}
