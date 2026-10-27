package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.quesos.LoteQueso;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kernel compartido del kardex: aqui viven las unicas dos operaciones que le importan al resto del
 * sistema (cuanto stock hay, y registrar un movimiento) para que {@code compras}, {@code produccion}
 * y {@code quesos} no dupliquen la logica de saldo ni el criterio de "cantidad siempre positiva,
 * direccion segun el tipo".
 *
 * <p>Los metodos {@code registrarX} no llevan {@code @Transactional} propio: siempre se llaman desde
 * dentro de la transaccion del servicio que orquesta la operacion completa (una compra, un lote, una
 * venta), para que un fallo a mitad de camino revierta tambien el movimiento ya escrito.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private final MovimientoInventarioRepository movimientoRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;

    // ---- Saldos ----

    public BigDecimal saldoMateriaPrima(Long materiaPrimaId) {
        return movimientoRepository.saldoMateriaPrima(materiaPrimaId);
    }

    public Map<Long, BigDecimal> saldosMateriasPrimas(Collection<Long> materiaPrimaIds) {
        return aMapa(movimientoRepository.saldosMateriasPrimas(materiaPrimaIds));
    }

    public BigDecimal saldoLoteQueso(Long loteQuesoId) {
        return movimientoRepository.saldoLoteQueso(loteQuesoId);
    }

    public Map<Long, BigDecimal> saldosLotesQueso(Collection<Long> loteQuesoIds) {
        return aMapa(movimientoRepository.saldosLotesQueso(loteQuesoIds));
    }

    private static Map<Long, BigDecimal> aMapa(List<MovimientoInventarioRepository.SaldoPorItem> filas) {
        Map<Long, BigDecimal> saldos = new HashMap<>();
        for (MovimientoInventarioRepository.SaldoPorItem fila : filas) {
            saldos.put(fila.getItemId(), fila.getSaldo());
        }
        return saldos;
    }

    /**
     * Bloquea una materia prima para consumirla sin riesgo de sobreconsumo concurrente. Quien necesite
     * bloquear varias a la vez (un lote con receta de varios insumos) debe pedirlas en el mismo orden
     * (ascendente de id) que cualquier otro llamador, para no producir un deadlock entre dos lotes que
     * consumen las mismas materias primas en orden distinto.
     */
    public MateriaPrima bloquearMateriaPrima(Long materiaPrimaId) {
        return materiaPrimaRepository
                .findParaConsumirById(materiaPrimaId)
                .filter(MateriaPrima::isActivo)
                .orElseThrow(() -> NotFoundException.of("Materia prima", materiaPrimaId));
    }

    // ---- Registrar movimientos ----
    //
    // @Transactional en cada uno, aunque hoy siempre se llaman dentro de la transaccion de otro
    // servicio (una compra, un lote, una venta): con propagacion REQUIRED se unen a esa transaccion
    // sin crear una nueva, y si alguna vez se llaman sueltos, quedan protegidos igual.

    /** Leche recibida en planta (V31): la registra {@link LecheAcopioService}. */
    @Transactional
    public MovimientoInventario registrarEntradaAcopio(
            MateriaPrima leche, BigDecimal litros, LocalDate fecha, DocumentoOrigenTipo origen, Long origenId, String obs) {
        return registrarMateriaPrima(TipoMovimientoInventario.ENTRADA_ACOPIO, leche, litros, fecha, origen, origenId, obs);
    }

    /** Correccion a la baja de una recepcion ya registrada: sale la diferencia, no se edita la entrada. */
    @Transactional
    public MovimientoInventario registrarAjusteSalida(
            MateriaPrima materiaPrima, BigDecimal cantidad, LocalDate fecha, DocumentoOrigenTipo origen, Long origenId,
            String obs) {
        return registrarMateriaPrima(TipoMovimientoInventario.AJUSTE_SALIDA, materiaPrima, cantidad, fecha, origen, origenId, obs);
    }

    /** Ajuste que suma stock: conteo fisico mayor, o insumos que vuelven al anular un lote (V59). */
    @Transactional
    public MovimientoInventario registrarAjusteEntrada(
            MateriaPrima materiaPrima, BigDecimal cantidad, LocalDate fecha, DocumentoOrigenTipo origen, Long origenId,
            String obs) {
        return registrarMateriaPrima(TipoMovimientoInventario.AJUSTE_ENTRADA, materiaPrima, cantidad, fecha, origen, origenId, obs);
    }

    /** Saca de camara el producto terminado de un lote anulado (V59). */
    @Transactional
    public MovimientoInventario registrarAjusteSalidaLote(LoteQueso lote, BigDecimal cantidad, LocalDate fecha, String obs) {
        return registrarLoteQueso(
                TipoMovimientoInventario.AJUSTE_SALIDA, lote, cantidad, fecha, DocumentoOrigenTipo.PRODUCCION, lote.getId(), obs);
    }

    @Transactional
    public MovimientoInventario registrarEntradaCompra(MateriaPrima materiaPrima, BigDecimal cantidad, LocalDate fecha, Long compraId) {
        return registrarMateriaPrima(
                TipoMovimientoInventario.ENTRADA_COMPRA, materiaPrima, cantidad, fecha, DocumentoOrigenTipo.COMPRA, compraId, null);
    }

    @Transactional
    public MovimientoInventario registrarSalidaProduccionConsumo(
            MateriaPrima materiaPrima, BigDecimal cantidad, LocalDate fecha, Long loteId) {
        return registrarMateriaPrima(
                TipoMovimientoInventario.SALIDA_PRODUCCION_CONSUMO,
                materiaPrima,
                cantidad,
                fecha,
                DocumentoOrigenTipo.PRODUCCION,
                loteId,
                null);
    }

    @Transactional
    public MovimientoInventario registrarEntradaProduccionTerminado(LoteQueso lote, BigDecimal cantidad, LocalDate fecha) {
        return registrarLoteQueso(
                TipoMovimientoInventario.ENTRADA_PRODUCCION_TERMINADO,
                lote,
                cantidad,
                fecha,
                DocumentoOrigenTipo.PRODUCCION,
                lote.getId(),
                null);
    }

    @Transactional
    public MovimientoInventario registrarSalidaVenta(LoteQueso lote, BigDecimal cantidad, LocalDate fecha, Long ventaId) {
        return registrarLoteQueso(
                TipoMovimientoInventario.SALIDA_VENTA, lote, cantidad, fecha, DocumentoOrigenTipo.VENTA, ventaId, null);
    }

    @Transactional
    public MovimientoInventario registrarEntradaAnulacionVenta(
            LoteQueso lote, BigDecimal cantidad, LocalDate fecha, Long ventaId, String motivo) {
        return registrarLoteQueso(
                TipoMovimientoInventario.ENTRADA_ANULACION_VENTA,
                lote,
                cantidad,
                fecha,
                DocumentoOrigenTipo.VENTA,
                ventaId,
                "Anulacion: " + motivo);
    }

    private MovimientoInventario registrarMateriaPrima(
            TipoMovimientoInventario tipo,
            MateriaPrima materiaPrima,
            BigDecimal cantidad,
            LocalDate fecha,
            DocumentoOrigenTipo origenTipo,
            Long origenId,
            String observaciones) {
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setTipo(tipo);
        movimiento.setMateriaPrima(materiaPrima);
        movimiento.setCantidad(cantidad);
        movimiento.setFecha(fecha);
        movimiento.setDocumentoOrigenTipo(origenTipo);
        movimiento.setDocumentoOrigenId(origenId);
        movimiento.setUsuario(actor());
        movimiento.setObservaciones(observaciones);
        return movimientoRepository.save(movimiento);
    }

    private MovimientoInventario registrarLoteQueso(
            TipoMovimientoInventario tipo,
            LoteQueso lote,
            BigDecimal cantidad,
            LocalDate fecha,
            DocumentoOrigenTipo origenTipo,
            Long origenId,
            String observaciones) {
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setTipo(tipo);
        movimiento.setLoteQueso(lote);
        movimiento.setCantidad(cantidad);
        movimiento.setFecha(fecha);
        movimiento.setDocumentoOrigenTipo(origenTipo);
        movimiento.setDocumentoOrigenId(origenId);
        movimiento.setUsuario(actor());
        movimiento.setObservaciones(observaciones);
        return movimientoRepository.save(movimiento);
    }

    private static String actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "sistema";
    }
}
