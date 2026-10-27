package org.ramiro.huata.common;

import java.util.List;

/**
 * Calculos geograficos compartidos. Unica formula de Haversine del backend: antes vivia duplicada
 * dentro de {@code RutaService} (reordenamiento por cercania) y ahora tambien la necesita
 * {@code RecojoService} (distancia entre el GPS del acopiador y la ubicacion del proveedor).
 */
public final class GeoUtils {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private GeoUtils() {}

    /** Un vertice del poligono de una zona, como {@code [latitud, longitud]}. */
    public static double[] punto(double latitud, double longitud) {
        return new double[] {latitud, longitud};
    }

    /**
     * true si la coordenada cae dentro del poligono (algoritmo de "ray casting"). Un poligono con
     * menos de 3 vertices no delimita area, asi que no encierra nada: se devuelve false. El borde
     * puede caer de un lado u otro por el redondeo, que es aceptable para una geocerca de zona.
     */
    public static boolean dentroDePoligono(double latitud, double longitud, List<double[]> vertices) {
        if (vertices == null || vertices.size() < 3) return false;
        boolean dentro = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            double yi = vertices.get(i)[0];
            double xi = vertices.get(i)[1];
            double yj = vertices.get(j)[0];
            double xj = vertices.get(j)[1];
            boolean cruza = (yi > latitud) != (yj > latitud)
                    && longitud < (xj - xi) * (latitud - yi) / (yj - yi) + xi;
            if (cruza) dentro = !dentro;
        }
        return dentro;
    }

    /** Distancia en linea recta entre dos coordenadas (formula de Haversine), en kilometros. */
    public static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA_KM * c;
    }

    /** Igual que {@link #distanciaKm}, en metros — la unidad que importa para validar cercania GPS. */
    public static double distanciaMetros(double lat1, double lon1, double lat2, double lon2) {
        return distanciaKm(lat1, lon1, lat2, lon2) * 1000.0;
    }
}
