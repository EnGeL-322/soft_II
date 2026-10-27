package org.ramiro.huata.quesos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.produccion.CalculoProduccion;
import org.ramiro.huata.produccion.LoteConsumo;
import org.ramiro.huata.produccion.LoteConsumoRepository;
import org.ramiro.huata.produccion.LoteManoObra;
import org.ramiro.huata.produccion.LoteManoObraRepository;
import org.ramiro.huata.produccion.Producto;
import org.ramiro.huata.produccion.ProductoService;
import org.ramiro.huata.produccion.RecetaQueso;
import org.ramiro.huata.produccion.RecetaQuesoRepository;
import org.ramiro.huata.produccion.TrabajadorProduccion;
import org.ramiro.huata.produccion.TrabajadorProduccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produccion y venta de queso por lote: las pantallas "Registrar produccion" y "Registrar venta" de
 * Secretaria (vistas-por-roles.html, NAVS.secretaria).
 *
 * <p><b>La regla que justifica las dos pantallas</b> es que una venta sale de UN lote y no puede
 * llevarse mas unidades de las que quedan en camara. Vive aqui y solo aqui: la pantalla muestra el
 * maximo para que la persona no se equivoque, pero quien lo impide es el servidor.
 *
 * <p><b>El stock se calcula, no se guarda</b> (ver {@link InventarioService}). Desde V13 la fuente es
 * el kardex unificado ({@code movimientos_inventario}), no ya solo las ventas activas del lote: crear
 * un lote registra su entrada de produccion terminada alli, y una venta o su anulacion registran la
 * salida o la reversion. Antes de V13 el stock era {@code unidades_producidas - ventas activas}; el
 * backfill de la migracion reconstruye ese mismo numero como movimientos, asi que el calculo no
 * cambia para ningun lote ya existente.
 *
 * <p><b>Crear un lote consume materia prima segun la receta</b> (receta x lo que se puso a producir,
 * ver {@link CalculoProduccion}) y registra mano de obra, y calcula el costo total y unitario del lote
 * (RF-61 a RF-67). La leche es un ingrediente mas: la del acopio entra al inventario al recibirse en
 * planta (V31) y sale de ahi por la receta. Es una sola transaccion: si algo falla a mitad de camino
 * (stock insuficiente de una materia prima, por ejemplo), no queda ni el lote ni ningun movimiento a
 * medias.
 *
 * <p>Lo que todavia no hace. La venta no genera movimiento de caja —el modulo de Caja no existe
 * aun— asi que guarda condicion, medio de cobro y estado, y cuando Caja exista el asiento se engancha
 * a estas ventas sin rehacerlas.
 */
@Service
@RequiredArgsConstructor
public class QueseriaService {

    private final LoteQuesoRepository loteRepository;
    private final VentaQuesoRepository ventaRepository;
    private final TarifaQuesoRepository tarifaRepository;
    private final RecetaQuesoRepository recetaRepository;
    private final TrabajadorProduccionRepository trabajadorRepository;
    private final LoteConsumoRepository loteConsumoRepository;
    private final LoteManoObraRepository loteManoObraRepository;
    private final InventarioService inventarioService;
    private final ProductoService productoService;
    private final AuditoriaService auditoriaService;
    private final AsientoContableService asientoContableService;
    private final ParametroNegocioRepository parametroNegocioRepository;

    private static final String ENTIDAD = "Quesos";

    /** Si IGV_PORCENTAJE no esta configurado, 18% (tasa vigente en Peru) es un valor de ley, no un dato de negocio inventado. */
    private static final BigDecimal IGV_PORCENTAJE_DEFECTO = BigDecimal.valueOf(18);

    /** Un lote con su stock (kardex). El controlador lo traduce a su DTO. */
    public record LoteConStock(LoteQueso lote, int stock) {
        public int unidadesVendidas() {
            return lote.getUnidadesProducidas() - stock;
        }
    }

    // ---- Produccion ----

    /**
     * Lotes del periodo con su stock, en una sola consulta agregada.
     *
     * <p>Preguntar el saldo lote por lote seria una consulta por fila de la tabla que se esta
     * pintando: con 40 lotes, 41 viajes a la base.
     */
    public List<LoteConStock> listarLotes(LocalDate desde, LocalDate hasta) {
        List<LoteQueso> lotes = (desde == null && hasta == null)
                ? loteRepository.findByActivoTrueOrderByFechaDescIdDesc()
                : loteRepository.findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(
                        desde != null ? desde : LocalDate.now().minusMonths(1),
                        hasta != null ? hasta : LocalDate.now());
        return conStock(lotes);
    }

    /** Lotes que todavia tienen unidades en camara: los unicos que la pantalla de venta ofrece. */
    public List<LoteConStock> lotesConStock() {
        return conStock(loteRepository.findByActivoTrueOrderByFechaDescIdDesc()).stream()
                .filter(l -> l.stock() > 0)
                .toList();
    }

    /**
     * Lo que se puede vender hoy: con stock y sin vencer, ordenado para que salga primero lo que
     * vence antes (FEFO); lo que no tiene vencimiento va al final, del mas viejo al mas nuevo.
     */
    public List<LoteConStock> lotesParaVender(LocalDate hoy) {
        return lotesConStock().stream()
                .filter(l -> !l.lote().vencidoEl(hoy))
                .sorted(Comparator.comparing(
                                (LoteConStock l) -> l.lote().getFechaVencimiento(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(l -> l.lote().getFecha()))
                .toList();
    }

    private List<LoteConStock> conStock(List<LoteQueso> lotes) {
        if (lotes.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> saldos =
                inventarioService.saldosLotesQueso(lotes.stream().map(LoteQueso::getId).toList());
        return lotes.stream()
                .map(lote -> new LoteConStock(lote, saldos.getOrDefault(lote.getId(), BigDecimal.ZERO).intValue()))
                .toList();
    }

    /**
     * Crea un lote de produccion: consume la materia prima realmente registrada, contabiliza la mano
     * de obra, calcula el costo total y unitario, y da entrada al producto terminado en el kardex.
     * Todo en una sola transaccion.
     */
    @Transactional
    public LoteQueso crearLote(LoteQuesoRequest req) {
        exigirFechaNoFutura(req.fecha(), "El lote");

        Producto producto = productoService.activo(req.productoId());
        RecetaQueso receta = recetaRepository
                .findById(req.recetaId())
                .filter(RecetaQueso::isActivo)
                .orElseThrow(() -> NotFoundException.of("Receta", req.recetaId()));
        // La receta decide las materias primas que se van a descontar: la de otro producto
        // consumiria insumos que este lote no usa.
        if (!receta.getProducto().getId().equals(producto.getId())) {
            throw new BusinessRuleException("La receta seleccionada es de " + receta.getProducto().getNombre()
                    + ", no de " + producto.getNombre() + ".");
        }

        // Lo que se descuenta sale de la receta, no del formulario (decision del cliente, 2026-09-30):
        // receta x lo que se puso a producir. La misma cuenta que muestra la pantalla antes de guardar.
        List<CalculoProduccion.Consumo> consumos = CalculoProduccion.consumoPara(receta, req.cantidadAConsumir());

        LoteQueso lote = new LoteQueso();
        lote.setFecha(req.fecha());
        lote.setProducto(producto);
        lote.setOrigenLeche(req.origenLeche());
        lote.setLitrosDestinados(litrosDelLote(consumos, req, producto));
        lote.setUnidadesProducidas(req.unidadesProducidas());
        lote.setResponsable(req.responsable().trim());
        lote.setObservaciones(req.observaciones());
        lote.setReceta(receta);
        lote.setCantidadObjetivo(req.cantidadObjetivo());
        lote.setQuesosReparados(req.quesosReparados());
        lote.setQuesosRecorte(req.quesosRecorte());
        lote.setRecorteKg(req.recorteKg());
        lote.setFechaVencimiento(vencimientoDe(req, producto));
        // El codigo se pone DESPUES de guardar, a partir del id (ver justificacion mas abajo).
        lote.setCodigo("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 12));
        LoteQueso guardado = loteRepository.save(lote);

        BigDecimal costoMateriaPrima = consumirMateriaPrima(guardado, consumos, req);
        BigDecimal costoManoObra = registrarManoObra(guardado, req);

        BigDecimal costoTotal = costoMateriaPrima.add(costoManoObra);
        BigDecimal costoUnitario = costoTotal.divide(
                BigDecimal.valueOf(guardado.getUnidadesProducidas()), 2, RoundingMode.HALF_UP);

        guardado.setCostoMateriaPrima(costoMateriaPrima.setScale(2, RoundingMode.HALF_UP));
        guardado.setCostoManoObra(costoManoObra.setScale(2, RoundingMode.HALF_UP));
        guardado.setCostoTotal(costoTotal.setScale(2, RoundingMode.HALF_UP));
        guardado.setCostoUnitario(costoUnitario);
        guardado.setCodigo(codigoDe(guardado));
        guardado = loteRepository.save(guardado);

        // Entrada de producto terminado al kardex: es lo que hace que el lote tenga stock disponible
        // para vender (ver InventarioService, decision de unificar el stock de queso en el kardex).
        inventarioService.registrarEntradaProduccionTerminado(
                guardado, BigDecimal.valueOf(guardado.getUnidadesProducidas()), req.fecha());

        registrarAsientosDeProduccion(guardado);

        auditoriaService.registrar(
                "Producción de quesos",
                ENTIDAD,
                guardado.getCodigo() + " · " + guardado.getProducto().getNombre() + " · " + guardado.getUnidadesProducidas()
                        + " u de " + guardado.getLitrosDestinados() + " L · costo unitario S/ " + guardado.getCostoUnitario());
        return guardado;
    }

    /**
     * Bloquea cada materia prima de la receta (en orden ascendente de id, para no producir un deadlock
     * con otro lote que consume las mismas materias primas en orden distinto), verifica que el stock
     * alcance, y registra el consumo y su salida en el kardex. Si una no alcanza, la excepcion revierte
     * toda la transaccion: las que ya se habian descontado vuelven a su saldo.
     */
    private BigDecimal consumirMateriaPrima(
            LoteQueso lote, List<CalculoProduccion.Consumo> consumos, LoteQuesoRequest req) {
        BigDecimal costoMateriaPrima = BigDecimal.ZERO;
        for (CalculoProduccion.Consumo c : consumos) {
            MateriaPrima materiaPrima = inventarioService.bloquearMateriaPrima(c.materiaPrima().getId());
            BigDecimal disponible = inventarioService.saldoMateriaPrima(materiaPrima.getId());
            if (disponible.compareTo(c.cantidad()) < 0) {
                String unidad = materiaPrima.getUnidadMedida().name().toLowerCase(java.util.Locale.ROOT);
                throw new BusinessRuleException("No hay stock suficiente de " + materiaPrima.getNombre() + ": hay "
                        + legible(disponible) + " " + unidad + " y la receta pide " + legible(c.cantidad()) + " " + unidad
                        + " para " + req.cantidadAConsumir() + " unidades. Registra una compra o produce menos.");
            }

            BigDecimal costoUnitarioMomento =
                    materiaPrima.getCostoUnitarioReferencial() != null ? materiaPrima.getCostoUnitarioReferencial() : BigDecimal.ZERO;
            LoteConsumo consumo = new LoteConsumo();
            consumo.setLote(lote);
            consumo.setMateriaPrima(materiaPrima);
            consumo.setCantidadConsumida(c.cantidad());
            consumo.setCostoUnitarioMomento(costoUnitarioMomento);
            loteConsumoRepository.save(consumo);

            inventarioService.registrarSalidaProduccionConsumo(materiaPrima, c.cantidad(), req.fecha(), lote.getId());
            costoMateriaPrima = costoMateriaPrima.add(consumo.costoTotal());
        }
        return costoMateriaPrima;
    }

    /**
     * Los litros del lote son la leche del acopio que se descuenta segun la receta. Solo si la receta
     * no lleva esa leche (datos anteriores a la regla) se acepta lo que diga el formulario; sin ninguno
     * de los dos no se puede saber cuanta leche se uso, y se pide completar la receta.
     */
    private static Double litrosDelLote(List<CalculoProduccion.Consumo> consumos, LoteQuesoRequest req, Producto producto) {
        BigDecimal leche = consumos.stream()
                .filter(c -> c.materiaPrima().isLecheAcopio())
                .map(CalculoProduccion.Consumo::cantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (leche.signum() > 0) {
            return leche.doubleValue();
        }
        if (req.litrosDestinados() != null) {
            return req.litrosDestinados();
        }
        throw new BusinessRuleException("La receta de " + producto.getNombre()
                + " no dice cuánta leche lleva. Agrégale la leche en Recetas y productos.");
    }

    /** "12.5" y no "12.5000": el kardex guarda 4 decimales pero la persona lee cantidades. */
    private static String legible(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }

    private BigDecimal registrarManoObra(LoteQueso lote, LoteQuesoRequest req) {
        if (req.manoObra() == null || req.manoObra().isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal costoManoObra = BigDecimal.ZERO;
        for (ManoObraItemRequest mo : req.manoObra()) {
            TrabajadorProduccion trabajador = trabajadorRepository
                    .findById(mo.trabajadorId())
                    .filter(TrabajadorProduccion::isActivo)
                    .orElseThrow(() -> NotFoundException.of("Trabajador de produccion", mo.trabajadorId()));
            LoteManoObra lmo = new LoteManoObra();
            lmo.setLote(lote);
            lmo.setTrabajador(trabajador);
            lmo.setMonto(mo.monto());
            loteManoObraRepository.save(lmo);
            costoManoObra = costoManoObra.add(mo.monto());
        }
        return costoManoObra;
    }

    /**
     * Lo que diga quien produce; si no dijo nada, la fecha del lote mas los dias que dura el
     * producto. Todo lote tiene vencimiento (pedido del cliente, 2026-10-27): sin los dias del
     * producto ni una fecha escrita no se produce, porque la venta saca primero lo que vence antes.
     */
    private static LocalDate vencimientoDe(LoteQuesoRequest req, Producto producto) {
        if (req.fechaVencimiento() != null) {
            if (req.fechaVencimiento().isBefore(req.fecha())) {
                throw new BusinessRuleException("La fecha de vencimiento no puede ser anterior a la de producción.");
            }
            return req.fechaVencimiento();
        }
        if (producto.getVidaUtilDias() == null) {
            throw new BusinessRuleException("Falta configurar cuántos días dura " + producto.getNombre()
                    + ". Configúralo en Configuración → Precios y calidad → Vencimiento de productos.");
        }
        return req.fecha().plusDays(producto.getVidaUtilDias());
    }

    private static String codigoDe(LoteQueso lote) {
        return "L" + lote.getFecha().getYear() + "-" + String.format("%04d", lote.getId());
    }

    /**
     * Tres asientos por lote, igual que el motor contable del prototipo (queseria.html): consumo de
     * materia prima, mano de obra, y traslado a producto terminado. Cada uno se omite si su costo es
     * cero (por ejemplo, un lote sin mano de obra registrada): un asiento sin monto no es un asiento.
     */
    private void registrarAsientosDeProduccion(LoteQueso lote) {
        String glosaLote = "lote " + lote.getCodigo();
        if (lote.getCostoMateriaPrima() != null && lote.getCostoMateriaPrima().signum() > 0) {
            asientoContableService.registrar(
                    lote.getFecha(),
                    "Consumo de materia prima, " + glosaLote,
                    "Por el consumo de la leche y los insumos en la elaboracion del " + glosaLote + ".",
                    OrigenAsiento.PRODUCCION_CONSUMO,
                    lote.getId(),
                    List.of(
                            LineaContable.debe("2311", lote.getCostoMateriaPrima()),
                            LineaContable.haber("2411", lote.getCostoMateriaPrima())));
        }
        if (lote.getCostoManoObra() != null && lote.getCostoManoObra().signum() > 0) {
            asientoContableService.registrar(
                    lote.getFecha(),
                    "Mano de obra, " + glosaLote,
                    "Por la mano de obra (pendiente de pago) del " + glosaLote + ".",
                    OrigenAsiento.PRODUCCION_MANO_OBRA,
                    lote.getId(),
                    List.of(
                            LineaContable.debe("2311", lote.getCostoManoObra()),
                            LineaContable.haber("4111", lote.getCostoManoObra())));
        }
        if (lote.getCostoTotal() != null && lote.getCostoTotal().signum() > 0) {
            asientoContableService.registrar(
                    lote.getFecha(),
                    "Termina " + glosaLote + ": " + lote.getUnidadesProducidas() + " " + lote.getProducto().getNombre(),
                    "Por el traslado al almacen de " + lote.getUnidadesProducidas() + " " + lote.getProducto().getNombre()
                            + " terminados del "
                            + glosaLote + ", a S/ " + lote.getCostoUnitario() + " cada uno.",
                    OrigenAsiento.PRODUCCION_TERMINADO,
                    lote.getId(),
                    List.of(
                            LineaContable.debe("2111", lote.getCostoTotal()),
                            LineaContable.haber("2311", lote.getCostoTotal())));
        }
    }

    /**
     * Anula un lote registrado por error (V59): devuelve al almacen lo que la receta descontó, saca
     * de camara el producto terminado y revierte los tres asientos de produccion. Solo si no se
     * vendio nada del lote: con ventas activas, el producto ya salio y lo que corresponde es anular
     * primero esas ventas. No se borra nada; el lote queda inactivo con su motivo.
     */
    @Transactional
    public LoteQueso anularLote(Long id, String motivoRecibido) {
        String motivo = motivoRecibido == null ? "" : motivoRecibido.trim();
        if (motivo.length() < 3) {
            throw new BusinessRuleException("La anulación de un lote exige un motivo.");
        }
        // Bloqueo de fila: una venta simultanea no puede llevarse unidades mientras se anula.
        LoteQueso lote = loteRepository
                .findParaVenderById(id)
                .orElseThrow(() -> NotFoundException.of("Lote de queso", id));
        if (!lote.isActivo()) {
            throw new BusinessRuleException("El lote " + lote.getCodigo() + " ya está anulado.");
        }
        BigDecimal enCamara = inventarioService.saldoLoteQueso(lote.getId());
        BigDecimal producidas = BigDecimal.valueOf(lote.getUnidadesProducidas());
        if (enCamara.compareTo(producidas) != 0) {
            int vendidas = producidas.subtract(enCamara).intValue();
            throw new BusinessRuleException("Del lote " + lote.getCodigo() + " ya salieron " + vendidas
                    + " unidad(es). Anula primero esas ventas para poder anular el lote.");
        }

        LocalDate hoy = LocalDate.now();
        String nota = "Anulación del lote " + lote.getCodigo() + ": " + motivo;
        inventarioService.registrarAjusteSalidaLote(lote, producidas, hoy, nota);
        for (LoteConsumo consumo : loteConsumoRepository.findByLote_IdOrderById(lote.getId())) {
            inventarioService.registrarAjusteEntrada(
                    consumo.getMateriaPrima(),
                    consumo.getCantidadConsumida(),
                    hoy,
                    org.ramiro.huata.inventario.DocumentoOrigenTipo.PRODUCCION,
                    lote.getId(),
                    nota);
        }

        lote.setActivo(false);
        lote.setMotivoAnulacion(motivo);
        LoteQueso guardado = loteRepository.save(lote);
        revertirAsientosDeProduccion(guardado, hoy);

        auditoriaService.registrar(
                "Anulación de producción",
                ENTIDAD,
                guardado.getCodigo() + " · " + guardado.getProducto().getNombre() + " · "
                        + guardado.getUnidadesProducidas() + " u · " + motivo);
        return guardado;
    }

    /** Los tres asientos de {@link #registrarAsientosDeProduccion} con debe y haber intercambiados. */
    private void revertirAsientosDeProduccion(LoteQueso lote, LocalDate fecha) {
        String glosaLote = "lote " + lote.getCodigo();
        if (lote.getCostoTotal() != null && lote.getCostoTotal().signum() > 0) {
            asientoContableService.registrar(
                    fecha,
                    "Anulación: termina " + glosaLote,
                    "Por la reversión del traslado al almacén del " + glosaLote + " anulado.",
                    OrigenAsiento.ANULACION_PRODUCCION,
                    lote.getId(),
                    List.of(LineaContable.debe("2311", lote.getCostoTotal()), LineaContable.haber("2111", lote.getCostoTotal())));
        }
        if (lote.getCostoManoObra() != null && lote.getCostoManoObra().signum() > 0) {
            asientoContableService.registrar(
                    fecha,
                    "Anulación: mano de obra, " + glosaLote,
                    "Por la reversión de la mano de obra del " + glosaLote + " anulado.",
                    OrigenAsiento.ANULACION_PRODUCCION,
                    lote.getId(),
                    List.of(LineaContable.debe("4111", lote.getCostoManoObra()), LineaContable.haber("2311", lote.getCostoManoObra())));
        }
        if (lote.getCostoMateriaPrima() != null && lote.getCostoMateriaPrima().signum() > 0) {
            asientoContableService.registrar(
                    fecha,
                    "Anulación: consumo de materia prima, " + glosaLote,
                    "Por la devolución al almacén de la leche y los insumos del " + glosaLote + " anulado.",
                    OrigenAsiento.ANULACION_PRODUCCION,
                    lote.getId(),
                    List.of(
                            LineaContable.debe("2411", lote.getCostoMateriaPrima()),
                            LineaContable.haber("2311", lote.getCostoMateriaPrima())));
        }
    }

    /** IGV_PORCENTAJE configurado en Parametros, o 18% (tasa de ley) si no se configuro ninguno. */
    private BigDecimal igvPorcentaje() {
        return parametroNegocioRepository
                .findByClave("IGV_PORCENTAJE")
                .map(p -> {
                    try {
                        return new BigDecimal(p.getValor().trim());
                    } catch (NumberFormatException e) {
                        return IGV_PORCENTAJE_DEFECTO;
                    }
                })
                .orElse(IGV_PORCENTAJE_DEFECTO);
    }

    // ---- Venta ----

    public List<VentaQueso> listarVentas(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusMonths(1);
        return ventaRepository.findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(inicio, fin);
    }

    /**
     * Registra una salida de producto y descuenta el stock del lote en el kardex.
     *
     * <p>El lote se lee con bloqueo de fila: sin el, dos ventas simultaneas de las ultimas unidades
     * leen el mismo stock, las dos lo encuentran suficiente y las dos se guardan. Comprobar antes de
     * escribir no sirve de nada si nadie impide que el otro compruebe a la vez.
     */
    @Transactional
    public VentaQueso registrarVenta(VentaQuesoRequest req) {
        LoteQueso lote = loteRepository
                .findParaVenderById(req.loteId())
                .filter(LoteQueso::isActivo)
                .orElseThrow(() -> NotFoundException.of("Lote de queso", req.loteId()));

        // La fecha ya no la manda el formulario (ver VentaQuesoRequest): siempre es la de hoy segun
        // el servidor. Por eso ya no hace falta exigirFechaNoFutura aqui -- LocalDate.now() nunca es
        // futura -- pero el helper sigue vivo porque lo sigue usando el registro de lotes.
        LocalDate fecha = LocalDate.now();
        if (lote.vencidoEl(fecha)) {
            throw new BusinessRuleException("El lote " + lote.getCodigo() + " de " + lote.getProducto().getNombre()
                    + " venció el " + lote.getFechaVencimiento() + ": no se puede vender.");
        }
        if (fecha.isBefore(lote.getFecha())) {
            throw new BusinessRuleException("La venta no puede ser anterior a la producción del lote " + lote.getCodigo()
                    + " (" + lote.getFecha() + "): ese queso todavía no existía.");
        }

        BigDecimal disponible = inventarioService.saldoLoteQueso(lote.getId());
        if (BigDecimal.valueOf(req.unidades()).compareTo(disponible) > 0) {
            // El queso se cuenta en unidades enteras aunque el kardex use 4 decimales (para poder
            // compartir columna con materia prima); "5.0000 unidad(es)" confundiria al usuario.
            throw new BusinessRuleException("El lote " + lote.getCodigo() + " tiene " + disponible.stripTrailingZeros().toPlainString()
                    + " unidad(es) en cámara y se intentan vender " + req.unidades() + ".");
        }

        VentaQueso venta = new VentaQueso();
        venta.setLote(lote);
        venta.setFecha(fecha);
        venta.setCliente(req.cliente().trim());
        venta.setUnidades(req.unidades());
        venta.setTipoComprador(req.tipoComprador());
        // El precio lo pone la tarifa, no el formulario. Se congela en la venta porque la tarifa
        // cambia y la nota de venta vieja tiene que reimprimirse con lo que se cobro entonces.
        venta.setPrecioUnitario(req.tipoComprador().precioEn(tarifaVigente(lote.getProducto(), fecha)));
        venta.setCondicion(req.condicion());
        venta.setMedioCobro(req.medioCobro());
        // Lo decide la condicion, no el formulario: ver CondicionVenta.
        venta.setEstado(req.condicion().estadoInicial());

        BigDecimal base = BigDecimal.valueOf(venta.total());
        BigDecimal igv = base.multiply(igvPorcentaje())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        venta.setMontoIgv(igv.doubleValue());

        VentaQueso guardada = ventaRepository.save(venta);

        inventarioService.registrarSalidaVenta(lote, BigDecimal.valueOf(req.unidades()), fecha, guardada.getId());
        registrarAsientosDeVenta(guardada, base, igv, OrigenAsiento.VENTA, "Venta de " + guardada.getUnidades() + " quesos");

        auditoriaService.registrar(
                "Venta de quesos",
                ENTIDAD,
                describir(guardada) + " · " + guardada.getTipoComprador() + " · " + guardada.getEstado());
        return guardada;
    }

    /**
     * Vende un producto sin elegir lote (pedido del cliente, 2026-10-27): sale primero lo que vence
     * antes (FEFO, el mismo orden de {@link #lotesParaVender}) y, si un lote no alcanza, se completa
     * con el siguiente. Cada lote tocado es una venta propia —asi cada una conserva su costo y su
     * trazabilidad— y todas van en una sola transaccion: o se registran todas o ninguna.
     */
    @Transactional
    public List<VentaQueso> venderPorProducto(VentaPorProductoRequest req) {
        LocalDate hoy = LocalDate.now();
        List<LoteConStock> lotes = lotesParaVender(hoy).stream()
                .filter(l -> l.lote().getProducto().getId().equals(req.productoId()))
                .toList();
        int disponible = lotes.stream().mapToInt(LoteConStock::stock).sum();
        if (req.unidades() > disponible) {
            String nombre = productoService.activoOHistorico(req.productoId()).getNombre();
            throw new BusinessRuleException("De " + nombre + " hay " + disponible
                    + " unidad(es) en cámara sin vencer y se intentan vender " + req.unidades() + ".");
        }
        List<VentaQueso> ventas = new java.util.ArrayList<>();
        int faltan = req.unidades();
        for (LoteConStock l : lotes) {
            if (faltan == 0) {
                break;
            }
            int toma = Math.min(faltan, l.stock());
            ventas.add(registrarVenta(new VentaQuesoRequest(
                    l.lote().getId(), req.cliente(), toma, req.tipoComprador(), req.condicion(), req.medioCobro())));
            faltan -= toma;
        }
        return ventas;
    }

    /**
     * Ingreso (base + IGV) y costo de venta, igual que el prototipo. {@code signo} es 1 para la venta
     * y -1 para revertirla al anular (debe/haber intercambiados, no una edicion del asiento original).
     */
    private void registrarAsientosDeVenta(VentaQueso venta, BigDecimal base, BigDecimal igv, OrigenAsiento origen, String glosaVenta) {
        String cuentaIngreso = venta.getCondicion() == CondicionVenta.CONTADO ? "1011" : "1212";
        BigDecimal totalCobrado = base.add(igv);
        boolean esAnulacion = origen == OrigenAsiento.ANULACION_VENTA;

        asientoContableService.registrar(
                esAnulacion ? LocalDate.now() : venta.getFecha(),
                (esAnulacion ? "Anulacion: " : "") + glosaVenta + " a S/ " + venta.getPrecioUnitario() + " + IGV",
                "Por " + (esAnulacion ? "la reversion de la venta" : "la venta") + " de " + venta.getUnidades()
                        + " quesos " + (venta.getCondicion() == CondicionVenta.CONTADO ? "al contado" : "al credito")
                        + ", mas IGV.",
                origen,
                venta.getId(),
                esAnulacion
                        ? List.of(
                                LineaContable.debe("7021", base),
                                LineaContable.debe("40111", igv),
                                LineaContable.haber(cuentaIngreso, totalCobrado))
                        : List.of(
                                LineaContable.debe(cuentaIngreso, totalCobrado),
                                LineaContable.haber("7021", base),
                                LineaContable.haber("40111", igv)));

        BigDecimal costoUnitario = venta.getLote().getCostoUnitario();
        if (costoUnitario != null) {
            BigDecimal costoVenta = costoUnitario.multiply(BigDecimal.valueOf(venta.getUnidades()));
            asientoContableService.registrar(
                    esAnulacion ? LocalDate.now() : venta.getFecha(),
                    (esAnulacion ? "Anulacion: costo de " : "Costo de ") + venta.getUnidades() + " quesos vendidos",
                    "Por " + (esAnulacion ? "la reversion del costo" : "el costo") + " de los " + venta.getUnidades()
                            + " quesos " + (esAnulacion ? "cuya venta se anulo" : "vendidos") + ", a costo promedio.",
                    esAnulacion ? OrigenAsiento.ANULACION_VENTA : OrigenAsiento.VENTA_COSTO,
                    venta.getId(),
                    esAnulacion
                            ? List.of(LineaContable.debe("2111", costoVenta), LineaContable.haber("6921", costoVenta))
                            : List.of(LineaContable.debe("6921", costoVenta), LineaContable.haber("2111", costoVenta)));
        }
    }

    /**
     * Marca cobrada una venta a credito. Es la unica transicion de cobro que existe: el camino
     * contrario —"descobrar" una venta— seria borrar el rastro de un dinero que entro.
     */
    @Transactional
    public VentaQueso marcarCobrada(Long id) {
        VentaQueso venta = ventaActiva(id);
        if (venta.getEstado() == EstadoCobro.COBRADO) {
            throw new BusinessRuleException("La venta ya estaba cobrada.");
        }
        venta.setEstado(EstadoCobro.COBRADO);
        VentaQueso guardada = ventaRepository.save(venta);
        auditoriaService.registrar("Cobro de venta de quesos", ENTIDAD, describir(guardada));
        return guardada;
    }

    /**
     * Anula una venta y devuelve sus unidades al kardex. No se borra la fila ni el movimiento
     * original de salida: hubo una salida de producto y el motivo por el que no cuenta es justamente
     * lo que habra que explicar despues. La reversion es un movimiento nuevo
     * ({@code ENTRADA_ANULACION_VENTA}), no una edicion del {@code SALIDA_VENTA} ya escrito.
     */
    @Transactional
    public VentaQueso anularVenta(Long id, AnularVentaRequest req) {
        VentaQueso venta = ventaActiva(id);
        String motivo = req.motivo().trim();
        if (motivo.isEmpty()) {
            throw new BusinessRuleException("La anulación de una venta exige un motivo");
        }
        venta.setActivo(false);
        venta.setMotivoAnulacion(motivo);
        VentaQueso guardada = ventaRepository.save(venta);

        inventarioService.registrarEntradaAnulacionVenta(
                guardada.getLote(), BigDecimal.valueOf(guardada.getUnidades()), LocalDate.now(), guardada.getId(), motivo);

        // El IGV es nulo en ventas anteriores a este modulo contable (V21): no se recalcula
        // retroactivamente con la tasa de hoy, se reversa como cero.
        BigDecimal base = BigDecimal.valueOf(guardada.total());
        BigDecimal igv = guardada.getMontoIgv() != null ? BigDecimal.valueOf(guardada.getMontoIgv()) : BigDecimal.ZERO;
        registrarAsientosDeVenta(
                guardada, base, igv, OrigenAsiento.ANULACION_VENTA, "Venta de " + guardada.getUnidades() + " quesos");

        auditoriaService.registrar(
                "Anulación de venta de quesos", ENTIDAD, describir(guardada) + " · " + motivo);
        return guardada;
    }

    private VentaQueso ventaActiva(Long id) {
        VentaQueso venta = ventaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Venta de queso", id));
        if (!venta.isActivo()) {
            throw new BusinessRuleException("La venta está anulada: no admite cambios.");
        }
        return venta;
    }

    // ---- Historial por cliente ----

    /**
     * Un cliente y lo que compro en el periodo. El cliente es texto libre en la venta: se agrupa por
     * el nombre sin mayusculas ni espacios de sobra ("Bodega Rosa" y "bodega rosa " son el mismo), y
     * se muestra como se escribio la ultima vez.
     */
    public record ClienteResumen(
            String cliente,
            int compras,
            int unidades,
            double total,
            double porCobrar,
            LocalDate ultimaCompra,
            List<String> productos,
            TipoComprador ultimoTipoComprador) {}

    /** Sin fechas: los ultimos 12 meses. Solo ventas no anuladas. */
    public List<ClienteResumen> clientes(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusMonths(12);
        return ventaRepository.findByActivoTrueAndFechaBetweenOrderByFechaDescIdDesc(inicio, fin).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        v -> normalizar(v.getCliente()), java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values().stream()
                .map(ventas -> {
                    VentaQueso ultima = ventas.get(0); // vienen de la mas nueva a la mas vieja
                    return new ClienteResumen(
                            ultima.getCliente().trim(),
                            ventas.size(),
                            ventas.stream().mapToInt(VentaQueso::getUnidades).sum(),
                            redondear(ventas.stream().mapToDouble(VentaQueso::total).sum()),
                            redondear(ventas.stream()
                                    .filter(v -> v.getEstado() == EstadoCobro.POR_COBRAR)
                                    .mapToDouble(VentaQueso::total)
                                    .sum()),
                            ultima.getFecha(),
                            ventas.stream().map(v -> v.getLote().getProducto().getNombre()).distinct().sorted().toList(),
                            ultima.getTipoComprador());
                })
                .sorted(Comparator.comparing(ClienteResumen::total).reversed())
                .toList();
    }

    /** Todas las compras de un cliente (tambien las anuladas, que la respuesta marca como tales). */
    public List<VentaQueso> historialCliente(String cliente, Long productoId, LocalDate desde, LocalDate hasta) {
        if (cliente == null || cliente.isBlank()) {
            throw new BusinessRuleException("Indica el cliente.");
        }
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusYears(5);
        return ventaRepository.historialDe(normalizar(cliente), inicio, fin).stream()
                .filter(v -> productoId == null || v.getLote().getProducto().getId().equals(productoId))
                .toList();
    }

    private static String normalizar(String cliente) {
        return cliente == null ? "" : cliente.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static double redondear(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Los cuatro KPI de la pantalla de venta, con el mismo stock que decide si una venta se acepta. */
    public ResumenVentasResponse resumen(LocalDate desde, LocalDate hasta) {
        List<VentaQueso> ventas = listarVentas(desde, hasta);
        double acumulada = ventas.stream().mapToDouble(VentaQueso::total).sum();
        double porCobrar = ventas.stream()
                .filter(v -> v.getEstado() == EstadoCobro.POR_COBRAR)
                .mapToDouble(VentaQueso::total)
                .sum();
        int stock = lotesConStock().stream().mapToInt(LoteConStock::stock).sum();
        return new ResumenVentasResponse(acumulada, ventas.size(), stock, porCobrar);
    }

    // ---- Tarifas por producto (se editan en Configuracion) ----

    /**
     * Precio que rige para un producto en una fecha. Sin precio no se vende: inventar uno por defecto
     * seria cobrar un numero que nadie autorizo, y descubrirlo tarde significa reclamar a un cliente
     * que ya se llevo el producto.
     *
     * <p>Una promocion (periodo con fecha de fin) manda mientras dura porque empezo despues que el
     * precio normal; cuando termina deja de cubrir la fecha y vuelve a mandar el anterior, sin que
     * nadie tenga que acordarse de reponerlo.
     */
    public TarifaQueso tarifaVigente(Producto producto, LocalDate fecha) {
        return tarifaRepository.vigentesEn(producto.getId(), fecha).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay precios de " + producto.getNombre() + " configurados para el " + fecha
                                + ". Configúrelos en la lista de precios antes de vender."));
    }

    public TarifaQueso tarifaVigente(Long productoId, LocalDate fecha) {
        return tarifaVigente(productoService.activoOHistorico(productoId), fecha);
    }

    /** Todos los periodos, o solo los de un producto. */
    public List<TarifaQueso> listarTarifas(Long productoId) {
        return productoId == null
                ? tarifaRepository.findByActivoTrueOrderByProductoNombreAscVigenteDesdeDesc()
                : tarifaRepository.findByActivoTrueAndProductoIdOrderByVigenteDesdeDesc(productoId);
    }

    /**
     * Registra una nueva tarifa. No corrige la vigente: se crea otra fila con su fecha de inicio, de
     * modo que las ventas ya emitidas conserven el precio con el que se cobraron.
     *
     * <p>Se audita porque cambia lo que cobra la cooperativa: un precio nuevo sin autor registrado es
     * un agujero de responsabilidad del mismo tipo que un pago sin firmar.
     */
    @Transactional
    public TarifaQueso guardarTarifa(TarifaQuesoRequest req) {
        if (req.vigenteHasta() != null && req.vigenteHasta().isBefore(req.vigenteDesde())) {
            throw new BusinessRuleException("La fecha de fin no puede ser anterior a la de inicio.");
        }
        TarifaQueso tarifa = new TarifaQueso();
        tarifa.setProducto(productoService.activo(req.productoId()));
        tarifa.setDescripcion(req.descripcion() != null && !req.descripcion().isBlank() ? req.descripcion().trim() : null);
        tarifa.setPrecioPublicoGeneral(req.precioPublicoGeneral());
        tarifa.setPrecioMayorista(req.precioMayorista());
        tarifa.setPrecioProveedor(req.precioProveedor());
        tarifa.setVigenteDesde(req.vigenteDesde());
        tarifa.setVigenteHasta(req.vigenteHasta());
        TarifaQueso guardada = tarifaRepository.save(tarifa);
        auditoriaService.registrar(
                "Precios de queso",
                ENTIDAD,
                guardada.getProducto().getNombre() + " · vigente desde " + guardada.getVigenteDesde()
                        + (guardada.getVigenteHasta() != null ? " hasta " + guardada.getVigenteHasta() + " (promoción)" : "")
                        + " · público S/ " + guardada.getPrecioPublicoGeneral()
                        + " · mayorista S/ " + guardada.getPrecioMayorista() + " · proveedor S/ "
                        + guardada.getPrecioProveedor());
        return guardada;
    }

    private static String describir(VentaQueso venta) {
        return venta.getLote().getCodigo() + " · " + venta.getCliente() + " · " + venta.getUnidades() + " u · S/ "
                + String.format(java.util.Locale.US, "%.2f", venta.total());
    }

    /**
     * Ni un lote ni una venta pueden registrarse con fecha futura: seria producto que todavia no
     * existe, y ademas descuadraria el stock de hoy contra lo que hay fisicamente en camara.
     */
    private static void exigirFechaNoFutura(LocalDate fecha, String que) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(que + " no puede registrarse con fecha futura.");
        }
    }
}
