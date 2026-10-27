package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajuste manual del stock de una materia prima (V59): merma, conteo fisico, leche descartada o
 * cortada. Nunca edita un movimiento ya escrito: deja el ajuste con su motivo, un movimiento
 * {@code AJUSTE_ENTRADA}/{@code AJUSTE_SALIDA} que apunta a el, y el asiento por la diferencia.
 *
 * <p><b>Asiento.</b> Valorizado al costo de referencia de la materia prima, con el mismo par de
 * cuentas que ya usa la correccion de la leche recibida ({@link LecheAcopioService}): 2411 Materias
 * primas contra 6121 Variacion de existencias. Sin costo de referencia no hay importe que asentar;
 * la cantidad igual queda en el kardex.
 */
@Service
@RequiredArgsConstructor
public class AjusteInventarioService {

    private final AjusteInventarioRepository ajusteRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final InventarioService inventarioService;
    private final AsientoContableService asientos;
    private final AuditoriaService auditoriaService;

    @Transactional
    public AjusteInventario registrar(AjusteInventarioRequest req) {
        LocalDate fecha = req.fecha() != null ? req.fecha() : LocalDate.now();
        if (fecha.isAfter(LocalDate.now())) {
            throw new BusinessRuleException("El ajuste no puede registrarse con fecha futura.");
        }
        String motivo = req.motivo() == null ? "" : req.motivo().trim();
        if (motivo.length() < 3) {
            throw new BusinessRuleException("Indica el motivo del ajuste (merma, conteo físico, leche descartada…).");
        }
        BigDecimal cantidad = req.cantidad().setScale(4, RoundingMode.HALF_UP);
        if (cantidad.signum() <= 0) {
            throw new BusinessRuleException("La cantidad del ajuste debe ser mayor a cero.");
        }

        boolean salida = req.tipo() == AjusteInventario.Tipo.SALIDA;
        // Bloqueo de fila igual que al producir: dos salidas simultaneas no pueden leer el mismo saldo.
        MateriaPrima materiaPrima = salida
                ? inventarioService.bloquearMateriaPrima(req.materiaPrimaId())
                : materiaPrimaRepository
                        .findById(req.materiaPrimaId())
                        .filter(MateriaPrima::isActivo)
                        .orElseThrow(() -> org.ramiro.huata.common.NotFoundException.of("Materia prima", req.materiaPrimaId()));
        String unidad = materiaPrima.getUnidadMedida().name().toLowerCase(java.util.Locale.ROOT);
        if (salida) {
            BigDecimal disponible = inventarioService.saldoMateriaPrima(materiaPrima.getId());
            if (disponible.compareTo(cantidad) < 0) {
                throw new BusinessRuleException("No se puede sacar " + legible(cantidad) + " " + unidad + " de "
                        + materiaPrima.getNombre() + ": en el almacén hay " + legible(disponible) + " " + unidad + ".");
            }
        }

        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setMateriaPrima(materiaPrima);
        ajuste.setTipo(req.tipo());
        ajuste.setCantidad(cantidad);
        ajuste.setFecha(fecha);
        ajuste.setMotivo(motivo);
        AjusteInventario guardado = ajusteRepository.save(ajuste);

        if (salida) {
            inventarioService.registrarAjusteSalida(
                    materiaPrima, cantidad, fecha, DocumentoOrigenTipo.AJUSTE, guardado.getId(), motivo);
        } else {
            inventarioService.registrarAjusteEntrada(
                    materiaPrima, cantidad, fecha, DocumentoOrigenTipo.AJUSTE, guardado.getId(), motivo);
        }
        asentar(guardado);

        auditoriaService.registrar(
                "Ajuste de inventario",
                "Inventario",
                materiaPrima.getNombre() + " · " + (salida ? "−" : "+") + legible(cantidad) + " " + unidad + " · " + motivo);
        return guardado;
    }

    private void asentar(AjusteInventario ajuste) {
        BigDecimal costo = ajuste.getMateriaPrima().getCostoUnitarioReferencial();
        if (costo == null || costo.signum() <= 0) {
            return;
        }
        BigDecimal importe = ajuste.getCantidad().multiply(costo).setScale(2, RoundingMode.HALF_UP);
        if (importe.signum() == 0) {
            return;
        }
        boolean salida = ajuste.getTipo() == AjusteInventario.Tipo.SALIDA;
        String nombre = ajuste.getMateriaPrima().getNombre();
        asientos.registrar(
                ajuste.getFecha(),
                (salida ? "Ajuste de inventario (salida): " : "Ajuste de inventario (entrada): ") + nombre,
                "Por el ajuste de " + legible(ajuste.getCantidad()) + " de " + nombre + " a S/ " + legible(costo)
                        + " (" + ajuste.getMotivo() + ").",
                OrigenAsiento.AJUSTE_INVENTARIO,
                ajuste.getId(),
                salida
                        ? List.of(LineaContable.debe("6121", importe), LineaContable.haber("2411", importe))
                        : List.of(LineaContable.debe("2411", importe), LineaContable.haber("6121", importe)));
    }

    private static String legible(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }
}
