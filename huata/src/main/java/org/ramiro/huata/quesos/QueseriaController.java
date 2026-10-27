package org.ramiro.huata.quesos;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.ramiro.huata.verificacion.PropositoVerificacion;
import org.ramiro.huata.verificacion.VerificacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Produccion y venta de queso por lote.
 *
 * <p>Va aparte de {@link QuesoController} porque son dos modelos distintos: aquel mantiene la tabla
 * plana historica (`quesos`), que siguen leyendo el exportador y la linea de tiempo de reportes;
 * este es el lote con stock que piden las dos pantallas de Secretaria.
 *
 * <p>Consultar exige quesos.read y registrar lotes quesos.write. Desde V54 cada operacion de venta
 * tiene su permiso (ventas.registrar, ventas.cobrar, ventas.anular) para concederlas por separado
 * desde Usuarios y roles. Gerencia ve la produccion y las ventas pero no las registra.
 */
@RestController
@RequestMapping("/api/v1/quesos")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + Permiso.QUESOS_READ + "')")
public class QueseriaController {

    private final QueseriaService queseriaService;
    private final VerificacionService verificacionService;

    /** Acepta quesos.read o produccion.read: Productor de queso consulta lotes pero no vende. */
    @GetMapping("/lotes")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_READ + "') or hasAuthority('" + Permiso.PRODUCCION_READ + "')")
    public List<LoteQuesoResponse> listarLotes(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return queseriaService.listarLotes(desde, hasta).stream()
                .map(LoteQuesoResponse::from)
                .toList();
    }

    /**
     * Lo que la pantalla de venta puede ofrecer: con unidades en camara y sin vencer, primero lo que
     * vence antes (V33).
     */
    @GetMapping("/lotes/con-stock")
    public List<LoteQuesoResponse> lotesConStock() {
        return queseriaService.lotesParaVender(LocalDate.now(java.time.ZoneId.of("America/Lima"))).stream()
                .map(LoteQuesoResponse::from)
                .toList();
    }

    // ---- Historial por cliente ----

    /** A quien se le vende: cada cliente con cuanto compro, cuanto debe y su ultima compra. */
    @GetMapping("/ventas/clientes")
    public List<QueseriaService.ClienteResumen> clientes(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return queseriaService.clientes(desde, hasta);
    }

    /** Todas las compras de un cliente (anuladas incluidas, marcadas), opcionalmente de un producto. */
    @GetMapping("/ventas/historial")
    public List<VentaQuesoResponse> historialCliente(
            @RequestParam String cliente,
            @RequestParam(required = false) Long productoId,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return queseriaService.historialCliente(cliente, productoId, desde, hasta).stream()
                .map(VentaQuesoResponse::from)
                .toList();
    }

    /**
     * Acepta quesos.write (Secretaria/Supervisor, el camino historico) o produccion.write (rol
     * Productor de queso, que no tiene quesos.write porque no vende ni administra tarifas): ambos
     * pueden ejecutar y cerrar un lote, solo cambia quien es.
     */
    @PostMapping("/lotes")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_WRITE + "') or hasAuthority('" + Permiso.PRODUCCION_WRITE + "')")
    public ResponseEntity<LoteQuesoResponse> crearLote(@Valid @RequestBody LoteQuesoRequest request) {
        LoteQueso lote = queseriaService.crearLote(request);
        // Recien creado no tiene ventas, asi que su stock es el total producido.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LoteQuesoResponse.from(new QueseriaService.LoteConStock(lote, lote.getUnidadesProducidas())));
    }

    /**
     * Anula un lote registrado por error: devuelve los insumos al almacen. Solo si no se vendio nada
     * de el (V59). Mismos permisos que registrarlo.
     */
    @PostMapping("/lotes/{id}/anular")
    @PreAuthorize("hasAuthority('" + Permiso.QUESOS_WRITE + "') or hasAuthority('" + Permiso.PRODUCCION_WRITE + "')")
    public LoteQuesoResponse anularLote(@PathVariable Long id, @Valid @RequestBody AnularVentaRequest request) {
        LoteQueso lote = queseriaService.anularLote(id, request.motivo());
        return LoteQuesoResponse.from(new QueseriaService.LoteConStock(lote, 0));
    }

    @GetMapping("/ventas")
    public List<VentaQuesoResponse> listarVentas(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return queseriaService.listarVentas(desde, hasta).stream()
                .map(VentaQuesoResponse::from)
                .toList();
    }

    @GetMapping("/ventas/resumen")
    public ResumenVentasResponse resumenVentas(
            @RequestParam(required = false) LocalDate desde, @RequestParam(required = false) LocalDate hasta) {
        return queseriaService.resumen(desde, hasta);
    }

    @PostMapping("/ventas")
    @PreAuthorize("hasAuthority('" + Permiso.VENTAS_REGISTRAR + "')")
    public ResponseEntity<VentaQuesoResponse> registrarVenta(@Valid @RequestBody VentaQuesoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VentaQuesoResponse.from(queseriaService.registrarVenta(request)));
    }

    /** Venta por producto: sale primero lo que vence antes, repartido entre lotes si hace falta. */
    @PostMapping("/ventas/por-producto")
    @PreAuthorize("hasAuthority('" + Permiso.VENTAS_REGISTRAR + "')")
    public ResponseEntity<List<VentaQuesoResponse>> venderPorProducto(@Valid @RequestBody VentaPorProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(queseriaService.venderPorProducto(request).stream().map(VentaQuesoResponse::from).toList());
    }

    @PatchMapping("/ventas/{id}/cobrada")
    @PreAuthorize("hasAuthority('" + Permiso.VENTAS_COBRAR + "')")
    public VentaQuesoResponse marcarCobrada(@PathVariable Long id) {
        return VentaQuesoResponse.from(queseriaService.marcarCobrada(id));
    }

    /**
     * Anula una venta y devuelve sus unidades al stock. Es POST y no DELETE porque exige motivo y no
     * borra nada: la fila queda, marcada como anulada.
     */
    @PostMapping("/ventas/{id}/anular")
    @PreAuthorize("hasAuthority('" + Permiso.VENTAS_ANULAR + "')")
    public VentaQuesoResponse anularVenta(@PathVariable Long id, @Valid @RequestBody AnularVentaRequest request) {
        return VentaQuesoResponse.from(queseriaService.anularVenta(id, request));
    }

    // ---- Precios (configuracion) ----

    /**
     * El precio que rige para un producto (hoy, o en la fecha dada). La lee cualquiera con
     * quesos.read porque la pantalla de venta necesita mostrar lo que se va a cobrar antes de
     * registrar la salida, incluida una promocion en curso.
     */
    @GetMapping("/tarifas/vigente")
    public TarifaQuesoResponse tarifaVigente(
            @RequestParam Long productoId, @RequestParam(required = false) LocalDate fecha) {
        return TarifaQuesoResponse.from(
                queseriaService.tarifaVigente(productoId, fecha != null ? fecha : LocalDate.now()));
    }

    @GetMapping("/tarifas")
    public List<TarifaQuesoResponse> tarifas(@RequestParam(required = false) Long productoId) {
        return queseriaService.listarTarifas(productoId).stream().map(TarifaQuesoResponse::from).toList();
    }

    /**
     * Cambiar precios es configuracion, no operacion: exige parametros.write, igual que la tarifa de
     * leche. Quien vende queso (quesos.write) no decide a cuanto se vende.
     *
     * <p>Ademas exige el codigo de 6 digitos enviado por correo. El permiso dice quien puede entrar;
     * el codigo dice que quien entro es quien dice ser, y sobre lo unico que cambia cuanto cobra la
     * cooperativa no son la misma pregunta.
     */
    @PostMapping("/tarifas")
    @PreAuthorize("hasAuthority('" + Permiso.PARAMETROS_WRITE + "')")
    public ResponseEntity<TarifaQuesoResponse> guardarTarifa(@Valid @RequestBody TarifaQuesoRequest request) {
        verificacionService.exigirCodigo(PropositoVerificacion.PRECIOS, request.codigo());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TarifaQuesoResponse.from(queseriaService.guardarTarifa(request)));
    }
}
