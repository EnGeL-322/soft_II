package org.ramiro.huata.productores;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/productores")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_READ + "')")
public class ProductorController {

    private final ProductorService productorService;
    private final ProductorUbicacionService ubicacionService;
    private final SolicitudCambioZonaService solicitudService;

    /**
     * El proveedor logueado en la app movil (2.4: puede recuperar su historial aunque recien haya
     * conseguido celular). Solo exige estar autenticado, no proveedores.read: un proveedor no tiene
     * ese permiso, solo ve lo suyo.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ProductorResponse me(Authentication authentication) {
        return ProductorResponse.from(productorService.obtenerPorUsuario(authentication.getName()));
    }

    // ---- Ubicacion del proveedor (2.6) ----

    @GetMapping("/{id}/ubicaciones")
    public List<ProductorUbicacionResponse> ubicaciones(@PathVariable Long id) {
        return ubicacionService.historial(id).stream().map(ProductorUbicacionResponse::from).toList();
    }

    /** La registra el proveedor desde su app (solo la suya), o quien edita proveedores desde el mapa. */
    @PostMapping("/{id}/ubicaciones")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "') or @accesoProveedor.esPropio(#id, authentication)")
    public ResponseEntity<ProductorUbicacionResponse> registrarUbicacion(
            @PathVariable Long id, @Valid @RequestBody ProductorUbicacionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductorUbicacionResponse.from(ubicacionService.registrar(id, request)));
    }

    // ---- Solicitud de cambio de zona (2.5) ----

    /**
     * Historial de las propias solicitudes de zona del proveedor logueado (7.4/RF-58). Solo exige
     * estar autenticado, no proveedores.read: igual razón que {@link #me}, el rol PROVEEDOR no tiene
     * ese permiso y no debe tenerlo solo para ver sus propias solicitudes.
     */
    @GetMapping("/me/solicitudes-zona")
    @PreAuthorize("isAuthenticated()")
    public List<SolicitudCambioZonaResponse> misSolicitudesZona(Authentication authentication) {
        Productor productor = productorService.obtenerPorUsuario(authentication.getName());
        return solicitudService.deProductor(productor.getId()).stream()
                .map(SolicitudCambioZonaResponse::from)
                .toList();
    }

    @GetMapping("/solicitudes-zona")
    public List<SolicitudCambioZonaResponse> solicitudesZona(
            @RequestParam(required = false) EstadoSolicitudZona estado) {
        return solicitudService.listar(estado).stream().map(SolicitudCambioZonaResponse::from).toList();
    }

    @GetMapping("/{id}/solicitudes-zona")
    public List<SolicitudCambioZonaResponse> solicitudesDeProductor(@PathVariable Long id) {
        return solicitudService.deProductor(id).stream().map(SolicitudCambioZonaResponse::from).toList();
    }

    /**
     * Zonas que se le pueden ofrecer en el selector: las de rutas activas menos la suya. Mismo
     * permiso que crear la solicitud, para que el proveedor vea su lista sin proveedores.read.
     */
    @GetMapping("/{id}/zonas-solicitables")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "') or @accesoProveedor.esPropio(#id, authentication)")
    public List<String> zonasSolicitables(@PathVariable Long id) {
        return solicitudService.zonasSolicitables(id);
    }

    /** La crea el propio proveedor desde su app (solo para su ficha), o quien edita proveedores. */
    @PostMapping("/{id}/solicitudes-zona")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "') or @accesoProveedor.esPropio(#id, authentication)")
    public ResponseEntity<SolicitudCambioZonaResponse> crearSolicitudZona(
            @PathVariable Long id, @Valid @RequestBody SolicitudCambioZonaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SolicitudCambioZonaResponse.from(solicitudService.crear(id, request)));
    }

    /** Aprobar o rechazar es una decision administrativa: exige proveedores.write. */
    @PatchMapping("/solicitudes-zona/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public SolicitudCambioZonaResponse decidirSolicitudZona(
            @PathVariable Long id, @RequestBody SolicitudCambioZonaDecisionRequest request) {
        return SolicitudCambioZonaResponse.from(solicitudService.decidir(id, request));
    }

    @GetMapping
    public PageResponse<ProductorResponse> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) EstadoProductor estado,
            @RequestParam(required = false) Long rutaId,
            @RequestParam(required = false) String zona,
            @RequestParam(required = false) Long acopiadorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productorService
                .buscar(new ProductorService.Filtro(q, estado, rutaId, zona, acopiadorId),
                        Paginas.de(page, size, Sort.by("nombres")))
                .map(ProductorResponse::from);
    }

    @GetMapping("/{id}")
    public ProductorResponse obtener(@PathVariable Long id) {
        return ProductorResponse.from(productorService.obtener(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ResponseEntity<ProductorResponse> crear(@Valid @RequestBody ProductorRequest request) {
        Productor creado = productorService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductorResponse.from(creado));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ProductorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductorRequest request) {
        return ProductorResponse.from(productorService.actualizar(id, request));
    }

    @PatchMapping("/{id}/excelencia")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ProductorResponse cambiarExcelencia(
            @PathVariable Long id, @RequestBody ProductorExcelenciaRequest request) {
        return ProductorResponse.from(productorService.cambiarExcelencia(id, request));
    }

    /** Traer o mover al proveedor a otra ruta desde "Rutas y mapa", sin rehacer su ficha. */
    @PatchMapping("/{id}/ruta")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ProductorResponse asignarRuta(@PathVariable Long id, @Valid @RequestBody ProductorRutaRequest request) {
        return ProductorResponse.from(productorService.asignarRuta(id, request));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ProductorResponse cambiarEstado(@PathVariable Long id, @RequestBody ProductorEstadoRequest request) {
        return ProductorResponse.from(productorService.cambiarEstado(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permiso.PROVEEDORES_WRITE + "')")
    public ResponseEntity<Void> inactivar(@PathVariable Long id) {
        productorService.inactivar(id);
        return ResponseEntity.noContent().build();
    }
}
