package org.ramiro.huata.liquidaciones;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Instant;
import java.util.Comparator;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula liquidaciones a partir de los recojos SYNCED (no cancelados) de un productor en un
 * periodo, aplicando la tarifa vigente. Regla de precio por calidad simplificada y pendiente de
 * validar con Huata (C-01): ACEPTADA usa precioNormal; cualquier otro resultado (u OBSERVADA
 * explicita) usa precioObservada. Un recojo sin control de calidad aun se asume ACEPTADA.
 */
@Service
@RequiredArgsConstructor
public class LiquidacionService {

    /**
     * El ciclo jueves-miercoles, el "hoy" y cada corte de periodo son en hora de Lima. Antes los
     * periodos se cortaban en UTC: lo entregado el miercoles despues de las 19:00 entraba en el pago
     * de la semana siguiente, y no coincidia con el resumen que ve el proveedor en la app (que ya
     * usaba esta zona). Las liquidaciones ya CERRADAS no se recalculan (ver {@link #calcular}).
     */
    private static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");

    private final LiquidacionRepository liquidacionRepository;
    private final LiquidacionDetalleRepository liquidacionDetalleRepository;
    private final AjusteLiquidacionRepository ajusteLiquidacionRepository;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final ProductorRepository productorRepository;
    private final RecojoRepository recojoRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final AuditoriaService auditoriaService;
    private final AsientoContableService asientoContableService;

    /**
     * Ciclo jueves-miércoles vigente hoy (regla 7.6). Mismo criterio que
     * {@code huata-web/src/app/core/config/ciclos.ts}: no se duplica el cálculo por casualidad, cada
     * stack lo implementa una vez porque el día de corte es una decisión de la cooperativa, no del
     * código, y ambos deben moverse juntos si cambia.
     */
    public static LocalDate[] cicloActual() {
        LocalDate hoy = LocalDate.now(ZONA_HUATA);
        int diasDesdeJueves = (hoy.getDayOfWeek().getValue() + 3) % 7;
        LocalDate inicio = hoy.minusDays(diasDesdeJueves);
        return new LocalDate[] {inicio, inicio.plusDays(6)};
    }

    /**
     * Cobro estimado del ciclo en curso para un proveedor (RF-80): la app del proveedor lo llama sin
     * crear ninguna {@link Liquidacion} — es la misma fila que ve Secretaría en la planilla del
     * ciclo, filtrada a un solo proveedor, para no tener una tercera copia de la regla de precios.
     * El monto es "estimado hasta el cierre del ciclo": una liquidación real puede diferir si
     * después se detecta una incidencia de calidad retroactiva sobre entregas ya contadas aquí.
     */
    public FilaPlanilla estimadoActual(Long productorId) {
        Productor productor =
                productorRepository.findById(productorId).orElseThrow(() -> NotFoundException.of("Productor", productorId));
        TarifaLeche tarifa = tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now(ZONA_HUATA))
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay una tarifa de leche configurada. Configure una en Parametros antes de calcular liquidaciones."));
        LocalDate[] ciclo = cicloActual();
        Instant desde = ciclo[0].atStartOfDay(ZONA_HUATA).toInstant();
        Instant hasta = ciclo[1].plusDays(1).atStartOfDay(ZONA_HUATA).toInstant();
        return filaDe(productor, tarifa, ciclo[0], ciclo[1], desde, hasta);
    }

    /** Resumen financiero por dia, semana, mes o rango para la app del proveedor. */
    public ResumenPagoProveedor resumenPagoProveedor(Long productorId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde) || hasta.isAfter(desde.plusYears(1))) {
            throw new BusinessRuleException("El periodo consultado no es valido");
        }
        Productor productor = productorRepository
                .findById(productorId)
                .orElseThrow(() -> NotFoundException.of("Productor", productorId));
        Instant inicio = desde.atStartOfDay(ZONA_HUATA).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant();
        List<Recojo> recojos = recojoRepository
                .findByFechaHoraGreaterThanEqualAndFechaHoraLessThanAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED)
                .stream()
                .filter(r -> r.getProductor().getId().equals(productorId))
                .toList();
        List<Liquidacion> pagadas = liquidacionRepository
                .findByProductorIdAndActivoTrueAndEstadoAndPeriodoFinGreaterThanEqualAndPeriodoInicioLessThanEqual(
                        productorId, EstadoLiquidacion.CLOSED, desde, hasta);
        boolean excelencia = productor.isExcelencia()
                && recojos.stream().allMatch(r -> resultadoDe(r) == ResultadoCalidad.ACEPTADA);
        double montoPendiente = recojos.stream()
                .filter(r -> !cubiertoPorPago(r, pagadas))
                .mapToDouble(r -> {
                    ResultadoCalidad resultado = resultadoDe(r);
                    if (resultado == ResultadoCalidad.RECHAZADA) return 0.0;
                    LocalDate fecha = r.getFechaHora().atZone(ZONA_HUATA).toLocalDate();
                    TarifaLeche tarifa = tarifaLecheRepository
                            .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(fecha)
                            .orElseThrow(() -> new BusinessRuleException("No hay tarifa de leche para " + fecha));
                    return precioDe(resultado, tarifa, excelencia) * r.getLitros();
                })
                .sum();
        double litrosPendientes = recojos.stream()
                .filter(r -> !cubiertoPorPago(r, pagadas))
                .filter(r -> resultadoDe(r) != ResultadoCalidad.RECHAZADA)
                .mapToDouble(Recojo::getLitros)
                .sum();
        double litrosPagados = recojos.stream()
                .filter(r -> cubiertoPorPago(r, pagadas))
                .mapToDouble(Recojo::getLitros)
                .sum();
        double montoPagado = pagadas.stream().mapToDouble(l -> montoPagadoEnRango(l, desde, hasta)).sum();
        return new ResumenPagoProveedor(
                desde,
                hasta,
                recojos.stream().mapToDouble(Recojo::getLitros).sum(),
                litrosPagados,
                litrosPendientes,
                montoPagado,
                montoPendiente,
                recojos.size(),
                recojos.stream().filter(r -> resultadoDe(r) == ResultadoCalidad.ACEPTADA).count(),
                recojos.stream().filter(r -> resultadoDe(r) == ResultadoCalidad.OBSERVADA).count(),
                recojos.stream().filter(r -> resultadoDe(r) == ResultadoCalidad.RECHAZADA).count());
    }

    private boolean cubiertoPorPago(Recojo recojo, List<Liquidacion> pagadas) {
        LocalDate fecha = recojo.getFechaHora().atZone(ZONA_HUATA).toLocalDate();
        return pagadas.stream().anyMatch(l -> !fecha.isBefore(l.getPeriodoInicio()) && !fecha.isAfter(l.getPeriodoFin()));
    }

    private double montoPagadoEnRango(Liquidacion liquidacion, LocalDate desde, LocalDate hasta) {
        double subtotal = liquidacionDetalleRepository.findByLiquidacionId(liquidacion.getId()).stream()
                .filter(d -> {
                    LocalDate fecha = d.getRecojo().getFechaHora().atZone(ZONA_HUATA).toLocalDate();
                    return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
                })
                .mapToDouble(LiquidacionDetalle::getSubtotal)
                .sum();
        double proporcion = liquidacion.getMontoTotal() > 0 ? subtotal / liquidacion.getMontoTotal() : 0.0;
        return subtotal + liquidacion.getAjustesTotal() * proporcion;
    }

    public record ResumenPagoProveedor(
            LocalDate desde,
            LocalDate hasta,
            double litrosProducidos,
            double litrosPagados,
            double litrosPendientes,
            double montoPagado,
            double montoPendiente,
            long entregas,
            long aceptadas,
            long observadas,
            long rechazadas) {}

    public PageResponse<Liquidacion> buscar(Long productorId, EstadoLiquidacion estado, Pageable pageable) {
        // Las inactivadas (duplicados historicos, ver V4) no se listan.
        Specification<Liquidacion> spec = SearchSpecifications.<Liquidacion>alwaysTrue()
                .and((root, cq, cb) -> cb.isTrue(root.get("activo")))
                .and(productorId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("productor").get("id"), productorId))
                .and(SearchSpecifications.equalsIfPresent("estado", estado));
        return PageResponse.from(liquidacionRepository.findAll(spec, pageable));
    }

    public Liquidacion obtener(Long id) {
        return liquidacionRepository.findById(id).orElseThrow(() -> NotFoundException.of("Liquidacion", id));
    }

    public List<LiquidacionDetalle> detalle(Long liquidacionId) {
        return liquidacionDetalleRepository.findByLiquidacionId(liquidacionId);
    }

    /** Sobrecarga pública para otros módulos (p. ej. la carga de datos DEMO) sin exponer el DTO del controlador. */
    @Transactional
    public Liquidacion calcular(Long productorId, LocalDate periodoInicio, LocalDate periodoFin) {
        return calcular(new LiquidacionCalcularRequest(productorId, periodoInicio, periodoFin));
    }

    @Transactional
    public Liquidacion calcular(LiquidacionCalcularRequest req) {
        Productor productor = productorRepository
                .findById(req.productorId())
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));

        TarifaLeche tarifa = tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now(ZONA_HUATA))
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay una tarifa de leche configurada. Configure una en Parametros antes de calcular liquidaciones."));

        List<Recojo> recojos = recojoRepository.findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                productor.getId(),
                req.periodoInicio().atStartOfDay(ZONA_HUATA).toInstant(),
                req.periodoFin().plusDays(1).atStartOfDay(ZONA_HUATA).toInstant(),
                EstadoRecojo.CANCELLED);

        // Si ya hay una liquidacion cerrada para este proveedor y periodo, se falla en vez de seguir.
        //
        // Antes esta condicion era un .filter(estado != CLOSED) encadenado al orElseGet, con lo que
        // recalcular un periodo ya pagado NO reutilizaba la existente: creaba una segunda
        // liquidacion para el mismo proveedor y la misma semana. El proveedor acababa con dos
        // liquidaciones de la misma semana y los reportes sumaban dos veces.
        Liquidacion liquidacion = liquidacionRepository
                .findByProductorIdAndPeriodoInicioAndPeriodoFinAndActivoTrue(productor.getId(), req.periodoInicio(), req.periodoFin())
                .map(existente -> {
                    if (existente.getEstado() == EstadoLiquidacion.CLOSED) {
                        throw new BusinessRuleException(
                                "La liquidación de " + productor.getCodigo() + " para el periodo "
                                        + req.periodoInicio() + " a " + req.periodoFin()
                                        + " ya está cerrada y no se puede recalcular.");
                    }
                    return existente;
                })
                .orElseGet(Liquidacion::new);

        if (liquidacion.getId() != null) {
            liquidacionDetalleRepository.deleteAll(liquidacionDetalleRepository.findByLiquidacionId(liquidacion.getId()));
        }

        liquidacion.setProductor(productor);
        liquidacion.setPeriodoInicio(req.periodoInicio());
        liquidacion.setPeriodoFin(req.periodoFin());
        liquidacion.setEstado(EstadoLiquidacion.CALCULATED);

        double litrosTotales = 0.0;
        double montoTotal = 0.0;
        Liquidacion liquidacionGuardada = liquidacionRepository.save(liquidacion);

        // 7.4: la tarifa de Excelencia se pierde para TODO el ciclo en cuanto hay una incidencia,
        // asi que hay que saberlo antes de recorrer las recepciones, no dentro del bucle.
        boolean cicloSinIncidencias = recojos.stream().allMatch(r -> resultadoDe(r) == ResultadoCalidad.ACEPTADA);
        boolean cobraExcelencia =
                productor.isExcelencia() && cicloSinIncidencias && tarifa.getPrecioExcelencia() != null;

        for (Recojo recojo : recojos) {
            double precio = precioDe(resultadoDe(recojo), tarifa, cobraExcelencia);
            double subtotal = precio * recojo.getLitros();

            LiquidacionDetalle detalle = new LiquidacionDetalle();
            detalle.setLiquidacion(liquidacionGuardada);
            detalle.setRecojo(recojo);
            detalle.setLitros(recojo.getLitros());
            detalle.setPrecioAplicado(precio);
            detalle.setSubtotal(subtotal);
            liquidacionDetalleRepository.save(detalle);

            litrosTotales += recojo.getLitros();
            montoTotal += subtotal;
        }

        liquidacionGuardada.setLitrosTotales(litrosTotales);
        liquidacionGuardada.setMontoTotal(montoTotal);
        // Los ajustes son entradas manuales y sobreviven al recalculo: solo se vuelve a sumar su
        // efecto sobre el bruto recien calculado.
        aplicarAjustes(liquidacionGuardada);
        Liquidacion resultado = liquidacionRepository.save(liquidacionGuardada);
        auditoriaService.registrar(
                "Cálculo", "Liquidación", productor.getCodigo() + " · " + req.periodoInicio() + " a " + req.periodoFin());
        return resultado;
    }

    /**
     * Planilla del ciclo: que le queda por cobrar a cada proveedor.
     *
     * <p>Es la consulta que alimenta las dos pantallas de Secretaria. Se calcula en el backend y no
     * en el navegador a proposito: el importe pendiente sale de la misma funcion de tarifa que usa
     * la liquidacion real ({@code precioDe}), de modo que lo que se muestra antes de pagar y lo que
     * se paga despues no pueden diferir. Calcularlo en el cliente seria una tercera copia de la
     * regla de precios.
     *
     * <p>"Pendiente" significa "todavia no esta dentro de una liquidacion CERRADA". Una liquidacion
     * calculada o en revision no descuenta nada: hasta que no se cierra, ese dinero no se pago.
     */
    public List<FilaPlanilla> planillaDelCiclo(LocalDate periodoInicio, LocalDate periodoFin) {
        TarifaLeche tarifa = tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now(ZONA_HUATA))
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay una tarifa de leche configurada. Configure una en Parametros antes de liquidar."));

        Instant desde = periodoInicio.atStartOfDay(ZONA_HUATA).toInstant();
        Instant hasta = periodoFin.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant();

        return productorRepository.findAll().stream()
                .filter(Productor::isActivo)
                .map(productor -> filaDe(productor, tarifa, periodoInicio, periodoFin, desde, hasta))
                .filter(fila -> fila.litrosEntregados() > 0)
                .sorted(Comparator.comparing(FilaPlanilla::proveedorCodigo))
                .toList();
    }

    private FilaPlanilla filaDe(
            Productor productor,
            TarifaLeche tarifa,
            LocalDate periodoInicio,
            LocalDate periodoFin,
            Instant desde,
            Instant hasta) {
        List<Recojo> recojos = recojoRepository.findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                productor.getId(), desde, hasta, EstadoRecojo.CANCELLED);

        double entregados = recojos.stream().mapToDouble(Recojo::getLitros).sum();
        double rechazados = recojos.stream()
                .filter(r -> resultadoDe(r) == ResultadoCalidad.RECHAZADA)
                .mapToDouble(Recojo::getLitros)
                .sum();

        boolean cicloSinIncidencias = recojos.stream().allMatch(r -> resultadoDe(r) == ResultadoCalidad.ACEPTADA);
        boolean cobraExcelencia =
                productor.isExcelencia() && cicloSinIncidencias && tarifa.getPrecioExcelencia() != null;

        Optional<Liquidacion> liquidacionOpt = liquidacionRepository
                .findByProductorIdAndPeriodoInicioAndPeriodoFinAndActivoTrue(productor.getId(), periodoInicio, periodoFin);
        boolean yaLiquidado = liquidacionOpt.map(l -> l.getEstado() == EstadoLiquidacion.CLOSED).orElse(false);

        // Que recojos ya quedaron DENTRO de la liquidacion cerrada (lo realmente pagado). Antes esta
        // funcion, al ver "yaLiquidado", reportaba como pagado el total recalculado en caliente de
        // TODOS los recojos del periodo — asi que un acopio registrado despues del cierre (por
        // ejemplo, un recojo tardio) desaparecia como si ya se hubiera pagado, en vez de seguir
        // pendiente. El dinero de ese acopio no se perdia en la base, pero la planilla dejaba de
        // decir que se debia: por eso se compara contra liquidacion_detalles, no contra el total.
        Set<Long> recojosCubiertos = yaLiquidado
                ? liquidacionDetalleRepository.findByLiquidacionId(liquidacionOpt.get().getId()).stream()
                        .map(d -> d.getRecojo().getId())
                        .collect(Collectors.toSet())
                : Set.of();
        List<Recojo> pendientes =
                yaLiquidado ? recojos.stream().filter(r -> !recojosCubiertos.contains(r.getId())).toList() : recojos;

        // "Pendiente"/"pagado" son siempre litros PAGABLES (igual que antes de este fix): un
        // rechazado no cuenta ni como pendiente ni como pagado, solo se ve en litrosRechazados.
        double pagables = entregados - rechazados;
        double totalPendienteBruto = pendientes.stream().mapToDouble(Recojo::getLitros).sum();
        double rechazadosPendientes = pendientes.stream()
                .filter(r -> resultadoDe(r) == ResultadoCalidad.RECHAZADA)
                .mapToDouble(Recojo::getLitros)
                .sum();
        double pagablesPendientes = totalPendienteBruto - rechazadosPendientes;
        double pagablesPagados = pagables - pagablesPendientes;
        double importePendiente = pendientes.stream()
                .mapToDouble(r -> precioDe(resultadoDe(r), tarifa, cobraExcelencia) * r.getLitros())
                .sum();

        double tarifaAplicada = pagablesPendientes > 0
                ? importePendiente / pagablesPendientes
                : (yaLiquidado ? tarifaPromedioDe(liquidacionOpt.get()) : 0.0);

        return new FilaPlanilla(
                productor.getId(),
                productor.getCodigo(),
                productor.getNombres() + " " + productor.getApellidos(),
                productor.getApellidos(),
                productor.getRuta() != null ? productor.getRuta().getZona() : null,
                productor.isExcelencia(),
                entregados,
                rechazados,
                pagablesPagados,
                pagablesPendientes,
                tarifaAplicada,
                importePendiente,
                // "Pagado" significa cero pendiente, no solo "existe una liquidacion cerrada": una
                // liquidacion cerrada con acopios nuevos sin cubrir sigue debiendo dinero.
                yaLiquidado && pendientes.isEmpty(),
                yaLiquidado ? liquidacionOpt.get().getId() : null,
                yaLiquidado);
    }

    private static double tarifaPromedioDe(Liquidacion liquidacion) {
        return liquidacion.getLitrosTotales() != null && liquidacion.getLitrosTotales() > 0 && liquidacion.getMontoTotal() != null
                ? liquidacion.getMontoTotal() / liquidacion.getLitrosTotales()
                : 0.0;
    }

    /**
     * Una fila de la planilla.
     *
     * @param litrosRechazados se muestran aparte y no se pagan (7.1 regla 1); ocultarlos haria que
     *     los litros de la planilla no cuadraran con los que el proveedor recuerda haber entregado
     * @param tarifaAplicada promedio real del ciclo, no la de catalogo: con calidades mezcladas no
     *     existe "una" tarifa
     * @param liquidado sin pendiente por cobrar: false si hay acopios sin cubrir aunque exista una
     *     liquidacion cerrada para el periodo (ver liquidacionId)
     * @param liquidacionId la liquidacion del periodo si ya se calculo o cerro alguna vez, exista o
     *     no saldo pendiente; null si nunca se calculo. Sirve para abrir el detalle o pedir su
     *     reapertura sin tener que volver a buscarla por proveedor+periodo.
     * @param tieneLiquidacionCerrada distingue "nunca se cerro" (false, se puede calcular) de "esta
     *     cerrada, con o sin saldo pendiente" (true, solo se puede tocar reabriendola)
     * @param proveedorApellidos aparte de proveedorNombre (que va "Nombres Apellidos") para que la
     *     web pueda ordenar de verdad por apellido -- partir el nombre completo a mano no es
     *     confiable con nombres compuestos
     */
    public record FilaPlanilla(
            Long proveedorId,
            String proveedorCodigo,
            String proveedorNombre,
            String proveedorApellidos,
            String zona,
            boolean excelencia,
            double litrosEntregados,
            double litrosRechazados,
            double litrosPagados,
            double litrosPendientes,
            double tarifaAplicada,
            double importePendiente,
            boolean liquidado,
            Long liquidacionId,
            boolean tieneLiquidacionCerrada) {}

    /**
     * Resultado de calidad de una recepcion. Sin control registrado se asume ACEPTADA: es leche que
     * entro a planta y nadie objeto: no pagarla por falta de un papel seria peor que pagarla.
     */
    private ResultadoCalidad resultadoDe(Recojo recojo) {
        return controlCalidadRepository
                .findByRecojoId(recojo.getId())
                .map(ControlCalidad::getResultado)
                .orElse(ResultadoCalidad.ACEPTADA);
    }

    /**
     * Traduce el resultado de calidad a precio por litro.
     *
     * <p><b>Una recepcion RECHAZADA no se paga.</b> Hasta aqui el calculo era
     * {@code aceptada ? precioNormal : precioObservada}, de modo que una leche devuelta al proveedor
     * por adulteracion se le liquidaba a tarifa reducida. Es la regla 7.1 (regla 1:
     * {@code accepted = false, price = 0}) aplicada a medias en el unico sitio donde se convierte en
     * dinero.
     *
     * <p>La decision de calidad NO se recalcula aqui: ya la tomo DecisionRecepcionService en la
     * recepcion y quedo guardada. Esto solo traduce esa decision a tarifa, para que no existan dos
     * criterios de calidad en el sistema.
     */
    private double precioDe(ResultadoCalidad resultado, TarifaLeche tarifa, boolean cobraExcelencia) {
        return switch (resultado) {
            case RECHAZADA -> 0.0;
            case OBSERVADA -> tarifa.getPrecioObservada();
            case ACEPTADA -> cobraExcelencia ? tarifa.getPrecioExcelencia() : tarifa.getPrecioNormal();
        };
    }

    /**
     * Mueve la liquidacion por su maquina de estados (revisar, cerrar).
     *
     * <p>Antes esto era un {@code setEstado} sin mas, de modo que se podia devolver a DRAFT una
     * liquidacion CERRADA y, desde ahi, recalcular un periodo ya pagado: el importe entregado al
     * proveedor cambiaba y no quedaba rastro de cual habia sido. Quien decide que transiciones
     * existen es {@link EstadoLiquidacion}, el mismo criterio que ya se aplico a los proveedores.
     *
     * <p>Y queda auditado, porque el cierre es lo que convierte una liquidacion en un pago: sin
     * autor registrado, {@code registros_auditoria} no puede decir quien pago a quien.
     */
    @Transactional
    public Liquidacion cambiarEstado(Long id, CambiarEstadoLiquidacionRequest req) {
        Liquidacion liquidacion = obtener(id);
        // Se lee ANTES de sobrescribirlo: la bitacora tiene que decir de donde venia, no solo a
        // donde fue.
        EstadoLiquidacion anterior = liquidacion.getEstado();
        exigirTransicionValida(anterior, req.estado());

        liquidacion.setEstado(req.estado());
        Liquidacion guardada = liquidacionRepository.save(liquidacion);
        if (req.estado() == EstadoLiquidacion.CLOSED) {
            registrarAsientoDePago(guardada);
        }
        auditoriaService.registrar(
                req.estado() == EstadoLiquidacion.CLOSED ? "Cierre de liquidación" : "Cambio de estado",
                "Liquidación",
                describir(guardada) + " · " + anterior + " -> " + req.estado());
        return guardada;
    }

    /**
     * Debe compras de leche (6021), haber caja (1011): cerrar una liquidacion es el momento en que el
     * sistema considera que el dinero ya se entrego al proveedor (CLOSED es terminal, ver
     * EstadoLiquidacion). Se omite si el neto no es positivo (ajustes que dejan el pago en cero o
     * negativo son un caso de negocio a resolver aparte, no un asiento contable valido).
     */
    private void registrarAsientoDePago(Liquidacion liquidacion) {
        if (liquidacion.getMontoNeto() == null || liquidacion.getMontoNeto() <= 0) {
            return;
        }
        BigDecimal monto = BigDecimal.valueOf(liquidacion.getMontoNeto()).setScale(2, java.math.RoundingMode.HALF_UP);
        // RN-05: el pago del ciclo jueves-miercoles se programa para el viernes siguiente al cierre.
        LocalDate fechaPago = liquidacion.getPeriodoFin().plusDays(2);
        asientoContableService.registrar(
                fechaPago,
                "Pago de liquidación, " + liquidacion.getProductor().getCodigo(),
                "Por el pago de la liquidación del ciclo " + liquidacion.getPeriodoInicio() + " a "
                        + liquidacion.getPeriodoFin() + " a " + liquidacion.getProductor().getCodigo() + ".",
                OrigenAsiento.LIQUIDACION_PAGO,
                liquidacion.getId(),
                List.of(LineaContable.debe("6021", monto), LineaContable.haber("1011", monto)));
    }

    /**
     * Reabre una liquidacion CERRADA para poder recalcularla (por ejemplo, cuando aparece un acopio
     * registrado despues del cierre). Es la UNICA salida de CLOSED que existe en todo el modulo: no
     * pasa por {@link #cambiarEstado} ni por {@link #exigirTransicionValida} a proposito, para no
     * abrir esa transicion a un PATCH generico. El codigo de verificacion (RN de la seccion
     * "Reapertura de pagos") ya se valido en el controlador antes de llegar aqui, igual que con el
     * cambio de precios: esta clase no sabe de correos ni de codigos, solo de que la reapertura fue
     * autorizada.
     *
     * <p>El pago original queda anulado con un asiento en sentido contrario (haber 6021, debe 1011,
     * mismo monto, fecha de hoy): el asiento de {@link #registrarAsientoDePago} no se borra ni se
     * edita, porque un asiento contable ya registrado no deberia desaparecer del libro diario. Al
     * volver a calcular y cerrar la liquidacion (fuera de este metodo, con el flujo normal), se
     * genera un asiento de pago nuevo por el monto correcto.
     */
    @Transactional
    public Liquidacion reabrir(Long id) {
        Liquidacion liquidacion = obtener(id);
        if (liquidacion.getEstado() != EstadoLiquidacion.CLOSED) {
            throw new BusinessRuleException(
                    "Solo se puede reabrir una liquidación que ya esté pagada (CLOSED). Esta está en "
                            + liquidacion.getEstado() + ".");
        }
        registrarAsientoReversaDePago(liquidacion);
        liquidacion.setEstado(EstadoLiquidacion.CALCULATED);
        Liquidacion guardada = liquidacionRepository.save(liquidacion);
        auditoriaService.registrar("Reapertura de liquidación", "Liquidación", describir(guardada));
        return guardada;
    }

    /** Reverso del asiento de {@link #registrarAsientoDePago}: mismo monto, debe/haber invertidos. */
    private void registrarAsientoReversaDePago(Liquidacion liquidacion) {
        if (liquidacion.getMontoNeto() == null || liquidacion.getMontoNeto() <= 0) {
            return;
        }
        BigDecimal monto = BigDecimal.valueOf(liquidacion.getMontoNeto()).setScale(2, java.math.RoundingMode.HALF_UP);
        asientoContableService.registrar(
                LocalDate.now(ZONA_HUATA),
                "Anulación: Pago de liquidación, " + liquidacion.getProductor().getCodigo(),
                "Por la reapertura de la liquidación del ciclo " + liquidacion.getPeriodoInicio() + " a "
                        + liquidacion.getPeriodoFin() + " de " + liquidacion.getProductor().getCodigo() + ".",
                OrigenAsiento.LIQUIDACION_REAPERTURA,
                liquidacion.getId(),
                List.of(LineaContable.debe("1011", monto), LineaContable.haber("6021", monto)));
    }

    /**
     * El estado no se asigna, se transita. Permitir cualquier salto convierte el enum en una
     * etiqueta de texto: bastaria un PATCH para reabrir un pago cerrado (la unica reapertura valida
     * es {@link #reabrir}, con su propio permiso y codigo de verificacion).
     */
    private static void exigirTransicionValida(EstadoLiquidacion desde, EstadoLiquidacion hasta) {
        if (hasta == null) {
            throw new BusinessRuleException("Falta el estado de destino");
        }
        if (desde == hasta) {
            throw new BusinessRuleException("La liquidación ya está en " + hasta);
        }
        if (!desde.permiteIr(hasta)) {
            String posibles = desde.destinosPosibles().isEmpty()
                    ? (desde == EstadoLiquidacion.CLOSED
                            ? "ninguno: una liquidación cerrada es un pago ya entregado y no se reabre"
                            : "ninguno")
                    : desde.destinosPosibles().toString();
            throw new BusinessRuleException("Transición no permitida: " + desde + " -> " + hasta + ". Desde " + desde
                    + " solo se puede ir a " + posibles + ".");
        }
    }

    /** Proveedor y periodo: lo justo para identificar la liquidacion en la bitacora. */
    private static String describir(Liquidacion liquidacion) {
        return liquidacion.getProductor().getCodigo() + " · " + liquidacion.getPeriodoInicio() + " a "
                + liquidacion.getPeriodoFin();
    }



    // ---- Cierre masivo del ciclo ("Pagar a todos") ----

    /**
     * Liquida y cierra de una vez a todos los proveedores con entregas en el periodo.
     *
     * <p>Es una sola transaccion a proposito: si falla el proveedor numero doce, no puede quedar
     * media planilla pagada y media sin pagar. Hacerlo desde el navegador con N llamadas daria
     * justamente ese resultado a medias.
     *
     * <p>A los que ya tienen una liquidacion CLOSED se les salta en vez de recalcular: recalcular un
     * periodo cerrado cambiaria un importe ya entregado al proveedor.
     */
    @Transactional
    public CierreCicloResultado cerrarCiclo(LocalDate periodoInicio, LocalDate periodoFin) {
        if (periodoFin.isBefore(periodoInicio)) {
            throw new BusinessRuleException("El fin del periodo no puede ser anterior a su inicio.");
        }

        // Un LinkedHashSet conserva el orden de aparicion, de modo que el resultado es estable entre
        // ejecuciones y no depende del orden en que la base devuelva las filas.
        Set<Long> productoresConEntregas = new LinkedHashSet<>();
        for (Recojo recojo : recojoRepository.findByFechaHoraBetweenAndEstadoNot(
                periodoInicio.atStartOfDay(ZONA_HUATA).toInstant(),
                periodoFin.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant(),
                EstadoRecojo.CANCELLED)) {
            productoresConEntregas.add(recojo.getProductor().getId());
        }

        List<Liquidacion> liquidadas = new java.util.ArrayList<>();
        int yaCerrados = 0;
        double litros = 0.0;
        double neto = 0.0;

        for (Long productorId : productoresConEntregas) {
            boolean cerrada = liquidacionRepository
                    .findByProductorIdAndPeriodoInicioAndPeriodoFinAndActivoTrue(productorId, periodoInicio, periodoFin)
                    .map(l -> l.getEstado() == EstadoLiquidacion.CLOSED)
                    .orElse(false);
            if (cerrada) {
                yaCerrados++;
                continue;
            }

            Liquidacion liquidacion = calcular(productorId, periodoInicio, periodoFin);
            // Pasa por la misma comprobacion que el cierre individual. Es CALCULATED recien salida
            // de calcular(), asi que nunca deberia fallar; si algun dia falla es que el calculo dejo
            // la liquidacion en un estado que no cierra, y eso hay que verlo, no saltarselo.
            exigirTransicionValida(liquidacion.getEstado(), EstadoLiquidacion.CLOSED);
            liquidacion.setEstado(EstadoLiquidacion.CLOSED);
            Liquidacion guardada = liquidacionRepository.save(liquidacion);
            registrarAsientoDePago(guardada);

            liquidadas.add(guardada);
            litros += guardada.getLitrosTotales();
            neto += guardada.getMontoNeto();
        }

        auditoriaService.registrar(
                "Pago global",
                "Liquidación",
                liquidadas.size() + " proveedor(es) · periodo " + periodoInicio + " a " + periodoFin
                        + " · neto S/ " + String.format(java.util.Locale.US, "%.2f", neto));

        return new CierreCicloResultado(liquidadas, yaCerrados, litros, neto);
    }

    /** Resultado interno del cierre masivo; el controlador lo traduce a su DTO. */
    public record CierreCicloResultado(
            List<Liquidacion> liquidadas, int yaCerrados, double litrosTotales, double montoNetoTotal) {}

    // ---- Ajustes (regla 7.7) ----

    public List<AjusteLiquidacion> listarAjustes(Long liquidacionId) {
        return ajusteLiquidacionRepository.findByLiquidacionIdOrderByIdAsc(liquidacionId);
    }

    @Transactional
    public Liquidacion agregarAjuste(Long liquidacionId, AjusteLiquidacionRequest req) {
        Liquidacion liquidacion = obtener(liquidacionId);
        exigirLiquidacionAbierta(liquidacion);

        AjusteLiquidacion ajuste = new AjusteLiquidacion();
        ajuste.setLiquidacion(liquidacion);
        ajuste.setTipo(req.tipo());
        // Se normaliza a positivo: el signo lo aporta el tipo, nunca el dato.
        ajuste.setMonto(Math.abs(req.monto()));
        ajuste.setMotivo(req.motivo());
        ajusteLiquidacionRepository.save(ajuste);

        aplicarAjustes(liquidacion);
        Liquidacion resultado = liquidacionRepository.save(liquidacion);
        auditoriaService.registrar(
                "Ajuste",
                "Liquidación",
                liquidacion.getProductor().getCodigo() + " · " + req.tipo() + " S/ " + ajuste.getMonto()
                        + (req.motivo() == null ? "" : " · " + req.motivo()));
        return resultado;
    }

    @Transactional
    public Liquidacion eliminarAjuste(Long liquidacionId, Long ajusteId) {
        Liquidacion liquidacion = obtener(liquidacionId);
        exigirLiquidacionAbierta(liquidacion);

        AjusteLiquidacion ajuste = ajusteLiquidacionRepository
                .findById(ajusteId)
                .filter(a -> a.getLiquidacion().getId().equals(liquidacionId))
                .orElseThrow(() -> NotFoundException.of("Ajuste de liquidación", ajusteId));
        ajusteLiquidacionRepository.delete(ajuste);

        aplicarAjustes(liquidacion);
        Liquidacion resultado = liquidacionRepository.save(liquidacion);
        auditoriaService.registrar(
                "Baja de ajuste",
                "Liquidación",
                liquidacion.getProductor().getCodigo() + " · " + ajuste.getTipo() + " S/ " + ajuste.getMonto());
        return resultado;
    }

    /**
     * Recalcula {@code ajustesTotal} y {@code montoNeto} desde los ajustes guardados. No persiste:
     * quien llama decide cuando guardar, para no escribir dos veces en la misma transaccion.
     */
    private void aplicarAjustes(Liquidacion liquidacion) {
        double total = liquidacion.getId() == null
                ? 0.0
                : ajusteLiquidacionRepository.findByLiquidacionIdOrderByIdAsc(liquidacion.getId()).stream()
                        .mapToDouble(AjusteLiquidacion::montoConSigno)
                        .sum();
        liquidacion.setAjustesTotal(total);
        liquidacion.setMontoNeto(liquidacion.getMontoTotal() + total);
    }

    /**
     * Una liquidación cerrada ya se pagó: cambiarle los ajustes alteraría un importe entregado al
     * proveedor sin dejar rastro de que el papel firmado decía otra cosa.
     */
    private void exigirLiquidacionAbierta(Liquidacion liquidacion) {
        if (!liquidacion.getEstado().admiteCambios()) {
            // El mensaje anterior decia "Reabrela antes de modificarla" y prometia una reapertura
            // que no existe: CLOSED es terminal a proposito (ver EstadoLiquidacion).
            throw new BusinessRuleException("La liquidación está cerrada: no admite ajustes. Es un pago ya "
                    + "entregado al proveedor; la corrección va como ajuste del ciclo siguiente.");
        }
    }

    // ---- Tarifas ----

    public List<TarifaLeche> listarTarifas() {
        return tarifaLecheRepository.findAll();
    }

    @Transactional
    public TarifaLeche guardarTarifa(TarifaLecheRequest req) {
        TarifaLeche tarifa = new TarifaLeche();
        tarifa.setPrecioNormal(req.precioNormal());
        tarifa.setPrecioObservada(req.precioObservada());
        tarifa.setPrecioExcelencia(req.precioExcelencia());
        tarifa.setVigenteDesde(req.vigenteDesde());
        tarifa.setVigenteHasta(req.vigenteHasta());
        TarifaLeche guardada = tarifaLecheRepository.save(tarifa);
        // Faltaba: el precio por litro decide lo que cobra cada proveedor de la cooperativa, y se
        // podia cambiar sin dejar autor. La tarifa de queso ya se auditaba; esto lo iguala.
        auditoriaService.registrar(
                "Precios de leche",
                "Configuración",
                "Vigente desde " + guardada.getVigenteDesde() + " · normal S/ " + guardada.getPrecioNormal()
                        + " · observada S/ " + guardada.getPrecioObservada() + " · excelencia S/ "
                        + guardada.getPrecioExcelencia());
        return guardada;
    }
}
