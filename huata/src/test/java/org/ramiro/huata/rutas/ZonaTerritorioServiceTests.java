package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renombrar y dar de baja la zona: el nombre vive a la vez en Ruta.zona y en ZonaPoligono.zona
 * (texto libre, decision C-05), asi que lo que se prueba aqui es que las dos tablas queden
 * consistentes y que no se pueda dejar una ruta sin zona por la puerta de atras.
 */
@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ZonaTerritorioServiceTests {

    @Autowired
    private RutaService rutaService;

    @Test
    void renombrarCambiaElNombreEnLasRutasYEnElPoligonoALaVez() {
        String zona = "Zona renombrar " + System.nanoTime();
        String nueva = zona + " NORTE";
        Ruta ruta = rutaService.crearRuta(new RutaRequest("Ruta de " + zona, zona, null));
        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                zona,
                "#123456",
                List.of(
                        new ZonaPoligonoPuntoRequest(1, -15.0, -70.0),
                        new ZonaPoligonoPuntoRequest(2, -15.1, -70.1),
                        new ZonaPoligonoPuntoRequest(3, -15.2, -70.2))));

        rutaService.renombrarZona(new ZonaRenombrarRequest(zona, nueva));

        assertThat(rutaService.obtenerRuta(ruta.getId()).getZona()).isEqualTo(nueva);
        assertThat(rutaService.poligonosDeZona()).anyMatch(p -> p.zona().equals(nueva));
        assertThat(rutaService.poligonosDeZona()).noneMatch(p -> p.zona().equals(zona));
    }

    @Test
    void renombrarFuncionaAunqueLaZonaTodaviaNoTengaPoligono() {
        String zona = "Zona sin poligono " + System.nanoTime();
        Ruta ruta = rutaService.crearRuta(new RutaRequest("Ruta de " + zona, zona, null));

        rutaService.renombrarZona(new ZonaRenombrarRequest(zona, zona + " SUR"));

        assertThat(rutaService.obtenerRuta(ruta.getId()).getZona()).isEqualTo(zona + " SUR");
    }

    @Test
    void renombrarHaciaUnaZonaQueYaExisteFallaEnVezDeFusionarlas() {
        String origen = "Zona origen " + System.nanoTime();
        String destino = "Zona destino " + System.nanoTime();
        rutaService.crearRuta(new RutaRequest("Ruta origen", origen, null));
        rutaService.crearRuta(new RutaRequest("Ruta destino", destino, null));

        assertThatThrownBy(() -> rutaService.renombrarZona(new ZonaRenombrarRequest(origen, destino)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(destino);
    }

    @Test
    void renombrarUnaZonaInexistenteFalla() {
        assertThatThrownBy(() ->
                        rutaService.renombrarZona(new ZonaRenombrarRequest("Zona que no existe " + System.nanoTime(), "Otra")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void eliminarUnaZonaConRutasActivasFallaParaNoDejarRutasSinZona() {
        String zona = "Zona con ruta " + System.nanoTime();
        rutaService.crearRuta(new RutaRequest("Ruta viva", zona, null));
        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                zona,
                null,
                List.of(
                        new ZonaPoligonoPuntoRequest(1, -15.0, -70.0),
                        new ZonaPoligonoPuntoRequest(2, -15.1, -70.1),
                        new ZonaPoligonoPuntoRequest(3, -15.2, -70.2))));

        assertThatThrownBy(() -> rutaService.eliminarZona(zona))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ruta(s) activa(s)");
        assertThat(rutaService.poligonosDeZona()).anyMatch(p -> p.zona().equals(zona));
    }

    @Test
    void eliminarUnaZonaSinRutasActivasBorraSuPoligono() {
        String zona = "Zona vacia " + System.nanoTime();
        Ruta ruta = rutaService.crearRuta(new RutaRequest("Ruta a dar de baja", zona, null));
        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                zona,
                null,
                List.of(
                        new ZonaPoligonoPuntoRequest(1, -15.0, -70.0),
                        new ZonaPoligonoPuntoRequest(2, -15.1, -70.1),
                        new ZonaPoligonoPuntoRequest(3, -15.2, -70.2))));
        rutaService.inactivarRuta(ruta.getId());

        rutaService.eliminarZona(zona);

        assertThat(rutaService.poligonosDeZona()).noneMatch(p -> p.zona().equals(zona));
    }

    /**
     * Pedir los puntos de una ruta que no existe devolvia 200 con lista vacia, indistinguible de una
     * ruta real todavia sin trazado: el cliente no podia saber si el id estaba mal o si faltaba
     * dibujar. Ahora falla igual que su endpoint hermano, proveedoresDeRuta.
     */
    @Test
    void pedirLosPuntosDeUnaRutaInexistenteFallaEnVezDeDevolverListaVacia() {
        assertThatThrownBy(() -> rutaService.puntosDeRuta(99_999_999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unaRutaRealSinTrazadoSiDevuelveListaVacia() {
        Ruta ruta = rutaService.crearRuta(new RutaRequest("Ruta sin trazado " + System.nanoTime(), "ZONA " + System.nanoTime(), null));

        assertThat(rutaService.puntosDeRuta(ruta.getId())).isEmpty();
    }

    @Test
    void laRutaRenombradaSigueApareciendoEnLaVistaTerritorialConSuNuevoNombre() {
        String zona = "Zona territorial " + System.nanoTime();
        String nueva = zona + " ALTA";
        rutaService.crearRuta(new RutaRequest("Ruta territorial " + System.nanoTime(), zona, "Descripcion"));

        rutaService.renombrarZona(new ZonaRenombrarRequest(zona, nueva));

        assertThat(rutaService.zonasAsignadas()).anyMatch(z -> z.zona().equals(nueva));
        assertThat(rutaService.buscarRutas(nueva, PageRequest.of(0, 20)).content()).isNotEmpty();
    }
}
