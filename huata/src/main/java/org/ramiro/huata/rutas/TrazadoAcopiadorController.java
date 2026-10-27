package org.ramiro.huata.rutas;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Pintar ruta del dia" (app movil del acopiador) y "Asignar como ruta predeterminada" (web).
 *
 * <p><b>Permisos.</b> Leer exige {@code rutas.read}, que ya tiene el ACOPIADOR. Pintar su propio
 * recorrido exige {@code acopio.write} —su permiso de escritura de campo, el mismo con el que reporta
 * GPS y registra recojos— y no {@code rutas.write}, que es administrar el catalogo de rutas y que el
 * acopiador no tiene ni debe tener. Predeterminar, en cambio, si exige {@code rutas.write}: decide la
 * referencia permanente de una persona y es una decision de administracion, no de campo.
 */
@RestController
@RequestMapping("/api/v1/trazados-acopiador")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.RUTAS_READ + "')")
public class TrazadoAcopiadorController {

    private final TrazadoAcopiadorService service;

    // ---- App movil: el acopiador y su propio recorrido ----

    /** Todo lo que necesita la pantalla de pintado: su ruta, lo ya pintado y los dos respaldos. */
    @GetMapping("/me")
    public MiTrazadoResponse miTrazado(
            Authentication authentication, @RequestParam(required = false) LocalDate fecha) {
        return service.miTrazado(authentication.getName(), fecha);
    }

    /** Reemplaza el recorrido pintado de ese dia; idempotente por (acopiador, fecha). */
    @PutMapping("/me")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public TrazadoAcopiadorResponse guardarMiTrazado(
            Authentication authentication, @Valid @RequestBody TrazadoDiaRequest request) {
        return service.guardarDelDia(authentication.getName(), request);
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasAuthority('" + Permiso.ACOPIO_WRITE + "')")
    public ResponseEntity<Void> borrarMiTrazado(
            Authentication authentication, @RequestParam(required = false) LocalDate fecha) {
        service.borrarDelDia(authentication.getName(), fecha);
        return ResponseEntity.noContent().build();
    }

    // ---- Web: lo que pintaron los acopiadores y cual es el predeterminado de cada uno ----

    /**
     * Los recorridos pintados de una fecha, con sus puntos.
     *
     * <p>{@code fecha} es <b>obligatoria</b>: sin ella la respuesta serian todos los trazados desde
     * el inicio con todos sus vertices, y eso crece sin techo con cada jornada. Ninguna pantalla los
     * pide todos, y la que quiera historial pedira una fecha.
     */
    @GetMapping
    public List<TrazadoAcopiadorResponse> pintados(@RequestParam LocalDate fecha) {
        return service.pintadosDelDia(fecha);
    }

    @GetMapping("/predeterminados")
    public List<TrazadoAcopiadorResponse> predeterminados() {
        return service.predeterminados();
    }

    @PostMapping("/{id}/predeterminar")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public TrazadoAcopiadorResponse predeterminar(@PathVariable Long id, Authentication authentication) {
        return service.predeterminar(id, authentication.getName());
    }

    /** El acopiador vuelve a tener la geometria de su ruta como recorrido de referencia. */
    @DeleteMapping("/predeterminados/{acopiadorId}")
    @PreAuthorize("hasAuthority('" + Permiso.RUTAS_WRITE + "')")
    public ResponseEntity<Void> quitarPredeterminado(@PathVariable Long acopiadorId) {
        service.quitarPredeterminado(acopiadorId);
        return ResponseEntity.noContent().build();
    }
}
