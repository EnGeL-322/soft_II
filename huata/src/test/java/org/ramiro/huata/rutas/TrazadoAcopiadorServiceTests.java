package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Pintar ruta del dia" y "Asignar como ruta predeterminada".
 *
 * <p>Lo que se fija aqui son las reglas que hacen utilizable la funcion: que volver a pintar corrija
 * el dibujo en vez de acumular versiones, que el predeterminado sea <b>una copia</b> y no una
 * referencia al trazado del dia (si no, corregir el dibujo de hoy cambiaria en silencio el recorrido
 * habitual), que sea por acopiador y no por ruta, y que quitarlo devuelva la geometria de la ruta como
 * respaldo.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "acopiador.demo@huata.pe")
class TrazadoAcopiadorServiceTests {

    /**
     * Una fecha pasada fija, no 2035 como el resto de las pruebas de acopio: el servicio rechaza a
     * proposito pintar un recorrido de una fecha futura, asi que aqui no sirve una fecha lejana. Y
     * pasada fija en vez de {@code now()} para que la prueba no dependa del dia en que se corre.
     * Nada siembra esta tabla, asi que el dia elegido esta siempre vacio.
     */
    private static final LocalDate DIA = LocalDate.of(2026, 1, 15);
    private static final String USUARIO_ACOPIADOR = "acopiador.demo@huata.pe";

    @Autowired
    private TrazadoAcopiadorService service;

    @Autowired
    private AcopiadorRepository acopiadorRepository;

    @Autowired
    private RutaPuntoRepository rutaPuntoRepository;

    private Acopiador acopiador;

    @BeforeEach
    void escenario() {
        acopiador = acopiadorRepository.findByUsuario_UsernameIgnoreCase(USUARIO_ACOPIADOR).orElseThrow();
    }

    private static List<TrazadoPuntoRequest> trazo(int puntos) {
        return java.util.stream.IntStream.range(0, puntos)
                .mapToObj(i -> new TrazadoPuntoRequest(i, -15.65 - i * 0.001, -69.98 - i * 0.001))
                .toList();
    }

    private TrazadoAcopiadorResponse pintar(List<TrazadoPuntoRequest> puntos, boolean sigueDesignada) {
        return service.guardarDelDia(
                USUARIO_ACOPIADOR, new TrazadoDiaRequest(DIA, sigueDesignada, null, puntos));
    }

    // ------------------------------------------------------------------- Pintar (la app del acopiador)

    @Test
    void elAcopiadorPintaSuRecorridoDelDiaYQuedaColgadoDeSuRuta() {
        TrazadoAcopiadorResponse pintado = pintar(trazo(4), false);

        assertThat(pintado.tipo()).isEqualTo(TipoTrazado.DIA);
        assertThat(pintado.fecha()).isEqualTo(DIA);
        assertThat(pintado.acopiadorId()).isEqualTo(acopiador.getId());
        assertThat(pintado.rutaId()).isEqualTo(acopiador.getRuta().getId());
        assertThat(pintado.puntos()).hasSize(4);
        assertThat(pintado.sigueRutaDesignada()).isFalse();
    }

    /** "Si va igualmente": pintar tambien vale cuando el acopiador si sigue la ruta designada. */
    @Test
    void tambienPuedePintarAunqueVayaPorLaRutaDesignada() {
        assertThat(pintar(trazo(3), true).sigueRutaDesignada()).isTrue();
    }

    /**
     * Volver a guardar corrige el dibujo del dia; no crea un segundo trazado. Es lo que hace que un
     * reintento de la cola offline tras un corte a medio envio no duplique el recorrido.
     */
    @Test
    void volverAPintarElMismoDiaReemplazaElTrazoYNoAcumula() {
        Long primero = pintar(trazo(5), false).id();
        TrazadoAcopiadorResponse corregido = pintar(trazo(2), false);

        assertThat(corregido.id()).isEqualTo(primero);
        assertThat(corregido.puntos()).hasSize(2);
        assertThat(service.pintadosDelDia(DIA))
                .filteredOn(t -> t.acopiadorId().equals(acopiador.getId()))
                .hasSize(1);
    }

    /** El orden de los vertices ES el recorrido: se renumera 0..n y no se confia en el cliente. */
    @Test
    void losPuntosSeRenumeranEnOrdenAunqueLleguenDesordenados() {
        TrazadoAcopiadorResponse pintado = service.guardarDelDia(
                USUARIO_ACOPIADOR,
                new TrazadoDiaRequest(
                        DIA,
                        false,
                        null,
                        List.of(
                                new TrazadoPuntoRequest(9, -15.60, -69.90),
                                new TrazadoPuntoRequest(1, -15.65, -69.95),
                                new TrazadoPuntoRequest(4, -15.62, -69.92))));

        assertThat(pintado.puntos()).extracting(TrazadoPuntoResponse::secuencia).containsExactly(0, 1, 2);
        // El primero sigue siendo el que traia la secuencia mas baja, no el que llego primero.
        assertThat(pintado.puntos().get(0).latitud()).isEqualTo(-15.65);
    }

    @Test
    void noSePuedePintarUnRecorridoDeUnaFechaFutura() {
        assertThatThrownBy(() -> service.guardarDelDia(
                        USUARIO_ACOPIADOR,
                        new TrazadoDiaRequest(LocalDate.now().plusDays(10), false, null, trazo(3))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("fecha futura");
    }

    @Test
    void unUsuarioQueNoEsAcopiadorNoPuedePintar() {
        assertThatThrownBy(() -> service.guardarDelDia(
                        "calidad.demo@huata.pe", new TrazadoDiaRequest(DIA, false, null, trazo(3))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no esta vinculado a un acopiador");
    }

    @Test
    void borrarElTrazadoDelDiaLoQuitaDeLaLista() {
        pintar(trazo(3), false);
        service.borrarDelDia(USUARIO_ACOPIADOR, DIA);

        assertThat(service.pintadosDelDia(DIA))
                .noneSatisfy(t -> assertThat(t.acopiadorId()).isEqualTo(acopiador.getId()));
    }

    // ------------------------------------------------------- Predeterminar (administracion, en la web)

    @Test
    void administracionConvierteElTrazadoDelDiaEnElPredeterminadoDeEseAcopiador() {
        TrazadoAcopiadorResponse pintado = pintar(trazo(4), false);

        TrazadoAcopiadorResponse predeterminado = service.predeterminar(pintado.id(), "admin@huata.pe");

        assertThat(predeterminado.tipo()).isEqualTo(TipoTrazado.PREDETERMINADO);
        assertThat(predeterminado.fecha()).isNull();
        assertThat(predeterminado.acopiadorId()).isEqualTo(acopiador.getId());
        assertThat(predeterminado.origenTrazadoId()).isEqualTo(pintado.id());
        assertThat(predeterminado.predeterminadoPor()).isEqualTo("admin@huata.pe");
        assertThat(predeterminado.puntos()).hasSize(4);
    }

    /**
     * El corazon de la decision: el predeterminado es una COPIA. Si referenciara la fila del dia, el
     * acopiador corrigiendo su dibujo de hoy cambiaria su recorrido habitual sin que nadie lo aprobara.
     */
    @Test
    void corregirElTrazadoDelDiaNoCambiaElPredeterminadoYaGuardado() {
        TrazadoAcopiadorResponse pintado = pintar(trazo(6), false);
        service.predeterminar(pintado.id(), "admin@huata.pe");

        pintar(trazo(2), false);

        assertThat(service.miTrazado(USUARIO_ACOPIADOR, DIA).predeterminado().puntos()).hasSize(6);
        assertThat(service.miTrazado(USUARIO_ACOPIADOR, DIA).delDia().puntos()).hasSize(2);
    }

    /** Es "el" recorrido habitual, no una lista: predeterminar otro reemplaza el anterior. */
    @Test
    void predeterminarOtroTrazadoReemplazaElAnterior() {
        service.predeterminar(pintar(trazo(5), false).id(), "admin@huata.pe");
        service.predeterminar(pintar(trazo(3), false).id(), "admin@huata.pe");

        assertThat(service.predeterminados())
                .filteredOn(t -> t.acopiadorId().equals(acopiador.getId()))
                .singleElement()
                .satisfies(t -> assertThat(t.puntos()).hasSize(3));
    }

    /** Predeterminar el predeterminado no tiene sentido y se rechaza en vez de duplicarlo. */
    @Test
    void noSePuedePredeterminarUnTrazadoQueYaEsPredeterminado() {
        TrazadoAcopiadorResponse predeterminado = service.predeterminar(pintar(trazo(3), false).id(), "admin@huata.pe");

        assertThatThrownBy(() -> service.predeterminar(predeterminado.id(), "admin@huata.pe"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya es el predeterminado");
    }

    @Test
    void quitarElPredeterminadoDejaAlAcopiadorSinRecorridoPropio() {
        service.predeterminar(pintar(trazo(3), false).id(), "admin@huata.pe");

        service.quitarPredeterminado(acopiador.getId());

        assertThat(service.miTrazado(USUARIO_ACOPIADOR, DIA).predeterminado()).isNull();
        assertThatThrownBy(() -> service.quitarPredeterminado(acopiador.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    /**
     * El respaldo que acordamos: sin trazado propio, la referencia sigue siendo la geometria de la
     * RUTA. Eso es lo que evita que activar esta funcion deje sin recorrido a quien no la use.
     */
    @Test
    void sinTrazadoPropioLaReferenciaSigueSiendoLaGeometriaDeLaRuta() {
        Long rutaId = acopiador.getRuta().getId();
        int puntosDeLaRuta = rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId).size();

        MiTrazadoResponse mio = service.miTrazado(USUARIO_ACOPIADOR, DIA);

        assertThat(mio.predeterminado()).isNull();
        assertThat(mio.delDia()).isNull();
        assertThat(mio.rutaId()).isEqualTo(rutaId);
        assertThat(mio.deLaRuta()).hasSize(puntosDeLaRuta);
    }

    /**
     * El predeterminado es del acopiador, no de la ruta: promoverlo no debe reescribir
     * {@code ruta_puntos}, que la comparten todos los acopiadores de esa ruta.
     */
    @Test
    void predeterminarNoTocaLaGeometriaCompartidaDeLaRuta() {
        Long rutaId = acopiador.getRuta().getId();
        var antes = rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId).stream()
                .map(p -> p.getLatitud() + "," + p.getLongitud())
                .toList();

        service.predeterminar(pintar(trazo(4), false).id(), "admin@huata.pe");

        assertThat(rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId).stream()
                        .map(p -> p.getLatitud() + "," + p.getLongitud()))
                .isEqualTo(antes);
    }
}
