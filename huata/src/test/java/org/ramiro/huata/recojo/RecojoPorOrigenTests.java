package org.ramiro.huata.recojo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.LugarAnalisis;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quien trae la leche (confirmado con el cliente, 2026-10-14): en ruta la recoge siempre un
 * acopiador; en una entrega directa el propio proveedor la lleva a planta, sin acopiador.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "calidad.demo@huata.pe")
class RecojoPorOrigenTests {

    private static final Instant DIA = Instant.parse("2036-04-06T12:00:00Z");

    @Autowired
    private RecojoService recojoService;

    @Autowired
    private ProductorRepository productores;

    @Autowired
    private AcopiadorRepository acopiadores;

    @Autowired
    private ControlCalidadRepository controles;

    private Productor proveedor;
    private Long acopiadorId;

    @BeforeEach
    void escenario() {
        proveedor = productores.findAll().get(0);
        acopiadorId = acopiadores.findByActivoTrueOrderByNombresAsc().stream()
                .findFirst()
                .orElseGet(() -> {
                    Acopiador a = new Acopiador();
                    a.setNombres("Acopiador");
                    a.setApellidos("De prueba");
                    return acopiadores.save(a);
                })
                .getId();
    }

    private RecojoRequest peticion(FuenteRecojo fuente, Long acopiador) {
        return new RecojoRequest(
                UUID.randomUUID().toString(), proveedor.getId(), acopiador, null, DIA, 75.0, 6.0, fuente,
                null, null, null, null, EstadoVisita.ACOPIADO, null, null, null, null);
    }

    @Test
    void unRecojoEnRutaSinAcopiadorSeRechaza() {
        assertThatThrownBy(() -> recojoService.registrar(peticion(FuenteRecojo.RUTA, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("acopiador");
    }

    @Test
    void sinFuenteSeTomaComoRutaYTambienExigeAcopiador() {
        assertThatThrownBy(() -> recojoService.registrar(peticion(null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void unaEntregaDirectaNoLlevaAcopiador() {
        assertThatThrownBy(() -> recojoService.registrar(peticion(FuenteRecojo.PLANTA, acopiadorId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("entrega directa");

        Recojo directa = recojoService.registrar(peticion(FuenteRecojo.PLANTA, null));
        assertThat(directa.getFuente()).isEqualTo(FuenteRecojo.PLANTA);
        assertThat(directa.getAcopiador()).isNull();
    }

    @Test
    void alRegistrarElRecojoEnRutaSeVinculaElAnalisisDelTesteador() {
        ControlCalidad campo = new ControlCalidad();
        campo.setProductor(proveedor);
        campo.setFechaHora(DIA.minusSeconds(3600));
        campo.setLugarAnalisis(LugarAnalisis.CAMPO);
        campo.setResultado(ResultadoCalidad.ACEPTADA);
        campo = controles.save(campo);

        Recojo r = recojoService.registrar(peticion(FuenteRecojo.RUTA, acopiadorId));

        assertThat(controles.findByRecojoId(r.getId())).map(ControlCalidad::getId).contains(campo.getId());
    }
}
