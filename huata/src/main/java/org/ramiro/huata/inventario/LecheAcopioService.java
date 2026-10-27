package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import org.ramiro.huata.parametros.ReglasLecheService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * La leche del acopio en el inventario (V31) y en la contabilidad (V33).
 *
 * <p><b>Cuanta leche entra la decide quien llama</b> ({@code RecepcionLecheService}: lo recibido en
 * planta menos lo que no es apto). Este servicio solo lleva el almacen hasta ese objetivo: calcula
 * lo que ya entro por ese documento (una ruta y dia, o una entrega directa) y mueve la diferencia.
 * Asi una correccion de planta, o un control de calidad cargado despues, dejan el almacen bien sin
 * duplicar nada. Nunca se edita un movimiento ya escrito.
 *
 * <p><b>Costo.</b> El que se configura en "Reglas de la leche"; si no hay, la tarifa normal vigente
 * ese dia (lo que se paga al proveedor). Queda como costo de referencia con el que produccion
 * costea el lote.
 *
 * <p><b>Asiento.</b> La liquidacion al proveedor ya registra la compra (6021 contra caja). La
 * recepcion hace el asiento de destino del PCGE: Debe 2411 Materias primas / Haber 6121 Variacion
 * de existencias. Una correccion a la baja lo revierte por la diferencia.
 */
@Service
@RequiredArgsConstructor
public class LecheAcopioService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final InventarioService inventarioService;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final ReglasLecheService reglasLeche;
    private final AsientoContableService asientos;

    /** La materia prima "leche del acopio". Existe siempre desde V31. */
    public MateriaPrima leche() {
        return materiaPrimaRepository
                .findFirstByLecheAcopioTrue()
                .orElseThrow(() -> new BusinessRuleException(
                        "No está configurada la materia prima de la leche del acopio. Revise el inventario."));
    }

    /** Lo que ya entro al almacen por un documento (neto de correcciones). */
    public BigDecimal yaEntro(DocumentoOrigenTipo origen, Long origenId) {
        return movimientoRepository.netoPorOrigen(leche().getId(), origen, origenId);
    }

    /**
     * Lleva la leche de un documento hasta {@code objetivo} litros. Participa en la transaccion de
     * quien lo llama: si la recepcion o el control fallan, no queda leche ni asiento a medias.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void ajustarHasta(
            BigDecimal objetivo, LocalDate fecha, DocumentoOrigenTipo origen, Long origenId, String descripcion) {
        BigDecimal meta = objetivo.max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
        BigDecimal delta = meta.subtract(yaEntro(origen, origenId));
        if (delta.signum() == 0) {
            return;
        }
        BigDecimal costo = costoPorLitro(fecha);
        if (delta.signum() > 0) {
            MateriaPrima leche = leche();
            if (costo != null) {
                leche.setCostoUnitarioReferencial(costo);
                materiaPrimaRepository.save(leche);
            }
            inventarioService.registrarEntradaAcopio(leche, delta, fecha, origen, origenId, descripcion);
            asentar(fecha, delta, costo, true, descripcion, origenId);
            return;
        }
        // Salida: se bloquea la fila como al producir, para que dos correcciones simultaneas no lean
        // el mismo saldo.
        BigDecimal aSacar = delta.negate();
        MateriaPrima leche = inventarioService.bloquearMateriaPrima(leche().getId());
        BigDecimal disponible = inventarioService.saldoMateriaPrima(leche.getId());
        if (disponible.compareTo(aSacar) < 0) {
            throw new BusinessRuleException("No se puede bajar " + aSacar.stripTrailingZeros().toPlainString()
                    + " L de la leche recibida: en el almacén quedan " + disponible.stripTrailingZeros().toPlainString()
                    + " L, el resto ya se usó en producción. Corrija primero con un ajuste de inventario.");
        }
        inventarioService.registrarAjusteSalida(leche, aSacar, fecha, origen, origenId, "Corrección: " + descripcion);
        asentar(fecha, aSacar, costo, false, descripcion, origenId);
    }

    /** Costo configurado en "Reglas de la leche"; si no hay, la tarifa normal vigente ese dia. */
    public BigDecimal costoPorLitro(LocalDate fecha) {
        BigDecimal configurado = reglasLeche.vigentes().costoLecheLitro();
        if (configurado != null) {
            return configurado;
        }
        return tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fecha)
                .map(t -> BigDecimal.valueOf(t.getPrecioNormal()))
                .orElse(null);
    }

    /** Asiento de destino (entrada) o su reversion (salida), por la diferencia valorizada. */
    private void asentar(
            LocalDate fecha, BigDecimal litros, BigDecimal costo, boolean entrada, String descripcion, Long origenId) {
        if (costo == null) {
            return; // sin costo no hay importe que asentar; la cantidad igual queda en el kardex
        }
        BigDecimal importe = litros.multiply(costo).setScale(2, RoundingMode.HALF_UP);
        if (importe.signum() == 0) {
            return;
        }
        String litrosTexto = litros.stripTrailingZeros().toPlainString();
        asientos.registrar(
                fecha,
                (entrada ? "Leche recibida en planta: " : "Corrección de leche recibida: ") + litrosTexto + " L",
                (entrada ? "Por el ingreso al almacén de " : "Por la salida del almacén de ") + litrosTexto
                        + " L de leche a S/ " + costo.stripTrailingZeros().toPlainString() + " (" + descripcion + ").",
                OrigenAsiento.RECEPCION_LECHE,
                origenId,
                entrada
                        ? List.of(LineaContable.debe("2411", importe), LineaContable.haber("6121", importe))
                        : List.of(LineaContable.debe("6121", importe), LineaContable.haber("2411", importe)));
    }
}
