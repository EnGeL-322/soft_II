package org.ramiro.huata.rutas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@WithMockUser(username = "admin@huata.pe")
class ZonaPoligonoServiceTests {

    @Autowired
    private RutaService rutaService;

    @Test
    void guardaCreaUnPoligonoNuevoConSusPuntosEnOrden() {
        var request = new ZonaPoligonoRequest(
                "Zona de prueba " + System.nanoTime(),
                "#2f6fc4",
                List.of(
                        new ZonaPoligonoPuntoRequest(1, -15.61, -69.98),
                        new ZonaPoligonoPuntoRequest(2, -15.62, -69.97),
                        new ZonaPoligonoPuntoRequest(3, -15.63, -69.99)));

        ZonaPoligonoResponse creado = rutaService.guardarPoligonoDeZona(request);

        assertThat(creado.id()).isNotNull();
        assertThat(creado.zona()).isEqualTo(request.zona());
        assertThat(creado.color()).isEqualTo("#2f6fc4");
        assertThat(creado.puntos()).extracting(ZonaPoligonoPuntoResponse::secuencia).containsExactly(1, 2, 3);
    }

    @Test
    void guardarDeNuevoLaMismaZonaReemplazaElPoligonoEnVezDeDuplicarlo() {
        String zona = "Zona reemplazo " + System.nanoTime();
        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                zona, "#111111", List.of(new ZonaPoligonoPuntoRequest(1, -15.0, -70.0), new ZonaPoligonoPuntoRequest(2, -15.1, -70.1))));

        ZonaPoligonoResponse actualizado = rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                zona.toUpperCase(),
                "#222222",
                List.of(new ZonaPoligonoPuntoRequest(1, -15.2, -70.2))));

        assertThat(actualizado.color()).isEqualTo("#222222");
        assertThat(actualizado.puntos()).hasSize(1);

        long coincidencias = rutaService.poligonosDeZona().stream()
                .filter(p -> p.zona().equalsIgnoreCase(zona))
                .count();
        assertThat(coincidencias).isEqualTo(1);
    }

    @Test
    void eliminarBorraElPoligonoYSusPuntos() {
        ZonaPoligonoResponse creado = rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(
                "Zona a eliminar " + System.nanoTime(),
                null,
                List.of(new ZonaPoligonoPuntoRequest(1, -15.0, -70.0), new ZonaPoligonoPuntoRequest(2, -15.1, -70.1))));

        rutaService.eliminarPoligonoDeZona(creado.id());

        assertThat(rutaService.poligonosDeZona()).noneMatch(p -> p.id().equals(creado.id()));
    }

    @Test
    void eliminarUnPoligonoInexistenteFalla() {
        assertThatThrownBy(() -> rutaService.eliminarPoligonoDeZona(-1L)).isInstanceOf(NotFoundException.class);
    }

    /**
     * Lo que pidió el cliente tras borrar un límite por accidente (2026-10-25): antes de cambiarlo o
     * borrarlo queda una copia, y esa copia lo devuelve tal cual estaba.
     */
    @Test
    void unLimiteBorradoPorAccidenteSeRestauraDesdeSuHistorial() {
        String zona = "Zona historial " + System.nanoTime();
        List<ZonaPoligonoPuntoRequest> original = List.of(
                new ZonaPoligonoPuntoRequest(1, -15.61, -69.98),
                new ZonaPoligonoPuntoRequest(2, -15.62, -69.97),
                new ZonaPoligonoPuntoRequest(3, -15.63, -69.99));
        ZonaPoligonoResponse creado = rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(zona, "#2f6fc4", original));
        assertThat(rutaService.historialDeLimite(zona)).as("crear no deja copia: no había nada antes").isEmpty();

        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(zona, "#2f6fc4", List.of(
                new ZonaPoligonoPuntoRequest(1, -15.0, -70.0),
                new ZonaPoligonoPuntoRequest(2, -15.1, -70.1),
                new ZonaPoligonoPuntoRequest(3, -15.2, -70.0),
                new ZonaPoligonoPuntoRequest(4, -15.1, -69.9))));
        rutaService.eliminarPoligonoDeZona(creado.id());
        assertThat(rutaService.poligonosDeZona()).noneMatch(p -> p.zona().equals(zona));

        List<ZonaPoligonoVersionResponse> historial = rutaService.historialDeLimite(zona);
        assertThat(historial).extracting(ZonaPoligonoVersionResponse::motivo)
                .containsExactly(RutaService.MOTIVO_ELIMINADO, RutaService.MOTIVO_MODIFICADO);
        assertThat(historial.get(1).puntos()).hasSize(3);

        ZonaPoligonoResponse restaurado = rutaService.restaurarLimite(historial.get(1).id());

        assertThat(restaurado.zona()).isEqualTo(zona);
        assertThat(restaurado.puntos())
                .extracting(ZonaPoligonoPuntoResponse::latitud)
                .containsExactly(-15.61, -15.62, -15.63);
        assertThat(rutaService.poligonosDeZona()).filteredOn(p -> p.zona().equals(zona)).hasSize(1);
    }

    @Test
    void eliminarLaZonaTambienGuardaSuLimiteEnElHistorial() {
        String zona = "Zona eliminada " + System.nanoTime();
        rutaService.guardarPoligonoDeZona(new ZonaPoligonoRequest(zona, "#111111", List.of(
                new ZonaPoligonoPuntoRequest(1, -15.0, -70.0),
                new ZonaPoligonoPuntoRequest(2, -15.1, -70.1),
                new ZonaPoligonoPuntoRequest(3, -15.2, -70.0))));

        rutaService.eliminarZona(zona);

        assertThat(rutaService.historialDeLimite(zona))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.motivo()).isEqualTo(RutaService.MOTIVO_ZONA_ELIMINADA);
                    assertThat(v.puntos()).hasSize(3);
                });
    }
}
