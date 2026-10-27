package org.ramiro.huata.rutas;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
public class RutaController {

    private final RutaService rutaService;

    // ---- Rutas ----

    @GetMapping("/zonas/asignaciones")
    @PreAuthorize("hasAuthority('" + Permiso.ZONAS_READ + "')")
    public List<ZonaAsignacionResponse> zonasAsignadas() {
        return rutaService.zonasAsignadas();
    }

    @GetMapping("/rutas")
    public PageResponse<RutaResponse> listarRutas(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return rutaService.buscarRutas(q, Paginas.de(page, size, Sort.by("nombre"))).map(RutaResponse::from);
    }

    @PostMapping("/rutas")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<RutaResponse> crearRuta(@Valid @RequestBody RutaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RutaResponse.from(rutaService.crearRuta(request)));
    }

    @PutMapping("/rutas/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public RutaResponse actualizarRuta(@PathVariable Long id, @Valid @RequestBody RutaRequest request) {
        return RutaResponse.from(rutaService.actualizarRuta(id, request));
    }

    @DeleteMapping("/rutas/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> inactivarRuta(@PathVariable Long id) {
        rutaService.inactivarRuta(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Asignaciones ----

    @GetMapping("/asignaciones-ruta")
    public List<AsignacionRutaResponse> asignaciones(
            @RequestParam LocalDate fecha, @RequestParam(required = false) Long acopiadorId) {
        return rutaService.asignacionesPorFecha(fecha, acopiadorId).stream()
                .map(AsignacionRutaResponse::from)
                .toList();
    }

    @PostMapping("/asignaciones-ruta")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<AsignacionRutaResponse> asignar(@Valid @RequestBody AsignacionRutaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AsignacionRutaResponse.from(rutaService.asignar(request)));
    }

    @PatchMapping("/asignaciones-ruta/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public AsignacionRutaResponse actualizarAsignacion(@PathVariable Long id, @RequestBody AsignacionActualizarRequest request) {
        return AsignacionRutaResponse.from(rutaService.actualizarAsignacion(id, request));
    }

    // ---- Plan operativo ----

    @GetMapping("/rutas/plan-operativo")
    public List<PlanRutaDia> planOperativo(@RequestParam LocalDate fecha) {
        return rutaService.planOperativo(fecha);
    }

    // ---- Geometria de la ruta (2.1.3-2.1.5): dibujar/editar/guardar/eliminar en el mapa ----

    @GetMapping("/rutas/{id}/puntos")
    public List<RutaPuntoResponse> puntos(@PathVariable Long id) {
        return rutaService.puntosDeRuta(id).stream().map(RutaPuntoResponse::from).toList();
    }

    /** Reemplaza toda la geometria de la ruta de una vez (guardar el trazo completo tras editarlo). */
    @PutMapping("/rutas/{id}/puntos")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public List<RutaPuntoResponse> guardarPuntos(@PathVariable Long id, @Valid @RequestBody RutaPuntosRequest request) {
        return rutaService.reemplazarPuntos(id, request).stream().map(RutaPuntoResponse::from).toList();
    }

    @DeleteMapping("/rutas/{id}/puntos")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> eliminarPuntos(@PathVariable Long id) {
        rutaService.eliminarPuntos(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Punto de acopio dinámico: se agrega por latitud/longitud sin rehacer todo el trazo, y de paso
     * reordena a los proveedores por cercanía real a ese punto (vecino más cercano) — es el "cambio
     * de destino" que decide el administrador y que la app móvil del acopiador ve reflejado al
     * refrescar su ruta.
     */
    @PostMapping("/rutas/{id}/puntos/agregar")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public PuntoAcopioAgregadoResponse agregarPuntoAcopio(@PathVariable Long id, @Valid @RequestBody PuntoAcopioRequest request) {
        return rutaService.agregarPuntoAcopio(id, request);
    }

    /**
     * Reordena a los proveedores de la ruta por cercanía sin tocar el trazo — por ejemplo, cuando el
     * origen del día cambió pero no hace falta agregar un punto nuevo al mapa.
     */
    @PostMapping("/rutas/{id}/reordenar")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public List<ProveedorDeRutaResponse> reordenar(@PathVariable Long id, @RequestBody(required = false) ReordenarRutaRequest request) {
        return rutaService.reordenarPorCercania(id, request);
    }

    // ---- Proveedores de la ruta, en orden de visita (2.1.6) ----

    /** "A que proveedores voy hoy": lo consulta tanto el admin como la app movil del acopiador. */
    @GetMapping("/rutas/{id}/proveedores")
    public List<ProveedorDeRutaResponse> proveedores(@PathVariable Long id) {
        return rutaService.proveedoresDeRuta(id);
    }

    // ---- Poligono de zona (mapa de Rutas y movilidad) ----

    /**
     * Mismo permiso que el resto de "Rutas y movilidad" (RUTAS_READ), no ZONAS_READ ("Ver zonas y
     * rendimiento", que es un recurso distinto que ni ACOPIADOR ni CALIDAD tienen). El mapa del
     * acopiador y del testeador en la app movil necesitan poder leer los poligonos igual que ya leen
     * la geometria de la ruta (RutaPunto) con este mismo permiso.
     */
    @GetMapping("/zonas/poligonos")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
    public List<ZonaPoligonoResponse> poligonosDeZona() {
        return rutaService.poligonosDeZona();
    }

    /** Crea o reemplaza el poligono completo de una zona (mismo permiso que editar la geometria de una ruta). */
    @PutMapping("/zonas/poligonos")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ZonaPoligonoResponse guardarPoligonoDeZona(@Valid @RequestBody ZonaPoligonoRequest request) {
        return rutaService.guardarPoligonoDeZona(request);
    }

    /** Versiones anteriores del limite de una zona, para deshacer un cambio o un borrado. */
    @GetMapping("/zonas/poligonos/historial")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
    public List<ZonaPoligonoVersionResponse> historialDeLimite(@RequestParam String zona) {
        return rutaService.historialDeLimite(zona);
    }

    @PostMapping("/zonas/poligonos/historial/{versionId}/restaurar")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ZonaPoligonoResponse restaurarLimite(@PathVariable Long versionId) {
        return rutaService.restaurarLimite(versionId);
    }

    @DeleteMapping("/zonas/poligonos/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> eliminarPoligonoDeZona(@PathVariable Long id) {
        rutaService.eliminarPoligonoDeZona(id);
        return ResponseEntity.noContent().build();
    }

    // ---- La zona como tal: renombrar y dar de baja ----

    /** El nombre va en el cuerpo, no en la ruta: las zonas reales llevan "/" (COLLANA I/YASIN). */
    @PatchMapping("/zonas")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> renombrarZona(@Valid @RequestBody ZonaRenombrarRequest request) {
        rutaService.renombrarZona(request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/zonas")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> eliminarZona(@RequestParam String zona) {
        rutaService.eliminarZona(zona);
        return ResponseEntity.noContent().build();
    }
}
