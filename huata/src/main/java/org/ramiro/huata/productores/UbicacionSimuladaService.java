package org.ramiro.huata.productores;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.ramiro.huata.common.GeoUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.ramiro.huata.rutas.ZonaPoligonoPuntoRepository;
import org.ramiro.huata.rutas.ZonaPoligonoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SOLO PARA DEMOSTRACIÓN. Al dar de alta un proveedor le asigna una vivienda en un punto al azar
 * DENTRO del polígono de su zona, para poder mostrar el acopio de punta a punta (mapa web, app del
 * acopiador, cercanía al registrar la leche) sin salir a tomar la ubicación real de cada uno.
 *
 * <p>La vivienda cae siempre dentro del limite de la zona de la RUTA asignada: si el proveedor pasa
 * a una ruta de otra zona (ficha, Rutas y mapa, solicitud aprobada o importacion), el punto se vuelve
 * a sortear dentro del limite de la zona nueva. Los proveedores que todavia no tienen punto (creados
 * antes de activar la simulacion, o cuya zona no tenia limite) lo reciben al arrancar el backend y
 * al dibujarse el limite de su zona.
 *
 * <p>Apagado por defecto ({@code huata.simulacion.ubicacion-proveedores=false}): en producción la
 * ubicación la toma el proveedor desde su app o administración en el mapa. La ubicación simulada
 * se guarda como una más del historial (tipo CASA) y queda marcada en la bitácora; la primera
 * ubicación real que se registre después la reemplaza en el mapa, porque gana la más reciente.
 */
@Slf4j
@Service
public class UbicacionSimuladaService {

    /** Intentos de muestreo dentro del rectángulo que encierra al polígono antes de usar el centro. */
    private static final int INTENTOS = 500;

    private final boolean activa;
    private final ZonaPoligonoRepository poligonos;
    private final ZonaPoligonoPuntoRepository vertices;
    private final ProductorUbicacionService ubicaciones;
    private final ProductorRepository productores;
    private final RandomGenerator azar;

    @Autowired
    public UbicacionSimuladaService(
            @Value("${huata.simulacion.ubicacion-proveedores:false}") boolean activa,
            ZonaPoligonoRepository poligonos,
            ZonaPoligonoPuntoRepository vertices,
            ProductorUbicacionService ubicaciones,
            ProductorRepository productores) {
        // java.util.Random y no RandomGenerator.getDefault(): la imagen Docker usa un JRE recortado
        // (jlink) sin el modulo jdk.random, y el algoritmo por defecto no existe ahi.
        this(activa, poligonos, vertices, ubicaciones, productores, new Random());
    }

    UbicacionSimuladaService(
            boolean activa,
            ZonaPoligonoRepository poligonos,
            ZonaPoligonoPuntoRepository vertices,
            ProductorUbicacionService ubicaciones,
            ProductorRepository productores,
            RandomGenerator azar) {
        this.activa = activa;
        this.poligonos = poligonos;
        this.vertices = vertices;
        this.ubicaciones = ubicaciones;
        this.productores = productores;
        this.azar = azar;
    }

    /**
     * Le pone una vivienda simulada dentro de su zona. No hace nada si la simulación está apagada,
     * si el proveedor no tiene zona o si su zona todavía no tiene polígono dibujado: el alta nunca
     * falla por esto.
     */
    public Optional<ProductorUbicacion> simularVivienda(Productor productor) {
        String zona = zonaDe(productor);
        if (!activa || zona == null) {
            return Optional.empty();
        }
        List<double[]> poligono = poligonos.findByZonaIgnoreCase(zona)
                .map(z -> vertices.findByZonaPoligono_IdOrderBySecuenciaAsc(z.getId()).stream()
                        .map(v -> GeoUtils.punto(v.getLatitud(), v.getLongitud()))
                        .toList())
                .orElse(List.of());
        if (poligono.size() < 3) {
            log.info("Sin polígono para la zona {}: no se simula la ubicación de {}", zona, productor.getCodigo());
            return Optional.empty();
        }
        double[] punto = puntoAlAzarDentro(poligono);
        return Optional.of(ubicaciones.registrarSimulada(productor, punto[0], punto[1]));
    }

    /**
     * Despues de cambiarle la ruta: si la nueva es de otra zona, su vivienda se sortea de nuevo dentro
     * del limite de esa zona. Dentro de la misma zona el punto ya cae en su limite y se conserva.
     */
    public Optional<ProductorUbicacion> alCambiarDeZona(Productor productor, String zonaAnterior) {
        String zonaNueva = zonaDe(productor);
        if (!activa || zonaNueva == null || normalizar(zonaNueva).equals(normalizar(zonaAnterior))) {
            return Optional.empty();
        }
        return simularVivienda(productor);
    }

    /**
     * Da vivienda a los proveedores activos que no tienen ninguna; con {@code zona} solo a los de esa
     * zona (se llama al dibujar su limite). Devuelve cuantos se ubicaron.
     */
    @Transactional
    public int completarFaltantes(String zona) {
        if (!activa) return 0;
        List<Productor> candidatos = productores.findByActivoTrueOrderByNombresAscApellidosAsc().stream()
                .filter(p -> zonaDe(p) != null)
                .filter(p -> zona == null || normalizar(zonaDe(p)).equals(normalizar(zona)))
                .toList();
        if (candidatos.isEmpty()) return 0;
        Map<Long, ProductorUbicacion> conPunto =
                ubicaciones.ultimasPorProductor(candidatos.stream().map(Productor::getId).toList());
        int ubicados = 0;
        for (Productor p : candidatos) {
            if (!conPunto.containsKey(p.getId()) && simularVivienda(p).isPresent()) ubicados++;
        }
        return ubicados;
    }

    /** Al arrancar con la simulacion activa, nadie se queda sin punto en el mapa. */
    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        int ubicados = completarFaltantes(null);
        if (ubicados > 0) log.info("Simulacion: {} proveedor(es) sin ubicacion recibieron una dentro de su zona", ubicados);
    }

    /** La zona que manda es la de la ruta asignada; si no tiene ruta, la que tenga anotada. */
    private static String zonaDe(Productor productor) {
        String zona = productor.getRuta() != null && productor.getRuta().getZona() != null
                ? productor.getRuta().getZona()
                : productor.getZonaPrincipal();
        return zona == null || zona.isBlank() ? null : zona.trim();
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /**
     * Muestreo por rechazo: puntos uniformes en el rectángulo que encierra al polígono hasta que uno
     * cae dentro. Para polígonos muy finos, tras {@link #INTENTOS} se usa el promedio de vértices.
     */
    double[] puntoAlAzarDentro(List<double[]> poligono) {
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLng = Double.MAX_VALUE, maxLng = -Double.MAX_VALUE;
        for (double[] v : poligono) {
            minLat = Math.min(minLat, v[0]);
            maxLat = Math.max(maxLat, v[0]);
            minLng = Math.min(minLng, v[1]);
            maxLng = Math.max(maxLng, v[1]);
        }
        for (int i = 0; i < INTENTOS; i++) {
            double lat = minLat + azar.nextDouble() * (maxLat - minLat);
            double lng = minLng + azar.nextDouble() * (maxLng - minLng);
            if (GeoUtils.dentroDePoligono(lat, lng, poligono)) return GeoUtils.punto(lat, lng);
        }
        double lat = poligono.stream().mapToDouble(v -> v[0]).average().orElseThrow();
        double lng = poligono.stream().mapToDouble(v -> v[1]).average().orElseThrow();
        return GeoUtils.punto(lat, lng);
    }
}
