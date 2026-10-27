package org.ramiro.huata.calidad;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/calidad")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.CALIDAD_READ + "')")
public class CalidadController {

    private final CalidadService calidadService;
    private final VoucherService voucherService;

    // ---- Parametros ----

    @GetMapping("/parametros")
    public List<ParametroCalidadResponse> parametros() {
        return calidadService.listarParametros().stream().map(ParametroCalidadResponse::from).toList();
    }

    @PostMapping("/parametros")
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ResponseEntity<ParametroCalidadResponse> crearParametro(@RequestBody ParametroCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ParametroCalidadResponse.from(calidadService.guardarParametro(null, request)));
    }

    @PutMapping("/parametros/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ParametroCalidadResponse actualizarParametro(@PathVariable Long id, @RequestBody ParametroCalidadRequest request) {
        return ParametroCalidadResponse.from(calidadService.guardarParametro(id, request));
    }

    // ---- Controles ----

    @PostMapping("/controles")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public ResponseEntity<ControlCalidadResponse> registrarControl(@Valid @RequestBody ControlCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ControlCalidadResponse.from(calidadService.registrarControl(request)));
    }

    @PutMapping("/controles/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public ControlCalidadResponse actualizarControl(@PathVariable Long id, @Valid @RequestBody ControlCalidadRequest request) {
        return ControlCalidadResponse.from(calidadService.actualizarControl(id, request));
    }

    /** Analisis registrados con la app que todavia no se aprobaron (V48). */
    @GetMapping("/controles/por-aprobar")
    public List<ControlCalidadResponse> porAprobar(@RequestParam(defaultValue = "14") int dias) {
        return calidadService.porAprobar(dias).stream().map(ControlCalidadResponse::from).toList();
    }

    /** Analisis de los ultimos dias, aprobados o no (los que se aprobaron solos incluidos). */
    @GetMapping("/controles/recientes")
    public List<ControlCalidadResponse> recientes(@RequestParam(defaultValue = "1") int dias) {
        return calidadService.recientes(dias).stream().map(ControlCalidadResponse::from).toList();
    }

    /** Lo llama la app movil (V49): aprueba quien tiene calidad.aprobar. La web solo consulta. */
    @PatchMapping("/controles/{id}/aprobar")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_APROBAR + "')")
    public ControlCalidadResponse aprobar(@PathVariable Long id, Authentication authentication) {
        return ControlCalidadResponse.from(calidadService.aprobar(id, authentication.getName()));
    }

    /** Controles de campo de los ultimos dias que todavia no tienen el recojo del acopiador. */
    @GetMapping("/controles/sin-recojo")
    public List<ControlCalidadResponse> sinRecojo(@RequestParam(defaultValue = "7") int dias) {
        return calidadService.controlesSinRecojo(dias).stream().map(ControlCalidadResponse::from).toList();
    }

    @GetMapping("/historial/{productorId}")
    public List<ControlCalidadResponse> historial(@PathVariable Long productorId) {
        return calidadService.historialPorProductor(productorId).stream().map(ControlCalidadResponse::from).toList();
    }

    // ---- Incidencias / seguimiento ----

    @GetMapping("/incidencias")
    public List<IncidenciaCalidadResponse> incidencias() {
        return calidadService.listarIncidencias().stream().map(IncidenciaCalidadResponse::from).toList();
    }

    @PostMapping("/incidencias")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public ResponseEntity<IncidenciaCalidadResponse> registrarIncidencia(@Valid @RequestBody IncidenciaCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(IncidenciaCalidadResponse.from(calidadService.registrarIncidencia(request)));
    }

    @PatchMapping("/incidencias/{id}/estado")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public IncidenciaCalidadResponse cambiarEstadoIncidencia(
            @PathVariable Long id, @Valid @RequestBody CambiarEstadoIncidenciaRequest request) {
        return IncidenciaCalidadResponse.from(calidadService.cambiarEstadoIncidencia(id, request));
    }

    @GetMapping("/seguimiento/{productorId}")
    public List<IncidenciaCalidadResponse> seguimiento(@PathVariable Long productorId) {
        return calidadService.seguimientoPorProductor(productorId).stream().map(IncidenciaCalidadResponse::from).toList();
    }

    // ---- Evidencia ----

    @PostMapping("/evidencias")
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public ResponseEntity<EvidenciaCalidadResponse> registrarEvidencia(@Valid @RequestBody EvidenciaCalidadRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(EvidenciaCalidadResponse.from(calidadService.registrarEvidencia(request)));
    }

    @GetMapping("/evidencias/control/{controlCalidadId}")
    public List<EvidenciaCalidadResponse> evidenciaDeControl(@PathVariable Long controlCalidadId) {
        return calidadService.evidenciaDeControl(controlCalidadId).stream().map(EvidenciaCalidadResponse::from).toList();
    }

    @GetMapping("/evidencias/incidencia/{incidenciaId}")
    public List<EvidenciaCalidadResponse> evidenciaDeIncidencia(@PathVariable Long incidenciaId) {
        return calidadService.evidenciaDeIncidencia(incidenciaId).stream().map(EvidenciaCalidadResponse::from).toList();
    }

    // ---- Foto del voucher (RF-85, RNF-13, RNF-20) ----

    /**
     * Sube la foto del voucher de un control ya guardado. Idempotente por {@code clientUuid}: la app
     * la reintenta desde su cola offline sin duplicar la evidencia.
     */
    @PostMapping(path = "/controles/{id}/voucher", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('" + Permiso.CALIDAD_WRITE + "')")
    public ResponseEntity<EvidenciaCalidadResponse> subirVoucher(
            @PathVariable Long id,
            @RequestPart("foto") MultipartFile foto,
            @RequestParam(required = false) String clientUuid,
            @RequestParam(required = false) String textoOcr,
            @RequestParam(required = false) String valoresOcr,
            @RequestParam(required = false) Double confianzaOcr,
            Authentication authentication) throws IOException {
        var evidencia = voucherService.subir(
                id,
                foto.getBytes(),
                new VoucherService.Datos(clientUuid, textoOcr, valoresOcr, confianzaOcr),
                authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(EvidenciaCalidadResponse.from(evidencia));
    }

    /**
     * Sirve el archivo con autorizacion: el almacen de objetos nunca se expone directo al navegador
     * (seccion 30, "controlar acceso a vouchers").
     */
    @GetMapping("/evidencias/{id}/archivo")
    public ResponseEntity<byte[]> archivoDeEvidencia(@PathVariable Long id) {
        var archivo = voucherService.archivo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                // Que el navegador no "adivine" otro tipo y ejecute el archivo como HTML o script.
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(archivo.contenido());
    }
}
