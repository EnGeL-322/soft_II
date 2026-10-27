package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.common.GeoUtils;
import org.ramiro.huata.rutas.ZonaPoligono;
import org.ramiro.huata.rutas.ZonaPoligonoPunto;
import org.ramiro.huata.rutas.ZonaPoligonoPuntoRepository;
import org.ramiro.huata.rutas.ZonaPoligonoRepository;

class UbicacionSimuladaServiceTests {

    /** Una "L": el rectángulo que la encierra tiene una esquina vacía donde no debe caer ningún punto. */
    private static final List<double[]> ZONA_EN_L = List.of(
            GeoUtils.punto(-15.80, -70.10),
            GeoUtils.punto(-15.80, -70.00),
            GeoUtils.punto(-15.85, -70.00),
            GeoUtils.punto(-15.85, -70.05),
            GeoUtils.punto(-15.90, -70.05),
            GeoUtils.punto(-15.90, -70.10));

    private final ZonaPoligonoRepository poligonos = mock(ZonaPoligonoRepository.class);
    private final ZonaPoligonoPuntoRepository vertices = mock(ZonaPoligonoPuntoRepository.class);
    private final ProductorUbicacionService ubicaciones = mock(ProductorUbicacionService.class);
    private final ProductorRepository productores = mock(ProductorRepository.class);

    private UbicacionSimuladaService servicio(boolean activa) {
        return new UbicacionSimuladaService(activa, poligonos, vertices, ubicaciones, productores, new Random(7));
    }

    private static Productor proveedorEn(String zona) {
        Productor p = new Productor();
        p.setCodigo("PRV-TEST");
        p.setZonaPrincipal(zona);
        return p;
    }

    @Test
    void todosLosPuntosCaenDentroDelPoligonoAunqueNoSeaConvexo() {
        UbicacionSimuladaService s = servicio(true);
        for (int i = 0; i < 2000; i++) {
            double[] p = s.puntoAlAzarDentro(ZONA_EN_L);
            assertThat(GeoUtils.dentroDePoligono(p[0], p[1], ZONA_EN_L)).isTrue();
        }
    }

    @Test
    void alDarDeAltaGuardaLaViviendaDentroDeSuZona() {
        ZonaPoligono zona = mock(ZonaPoligono.class);
        when(zona.getId()).thenReturn(3L);
        when(poligonos.findByZonaIgnoreCase("FAON")).thenReturn(Optional.of(zona));
        when(vertices.findByZonaPoligono_IdOrderBySecuenciaAsc(3L)).thenReturn(ZONA_EN_L.stream().map(v -> {
            ZonaPoligonoPunto punto = new ZonaPoligonoPunto();
            punto.setLatitud(v[0]);
            punto.setLongitud(v[1]);
            return punto;
        }).toList());
        Productor proveedor = proveedorEn(" FAON ");
        when(ubicaciones.registrarSimulada(any(), anyDouble(), anyDouble())).thenReturn(new ProductorUbicacion());

        assertThat(servicio(true).simularVivienda(proveedor)).isPresent();

        verify(ubicaciones).registrarSimulada(
                eq(proveedor),
                doubleThat(lat -> lat >= -15.90 && lat <= -15.80),
                doubleThat(lng -> lng >= -70.10 && lng <= -70.00));
    }

    @Test
    void apagadaNoTocaNada() {
        assertThat(servicio(false).simularVivienda(proveedorEn("FAON"))).isEmpty();
        verifyNoInteractions(poligonos, vertices, ubicaciones, productores);
    }

    @Test
    void sinPoligonoDibujadoNoInventaUbicacion() {
        when(poligonos.findByZonaIgnoreCase("ZONA SIN DIBUJO")).thenReturn(Optional.empty());

        assertThat(servicio(true).simularVivienda(proveedorEn("ZONA SIN DIBUJO"))).isEmpty();
        verify(ubicaciones, never()).registrarSimulada(any(), anyDouble(), anyDouble());
    }

    /** Prepara el limite en L para la zona indicada. */
    private void conLimite(String zona) {
        ZonaPoligono z = mock(ZonaPoligono.class);
        when(z.getId()).thenReturn(3L);
        when(poligonos.findByZonaIgnoreCase(zona)).thenReturn(Optional.of(z));
        when(vertices.findByZonaPoligono_IdOrderBySecuenciaAsc(3L)).thenReturn(ZONA_EN_L.stream().map(v -> {
            ZonaPoligonoPunto punto = new ZonaPoligonoPunto();
            punto.setLatitud(v[0]);
            punto.setLongitud(v[1]);
            return punto;
        }).toList());
        when(ubicaciones.registrarSimulada(any(), anyDouble(), anyDouble())).thenReturn(new ProductorUbicacion());
    }

    /** Pedido del cliente: al pasar a una ruta de otra zona, el punto se traslada al limite nuevo. */
    @Test
    void alPasarAUnaRutaDeOtraZonaLaViviendaSeSorteaEnElLimiteNuevo() {
        conLimite("YASIN");
        org.ramiro.huata.rutas.Ruta ruta = new org.ramiro.huata.rutas.Ruta();
        ruta.setZona("YASIN");
        Productor proveedor = proveedorEn("YASIN");
        proveedor.setRuta(ruta);

        assertThat(servicio(true).alCambiarDeZona(proveedor, "FAON")).isPresent();

        verify(ubicaciones).registrarSimulada(
                eq(proveedor),
                doubleThat(lat -> lat >= -15.90 && lat <= -15.80),
                doubleThat(lng -> lng >= -70.10 && lng <= -70.00));
    }

    @Test
    void cambiarDeRutaDentroDeLaMismaZonaConservaElPunto() {
        Productor proveedor = proveedorEn("YASIN");

        assertThat(servicio(true).alCambiarDeZona(proveedor, " yasin ")).isEmpty();

        verify(ubicaciones, never()).registrarSimulada(any(), anyDouble(), anyDouble());
    }

    @Test
    void completaSoloALosQueNoTienenPunto() {
        conLimite("YASIN");
        Productor sinPunto = proveedorEn("YASIN");
        sinPunto.setId(1L);
        Productor conPunto = proveedorEn("YASIN");
        conPunto.setId(2L);
        when(productores.findByActivoTrueOrderByNombresAscApellidosAsc()).thenReturn(List.of(sinPunto, conPunto));
        when(ubicaciones.ultimasPorProductor(List.of(1L, 2L))).thenReturn(java.util.Map.of(2L, new ProductorUbicacion()));

        assertThat(servicio(true).completarFaltantes(null)).isEqualTo(1);

        verify(ubicaciones).registrarSimulada(eq(sinPunto), anyDouble(), anyDouble());
        verify(ubicaciones, never()).registrarSimulada(eq(conPunto), anyDouble(), anyDouble());
    }
}
