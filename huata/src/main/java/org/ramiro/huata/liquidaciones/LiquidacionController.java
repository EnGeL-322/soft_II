package org.ramiro.huata.liquidaciones;

import org.ramiro.huata.common.Paginas;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorService;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.verificacion.PropositoVerificacion;
import org.ramiro.huata.verificacion.VerificacionService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.PAGOS_READ + "')")
public class LiquidacionController {

    private final LiquidacionService liquidacionService;
    private final VerificacionService verificacionService;
    private final ProductorService productorService;

    /**
     * Historial de liquidaciones del proveedor logueado (RF-81). Solo exige estar autenticado, no
     * pagos.read: el rol PROVEEDOR no tiene ese permiso (ver MatrizPermisos) y no debe tenerlo, ya
     * que expondría la planilla completa de todos los proveedores en /liquidaciones. Mismo patrón
     * que ProductorController.me y RecojoController.misRecojos.
     */
    @GetMapping("/liquidaciones/me")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<LiquidacionResponse> misLiquidaciones(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Productor productor = productorService.obtenerPorUsuario(authentication.getName());
        return liquidacionService
                .buscar(productor.getId(), null, Paginas.de(page, size, Sort.by(Sort.Direction.DESC, "periodoFin")))
                .map(l -> LiquidacionResponse.from(l, null));
    }

    /** Cobro estimado del ciclo en curso para el proveedor logueado (RF-80). Ver LiquidacionService.estimadoActual. */
    @GetMapping("/liquidaciones/me/estimado")
    @PreAuthorize("isAuthenticated()")
    public LiquidacionService.FilaPlanilla miEstimado(Authentication authentication) {
        Productor productor = productorService.obtenerPorUsuario(authentication.getName());
        return liquidacionService.estimadoActual(productor.getId());
    }

    @GetMapping("/liquidaciones/me/resumen")
    @PreAuthorize("isAuthenticated()")
    public LiquidacionService.ResumenPagoProveedor miResumen(
            Authentication authentication,
            @RequestParam java.time.LocalDate desde,
            @RequestParam java.time.LocalDate hasta) {
        Productor productor = productorService.obtenerPorUsuario(authentication.getName());
        return liquidacionService.resumenPagoProveedor(productor.getId(), desde, hasta);
    }

    @GetMapping("/liquidaciones")
    public PageResponse<LiquidacionResponse> listar(
            @RequestParam(required = false) Long productorId,
            @RequestParam(required = false) EstadoLiquidacion estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return liquidacionService
                .buscar(productorId, estado, Paginas.de(page, size, Sort.by(Sort.Direction.DESC, "periodoFin")))
                .map(l -> LiquidacionResponse.from(l, null));
    }

    @GetMapping("/liquidaciones/{id}")
    public LiquidacionResponse obtener(@PathVariable Long id) {
        return conDetalleYAjustes(liquidacionService.obtener(id));
    }

    /**
     * Planilla del ciclo: lo que cada proveedor tiene pendiente de cobro. Alimenta las dos
     * pantallas de Secretaria (Pago unico y Pago global).
     *
     * <p>Es de lectura, asi que basta pagos.read: gerencia y administracion pueden verla sin poder
     * ejecutar el pago.
     */
    @GetMapping("/liquidaciones/planilla")
    public java.util.List<LiquidacionService.FilaPlanilla> planilla(
            @RequestParam java.time.LocalDate periodoInicio, @RequestParam java.time.LocalDate periodoFin) {
        return liquidacionService.planillaDelCiclo(periodoInicio, periodoFin);
    }

    @PostMapping("/liquidaciones/calcular")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_WRITE + "')")
    public LiquidacionResponse calcular(@Valid @RequestBody LiquidacionCalcularRequest request) {
        return conDetalleYAjustes(liquidacionService.calcular(request));
    }

    @PatchMapping("/liquidaciones/{id}/estado")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_WRITE + "')")
    public LiquidacionResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoLiquidacionRequest request) {
        return conDetalleYAjustes(liquidacionService.cambiarEstado(id, request));
    }

    /**
     * "Pagar a todos": liquida y cierra en un solo paso a todos los proveedores con entregas en el
     * periodo.
     *
     * <p>Restringido a ADMINISTRADOR. Es la operacion de mas alcance del modulo — mueve el pago de
     * toda la planilla de una semana — y ocultar el boton en la interfaz no protege el endpoint.
     */
    @PostMapping("/liquidaciones/cerrar-ciclo")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_CERRAR_CICLO + "')")
    public CierreCicloResponse cerrarCiclo(@Valid @RequestBody CerrarCicloRequest request) {
        LiquidacionService.CierreCicloResultado r =
                liquidacionService.cerrarCiclo(request.periodoInicio(), request.periodoFin());
        return new CierreCicloResponse(
                request.periodoInicio(),
                request.periodoFin(),
                r.liquidadas().size(),
                r.yaCerrados(),
                r.litrosTotales(),
                r.montoNetoTotal(),
                r.liquidadas().stream().map(this::conDetalleYAjustes).toList());
    }

    /**
     * Pide el codigo de 6 digitos para reabrir un pago. Separado de POST /verificacion/codigo:
     * exige pagos.reabrir, no parametros.write — el codigo de reapertura no deberia poder pedirse
     * con el mismo permiso que cambia precios.
     */
    @PostMapping("/liquidaciones/reabrir/codigo")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_REABRIR + "')")
    public VerificacionService.CodigoEmitido solicitarCodigoReapertura() {
        return verificacionService.solicitar(PropositoVerificacion.REAPERTURA_PAGO);
    }

    /**
     * Reabre una liquidacion CERRADA (mismo control que cambiar el precio de la leche: codigo de 6
     * digitos enviado al correo de quien lo pide, ver VerificacionService). El codigo se valida aqui,
     * igual que en {@link #crearTarifa}: el servicio de liquidaciones no sabe de correos ni codigos,
     * solo de que la reapertura ya fue autorizada.
     */
    @PostMapping("/liquidaciones/{id}/reabrir")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_REABRIR + "')")
    public LiquidacionResponse reabrir(@PathVariable Long id, @Valid @RequestBody ReabrirLiquidacionRequest request) {
        verificacionService.exigirCodigo(PropositoVerificacion.REAPERTURA_PAGO, request.codigo());
        return conDetalleYAjustes(liquidacionService.reabrir(id));
    }

    // ---- Ajustes de la liquidacion (regla 7.7) ----

    @PostMapping("/liquidaciones/{id}/ajustes")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_WRITE + "')")
    public LiquidacionResponse agregarAjuste(@PathVariable Long id, @Valid @RequestBody AjusteLiquidacionRequest request) {
        return conDetalleYAjustes(liquidacionService.agregarAjuste(id, request));
    }

    @DeleteMapping("/liquidaciones/{id}/ajustes/{ajusteId}")
    @PreAuthorize("hasAuthority('" + Permiso.PAGOS_WRITE + "')")
    public LiquidacionResponse eliminarAjuste(@PathVariable Long id, @PathVariable Long ajusteId) {
        return conDetalleYAjustes(liquidacionService.eliminarAjuste(id, ajusteId));
    }

    /** El listado paginado no trae detalle ni ajustes; una liquidacion concreta si. */
    private LiquidacionResponse conDetalleYAjustes(Liquidacion liquidacion) {
        Long id = liquidacion.getId();
        return LiquidacionResponse.from(liquidacion, liquidacionService.detalle(id), liquidacionService.listarAjustes(id));
    }

    // ---- Tarifas de leche (configuracion) ----

    @GetMapping("/tarifas-leche")
    public List<TarifaLecheResponse> tarifas() {
        return liquidacionService.listarTarifas().stream().map(TarifaLecheResponse::from).toList();
    }

    /**
     * Exige el codigo de 6 digitos enviado por correo, igual que los precios de queso: es el mismo
     * tipo de cambio y no puede tener dos niveles de exigencia segun por que pantalla se entre.
     */
    @PostMapping("/tarifas-leche")
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ResponseEntity<TarifaLecheResponse> crearTarifa(@Valid @RequestBody TarifaLecheRequest request) {
        verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, request.codigo());
        return ResponseEntity.status(HttpStatus.CREATED).body(TarifaLecheResponse.from(liquidacionService.guardarTarifa(request)));
    }
}
