package org.ramiro.huata.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libro diario / libro mayor / plan de cuentas de la queseria (partida doble).
 *
 * <p><b>Todo asiento se genera automaticamente</b> desde {@code CompraService}, {@code
 * QueseriaService} (produccion y venta) — no hay una pantalla de registro manual todavia. Un asiento
 * que no cuadra (suma de debe distinta de suma de haber) nunca llega a guardarse: es un error de
 * programacion en quien lo genero, no un dato de negocio invalido, y por eso lanza
 * {@link IllegalStateException} y no una excepcion de regla de negocio.
 *
 * <p><b>El plan de cuentas y el IGV son una decision de negocio del cliente</b>, sembrados desde el
 * prototipo que entrego (queseria.html): ver el comentario de la migracion V21 y el de {@link
 * PlanCuenta}. No se inventaron aqui.
 */
@Service
@RequiredArgsConstructor
public class AsientoContableService {

    private final PlanCuentaRepository planCuentaRepository;
    private final AsientoContableRepository asientoContableRepository;
    private final AsientoLineaRepository asientoLineaRepository;

    /**
     * Registra un asiento con sus lineas. Las lineas con debe = 0 y haber = 0 se descartan (por
     * ejemplo, mano de obra de un lote que no tuvo, o IGV de una venta exonerada): no tiene sentido
     * imprimir una linea de un movimiento que no existio.
     */
    @Transactional
    public AsientoContable registrar(
            LocalDate fecha, String glosa, String comentario, OrigenAsiento origenTipo, Long origenId, List<LineaContable> lineas) {
        List<LineaContable> lineasConMonto = lineas.stream()
                .filter(l -> l.debe().signum() != 0 || l.haber().signum() != 0)
                .toList();
        if (lineasConMonto.isEmpty()) {
            throw new IllegalStateException("Un asiento contable no puede registrarse sin lineas con monto: " + glosa);
        }

        BigDecimal totalDebe = lineasConMonto.stream().map(LineaContable::debe).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHaber = lineasConMonto.stream().map(LineaContable::haber).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDebe.compareTo(totalHaber) != 0) {
            throw new IllegalStateException(
                    "Partida doble incumplida en '" + glosa + "': debe " + totalDebe + " != haber " + totalHaber);
        }

        AsientoContable asiento = new AsientoContable();
        asiento.setFecha(fecha);
        asiento.setGlosa(glosa);
        asiento.setComentario(comentario);
        asiento.setOrigenTipo(origenTipo);
        asiento.setOrigenId(origenId);

        for (LineaContable l : lineasConMonto) {
            PlanCuenta cuenta = planCuentaRepository
                    .findByCodigoAndActivoTrue(l.codigoCuenta())
                    .orElseThrow(() -> NotFoundException.of("Cuenta contable", l.codigoCuenta()));
            AsientoLinea linea = new AsientoLinea();
            linea.setAsiento(asiento);
            linea.setCuenta(cuenta);
            linea.setDebe(l.debe());
            linea.setHaber(l.haber());
            asiento.getLineas().add(linea);
        }
        return asientoContableRepository.save(asiento);
    }

    @Transactional(readOnly = true)
    public List<AsientoContableResponse> libroDiario(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusMonths(1);
        return asientoContableRepository.findConLineasByFechaBetween(inicio, fin).stream()
                .map(AsientoContableResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanCuentaResponse> planCuentas() {
        return planCuentaRepository.findByActivoTrueOrderByCodigoAsc().stream().map(PlanCuentaResponse::from).toList();
    }

    /** Solo las cuentas con algun movimiento: evita ofrecer en el selector cuentas siempre vacias. */
    @Transactional(readOnly = true)
    public List<PlanCuentaResponse> cuentasConMovimiento() {
        List<String> codigos = asientoLineaRepository.codigosDeCuentasConMovimiento();
        return planCuentaRepository.findByActivoTrueOrderByCodigoAsc().stream()
                .filter(c -> codigos.contains(c.getCodigo()))
                .map(PlanCuentaResponse::from)
                .toList();
    }

    /**
     * Saldo acumulado igual que el prototipo: {@code saldo += debe - haber}, sin distinguir cuentas
     * deudoras de acreedoras (una cuenta de pasivo/ingreso queda con saldo negativo cuando esta "al
     * dia", que es exactamente como lo pinta queseria.html con la etiqueta D/H segun el signo).
     */
    @Transactional(readOnly = true)
    public LibroMayorResponse libroMayor(String codigoCuenta, LocalDate desde, LocalDate hasta) {
        PlanCuenta cuenta = planCuentaRepository
                .findByCodigoAndActivoTrue(codigoCuenta)
                .orElseThrow(() -> NotFoundException.of("Cuenta contable", codigoCuenta));
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusMonths(1);

        BigDecimal saldo = BigDecimal.ZERO;
        List<MovimientoMayorResponse> movimientos = new java.util.ArrayList<>();
        for (AsientoLinea linea : asientoLineaRepository.findPorCuentaYFecha(codigoCuenta, inicio, fin)) {
            saldo = saldo.add(linea.getDebe()).subtract(linea.getHaber());
            movimientos.add(new MovimientoMayorResponse(
                    linea.getAsiento().getFecha(), linea.getAsiento().getId(), linea.getAsiento().getGlosa(),
                    linea.getDebe(), linea.getHaber(), saldo));
        }
        return new LibroMayorResponse(cuenta.getCodigo(), cuenta.getNombre(), movimientos, saldo);
    }
}
