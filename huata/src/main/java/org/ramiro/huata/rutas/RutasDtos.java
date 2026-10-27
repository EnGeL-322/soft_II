package org.ramiro.huata.rutas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorUbicacion;
import org.ramiro.huata.productores.TipoUbicacion;

record RutaRequest(@NotBlank String nombre, String zona, String descripcion) {}

record RutaResponse(Long id, String nombre, String zona, String descripcion, boolean activo) {
    static RutaResponse from(Ruta r) {
        return new RutaResponse(r.getId(), r.getNombre(), r.getZona(), r.getDescripcion(), r.isActivo());
    }
}

record AsignacionRutaRequest(@NotNull Long rutaId, @NotNull Long acopiadorId, @NotNull LocalDate fecha, String movilidad) {}

record AsignacionActualizarRequest(String movilidad, String observacion) {}

record AsignacionRutaResponse(
        Long id,
        Long rutaId,
        String rutaNombre,
        // Zona de la ruta: la app del acopiador la usa para saber dentro de que poligono debe
        // permanecer mientras recorre (geocerca); null si la ruta no tiene zona asignada.
        String zona,
        Long acopiadorId,
        String acopiadorNombre,
        LocalDate fecha,
        String movilidad,
        String observacion) {
    static AsignacionRutaResponse from(AsignacionRuta a) {
        return new AsignacionRutaResponse(
                a.getId(),
                a.getRuta().getId(),
                a.getRuta().getNombre(),
                a.getRuta().getZona(),
                a.getAcopiador().getId(),
                a.getAcopiador().getNombres() + " " + a.getAcopiador().getApellidos(),
                a.getFecha(),
                a.getMovilidad(),
                a.getObservacion());
    }
}

// ---- Geometria de la ruta (2.1.3-2.1.5) ----

record RutaPuntoRequest(@NotNull Integer secuencia, @NotNull Double latitud, @NotNull Double longitud) {}

/** Reemplaza TODOS los puntos de la ruta de una vez: es un poligono, no altas/bajas sueltas. */
record RutaPuntosRequest(@NotEmpty List<@Valid RutaPuntoRequest> puntos) {}

record RutaPuntoResponse(Long id, Integer secuencia, Double latitud, Double longitud) {
    static RutaPuntoResponse from(RutaPunto p) {
        return new RutaPuntoResponse(p.getId(), p.getSecuencia(), p.getLatitud(), p.getLongitud());
    }
}

/**
 * Un punto de acopio nuevo (por ejemplo, un destino que decide el administrador) para agregar al
 * trazo SIN reemplazar los demas puntos, a diferencia de {@link RutaPuntosRequest}.
 */
record PuntoAcopioRequest(@NotNull Double latitud, @NotNull Double longitud) {}

/**
 * Resultado de agregar un punto de acopio: el trazo queda con el punto nuevo y los proveedores
 * quedan reordenados por cercania real a partir de ese punto (RutaService.reordenarPorCercania), asi
 * que la pantalla puede refrescar mapa y tabla con una sola respuesta.
 */
record PuntoAcopioAgregadoResponse(List<RutaPuntoResponse> puntos, List<ProveedorDeRutaResponse> proveedores) {}

/** Origen opcional para reordenar; sin el, se usa el primer punto del trazo de la ruta. */
record ReordenarRutaRequest(Double latitud, Double longitud) {}

// ---- Proveedores de la ruta, en orden de visita (2.1.6) ----

/**
 * El proveedor de la ruta viaja con su ultima ubicacion conocida (puede no tenerla) para que el
 * mapa del trazado pueda dibujarlo sin una peticion por proveedor.
 */
record ProveedorDeRutaResponse(
        Long id,
        String codigo,
        String nombres,
        String apellidos,
        Integer ordenEnRuta,
        Double latitud,
        Double longitud,
        TipoUbicacion tipoUbicacion,
        LocalDate fechaUbicacion,
        // `activo` (baja logica) no es lo mismo que el estado: un proveedor PENDIENTE_APROBACION o
        // RETIRADO sigue "activo" y aparecia en la ruta del acopiador, que al registrar su leche
        // recibia un rechazo. Con el estado, cada pantalla decide a quien mostrar.
        org.ramiro.huata.productores.EstadoProductor estado) {

    static ProveedorDeRutaResponse from(Productor p, ProductorUbicacion ubicacion) {
        return new ProveedorDeRutaResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombres(),
                p.getApellidos(),
                p.getOrdenEnRuta(),
                ubicacion == null ? null : ubicacion.getLatitud(),
                ubicacion == null ? null : ubicacion.getLongitud(),
                ubicacion == null ? null : ubicacion.getTipo(),
                ubicacion == null ? null : ubicacion.getFecha(),
                p.getEstado());
    }
}

// ---- Vista territorial: una ruta vincula la zona con sus proveedores y acopiadores ----

record AcopiadorDeZonaResponse(Long id, String nombres, String apellidos) {
    static AcopiadorDeZonaResponse from(org.ramiro.huata.acopiadores.Acopiador a) {
        return new AcopiadorDeZonaResponse(a.getId(), a.getNombres(), a.getApellidos());
    }
}

record ProveedorDeZonaResponse(
        Long id,
        String codigo,
        String nombres,
        String apellidos,
        String telefono,
        Integer ordenEnRuta,
        String acopiadorResponsableNombre) {
    static ProveedorDeZonaResponse from(Productor p) {
        return new ProveedorDeZonaResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombres(),
                p.getApellidos(),
                p.getTelefono(),
                p.getOrdenEnRuta(),
                p.getAcopiadorResponsable() == null
                        ? null
                        : p.getAcopiadorResponsable().getNombres() + " "
                                + p.getAcopiadorResponsable().getApellidos());
    }
}

record ZonaAsignacionResponse(
        String zona,
        Long rutaId,
        String rutaNombre,
        String rutaDescripcion,
        List<ProveedorDeZonaResponse> proveedores,
        List<AcopiadorDeZonaResponse> acopiadores) {}

// ---- Poligono de zona (mapa de Rutas y movilidad) ----

record ZonaPoligonoPuntoRequest(@NotNull Integer secuencia, @NotNull Double latitud, @NotNull Double longitud) {}

/** Reemplaza TODO el poligono de una zona de una vez, igual que RutaPuntosRequest. */
record ZonaPoligonoRequest(
        @NotBlank String zona, String color, @NotEmpty List<@Valid ZonaPoligonoPuntoRequest> puntos) {}

record ZonaPoligonoPuntoResponse(Integer secuencia, Double latitud, Double longitud) {
    static ZonaPoligonoPuntoResponse from(ZonaPoligonoPunto p) {
        return new ZonaPoligonoPuntoResponse(p.getSecuencia(), p.getLatitud(), p.getLongitud());
    }
}

record ZonaPoligonoResponse(Long id, String zona, String color, List<ZonaPoligonoPuntoResponse> puntos) {}

/** Una version guardada del limite de una zona (V56): que habia, quien lo cambio y cuando. */
record ZonaPoligonoVersionResponse(
        Long id,
        String zona,
        String color,
        List<ZonaPoligonoPuntoResponse> puntos,
        String motivo,
        java.time.Instant fecha,
        String usuario) {}

// ---- Edicion y baja de la zona como tal (nombre compartido por Ruta.zona y ZonaPoligono.zona) ----

/**
 * Renombrar una zona toca dos tablas (rutas y zona_poligonos) porque la zona es texto libre y no
 * tiene tabla maestra; por eso se resuelve en el servidor en una sola transaccion y no con varias
 * llamadas sueltas desde el navegador.
 */
record ZonaRenombrarRequest(@NotBlank String zona, @NotBlank String nuevoNombre) {}
