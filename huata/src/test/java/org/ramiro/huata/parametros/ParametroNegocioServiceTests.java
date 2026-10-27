package org.ramiro.huata.parametros;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parametros de negocio: el {@code BusinessSettings} de la Fase 1, modelado como clave/valor.
 *
 * <p>Lo que importa probar aqui es que {@code guardar} sea un <i>upsert</i> por clave y no un alta
 * ciega. Si insertara siempre, la tabla acumularia varias filas con la misma clave y quien leyera el
 * parametro obtendria una u otra segun el orden de la consulta: una tarifa o un limite de calidad
 * distinto en cada arranque, sin que nadie hubiera cambiado nada.
 */
@SpringBootTest
@Transactional
class ParametroNegocioServiceTests {

    @Autowired
    private ParametroNegocioService servicio;

    @Autowired
    private ParametroNegocioRepository repository;

    @Test
    void guardarUnaClaveNuevaLaCrea() {
        ParametroNegocio creado = servicio.guardar(
                new ParametroNegocioRequest("huata.prueba.clave", "10", "Parametro de prueba"));

        assertThat(creado.getId()).isNotNull();
        assertThat(repository.findByClave("huata.prueba.clave")).isPresent();
    }

    @Test
    void guardarLaMismaClaveDosVecesActualizaEnVezDeDuplicar() {
        ParametroNegocio primero = servicio.guardar(
                new ParametroNegocioRequest("huata.prueba.upsert", "10", "Original"));

        ParametroNegocio segundo = servicio.guardar(
                new ParametroNegocioRequest("huata.prueba.upsert", "25", "Corregido"));

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(segundo.getValor()).isEqualTo("25");
        assertThat(segundo.getDescripcion()).isEqualTo("Corregido");
        assertThat(repository.findAll().stream()
                        .filter(p -> "huata.prueba.upsert".equals(p.getClave()))
                        .count())
                .isEqualTo(1);
    }

    @Test
    void listarDevuelveLosParametrosGuardados() {
        servicio.guardar(new ParametroNegocioRequest("huata.prueba.listado", "x", null));

        assertThat(servicio.listar()).extracting(ParametroNegocio::getClave).contains("huata.prueba.listado");
    }
}
