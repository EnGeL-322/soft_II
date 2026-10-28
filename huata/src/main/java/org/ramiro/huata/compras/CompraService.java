package org.ramiro.huata.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.contabilidad.AsientoContableService;
import org.ramiro.huata.contabilidad.LineaContable;
import org.ramiro.huata.contabilidad.OrigenAsiento;
import org.ramiro.huata.inventario.DocumentoOrigenTipo;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compras de materia prima/insumos y su recepcion (V61).
 *
 * <p>Registrar una compra NO mueve el almacen: la deja PENDIENTE_RECEPCION. Lo que entra al almacen
 * es la recepcion (total o parcial, con lote y vencimiento del proveedor): por cada linea recibida
 * genera un movimiento {@code ENTRADA_COMPRA} en el kardex, actualiza el costo de referencia y
 * registra el asiento por el valor recibido. La compra al contado que llega con la factura se recibe
 * en el mismo acto ({@link CompraRequest#recibirEnElActo()}), que es como funcionaba antes de V61.
 * Todo en una sola transaccion (RNF-18).
 */
@Service
@RequiredArgsConstructor
public class CompraService {

    private final CompraRepository compraRepository;
    private final RecepcionCompraRepository recepcionRepository;
    private final ProveedorCompraRepository proveedorRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final InventarioService inventarioService;
    private final AuditoriaService auditoriaService;
    private final AsientoContableService asientoContableService;

    private static final String ENTIDAD = "Compras";

    /**
     * Mapea a DTO dentro de la transaccion: {@code Compra.items} y {@code CompraItem.materiaPrima}
     * son LAZY, y el response los recorre. Devolver la entidad para mapearla en el controller
     * revienta con LazyInitializationException porque la sesion ya se cerro.
     */
    @Transactional(readOnly = true)
    public Page<CompraResponse> listar(LocalDate desde, LocalDate hasta, Pageable pageable) {
        Page<Compra> pagina = (desde == null && hasta == null)
                ? compraRepository.findAllByOrderByFechaDescIdDesc(pageable)
                : compraRepository.findByFechaBetweenOrderByFechaDescIdDesc(
                        desde != null ? desde : LocalDate.now().minusMonths(1), hasta != null ? hasta : LocalDate.now(), pageable);
        return pagina.map(CompraResponse::from);
    }

    /** Lo que se compro y todavia no llega (completo o en parte): la bandeja de Recepciones. */
    @Transactional(readOnly = true)
    public List<CompraResponse> pendientesDeRecepcion() {
        return compraRepository.findByActivoTrueAndEstadoNotOrderByFechaAscIdAsc(EstadoCompra.RECIBIDA).stream()
                .map(CompraResponse::from)
                .toList();
    }

    /** Cuanto de cada materia prima esta comprado y en camino (compras activas sin recibir del todo). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> enCaminoPorMateriaPrima() {
        Map<Long, BigDecimal> enCamino = new HashMap<>();
        for (Compra c : compraRepository.findByActivoTrueAndEstadoNotOrderByFechaAscIdAsc(EstadoCompra.RECIBIDA)) {
            for (CompraItem i : c.getItems()) {
                if (i.pendiente().signum() > 0) {
                    enCamino.merge(i.getMateriaPrima().getId(), i.pendiente(), BigDecimal::add);
                }
            }
        }
        return enCamino;
    }

    @Transactional(readOnly = true)
    public List<RecepcionResponse> recepciones() {
        return recepcionRepository.todasConDetalle().stream().map(RecepcionResponse::from).toList();
    }

    @Transactional
    public CompraResponse registrarYResponder(CompraRequest req) {
        return CompraResponse.from(registrar(req));
    }

    @Transactional
    public Compra registrar(CompraRequest req) {
        exigirFechaNoFutura(req.fecha(), "La compra");
        ProveedorCompra proveedor = proveedorRepository
                .findById(req.proveedorCompraId())
                .filter(ProveedorCompra::isActivo)
                .orElseThrow(() -> NotFoundException.of("Proveedor de compra", req.proveedorCompraId()));

        Compra compra = new Compra();
        compra.setProveedor(proveedor);
        compra.setFecha(req.fecha());
        compra.setDocumento(blancoANulo(req.documento()));
        compra.setObservaciones(blancoANulo(req.observaciones()));

        // El mismo insumo puede venir en dos lineas (una factura con dos precios): la recepcion
        // trabaja por id de linea, asi que no hay ambiguedad. La web avisa, pero no se prohibe.
        for (CompraItemRequest itemReq : req.items()) {
            MateriaPrima materiaPrima = materiaPrimaRepository
                    .findById(itemReq.materiaPrimaId())
                    .filter(MateriaPrima::isActivo)
                    .orElseThrow(() -> NotFoundException.of("Materia prima", itemReq.materiaPrimaId()));
            // La leche entra al almacen al recibir rutas o entregas directas, con su liquidacion al
            // proveedor: comprarla aqui la contaria dos veces. La web ya no la ofrece; esto cubre la API.
            if (materiaPrima.isLecheAcopio()) {
                throw new BusinessRuleException(
                        "La leche no se compra: entra al almacén al recibir las rutas o una entrega directa.");
            }
            CompraItem item = new CompraItem();
            item.setCompra(compra);
            item.setMateriaPrima(materiaPrima);
            item.setCantidad(itemReq.cantidad());
            item.setCostoUnitario(itemReq.costoUnitario());
            compra.getItems().add(item);
        }
        compra.setEstado(EstadoCompra.PENDIENTE_RECEPCION);
        Compra guardada = compraRepository.save(compra);

        auditoriaService.registrar(
                "Compra de materia prima",
                ENTIDAD,
                guardada.codigo() + " · " + proveedor.getNombre() + " · " + guardada.getItems().size()
                        + " item(s) · S/ " + guardada.total() + (req.recibirEnElActo() ? " · recibida en el acto" : " · pendiente de recepción"));

        if (req.recibirEnElActo()) {
            List<RecepcionItemRequest> todo = guardada.getItems().stream()
                    .map(i -> new RecepcionItemRequest(i.getId(), i.getCantidad(), null, null))
                    .toList();
            recibir(guardada.getId(), new RecepcionRequest(req.fecha(), req.documento(), todo));
        }
        return guardada;
    }

    @Transactional
    public CompraResponse recibirYResponder(Long compraId, RecepcionRequest req) {
        recibir(compraId, req);
        return CompraResponse.from(compraRepository.findById(compraId).orElseThrow());
    }

    /**
     * Recibe lo que llego de una compra: total o en parte, con el lote y el vencimiento del
     * proveedor. Cada linea recibida entra al almacen y la recepcion registra su asiento por el
     * valor recibido. No se puede recibir mas de lo pendiente ni con fecha anterior a la compra.
     */
    @Transactional
    public RecepcionCompra recibir(Long compraId, RecepcionRequest req) {
        Compra compra = compraRepository.findById(compraId).orElseThrow(() -> NotFoundException.of("Compra", compraId));
        if (!compra.isActivo()) {
            throw new BusinessRuleException("La compra " + compra.codigo() + " está anulada.");
        }
        if (compra.getEstado() == EstadoCompra.RECIBIDA) {
            throw new BusinessRuleException("La compra " + compra.codigo() + " ya se recibió completa.");
        }
        LocalDate fecha = req.fecha() != null ? req.fecha() : LocalDate.now();
        exigirFechaNoFutura(fecha, "La recepción");
        if (fecha.isBefore(compra.getFecha())) {
            throw new BusinessRuleException("La recepción no puede ser anterior a la compra (" + compra.getFecha() + ").");
        }

        Map<Long, CompraItem> lineas = new HashMap<>();
        compra.getItems().forEach(i -> lineas.put(i.getId(), i));

        RecepcionCompra recepcion = new RecepcionCompra();
        recepcion.setCompra(compra);
        recepcion.setFecha(fecha);
        recepcion.setDocumento(blancoANulo(req.documento()) != null ? req.documento().trim() : compra.getDocumento());

        Set<Long> vistas = new HashSet<>();
        for (RecepcionItemRequest r : req.items()) {
            CompraItem linea = lineas.get(r.compraItemId());
            if (linea == null) {
                throw new BusinessRuleException("La línea " + r.compraItemId() + " no es de la compra " + compra.codigo() + ".");
            }
            if (!vistas.add(linea.getId())) {
                throw new BusinessRuleException(linea.getMateriaPrima().getNombre() + " está dos veces en la recepción.");
            }
            if (r.cantidad().compareTo(linea.pendiente()) > 0) {
                throw new BusinessRuleException("De " + linea.getMateriaPrima().getNombre() + " quedan por recibir "
                        + legible(linea.pendiente()) + " y se quiere recibir " + legible(r.cantidad()) + ".");
            }
            if (r.fechaVencimiento() != null && r.fechaVencimiento().isBefore(fecha)) {
                throw new BusinessRuleException(linea.getMateriaPrima().getNombre() + " llega ya vencido ("
                        + r.fechaVencimiento() + "). No lo recibas o corrige la fecha.");
            }
            RecepcionItem item = new RecepcionItem();
            item.setRecepcion(recepcion);
            item.setCompraItem(linea);
            item.setCantidad(r.cantidad());
            item.setLote(blancoANulo(r.lote()));
            item.setFechaVencimiento(r.fechaVencimiento());
            recepcion.getItems().add(item);
            linea.setCantidadRecibida(linea.getCantidadRecibida().add(r.cantidad()));
        }
        if (recepcion.getItems().isEmpty()) {
            throw new BusinessRuleException("Indica cuánto llegó de al menos un insumo.");
        }
        compra.actualizarEstado();
        compraRepository.save(compra);
        RecepcionCompra guardada = recepcionRepository.save(recepcion);

        for (RecepcionItem item : guardada.getItems()) {
            MateriaPrima materiaPrima = item.getCompraItem().getMateriaPrima();
            inventarioService.registrarEntradaCompra(materiaPrima, item.getCantidad(), fecha, compra.getId());
            // Informativo: el costo de referencia para prellenar el siguiente formulario, no la
            // fuente de verdad de ningun costo de lote ya cerrado.
            materiaPrima.setCostoUnitarioReferencial(item.getCompraItem().getCostoUnitario());
            materiaPrimaRepository.save(materiaPrima);
        }
        registrarAsientoDeRecepcion(guardada);

        auditoriaService.registrar(
                "Recepción de compra",
                ENTIDAD,
                guardada.codigo() + " de " + compra.codigo() + " · " + guardada.getItems().size() + " línea(s) · S/ "
                        + guardada.total().setScale(2, java.math.RoundingMode.HALF_UP) + " · compra " + compra.getEstado());
        return guardada;
    }

    /** {@link #anular} mapeado dentro de la transaccion: proveedor e items son LAZY. */
    @Transactional
    public CompraResponse anularYResponder(Long id, String motivo) {
        return CompraResponse.from(anular(id, motivo));
    }

    /**
     * Anula una compra registrada por error (V59): saca del almacen lo que ya entro por ella y
     * revierte su asiento. Lo que no llego no movio nada, asi que una compra pendiente se anula sin
     * mas. Solo si lo recibido sigue en el almacen; si parte ya se uso en produccion, la salida dejaria
     * el stock negativo y lo que corresponde es un ajuste de inventario. No se borra nada.
     */
    @Transactional
    public Compra anular(Long id, String motivoRecibido) {
        String motivo = motivoRecibido == null ? "" : motivoRecibido.trim();
        if (motivo.length() < 3) {
            throw new BusinessRuleException("La anulación de una compra exige un motivo.");
        }
        Compra compra = compraRepository.findById(id).orElseThrow(() -> NotFoundException.of("Compra", id));
        if (!compra.isActivo()) {
            throw new BusinessRuleException("La compra ya está anulada.");
        }

        // Lo recibido por materia prima, bloqueadas en orden ascendente de id como al producir, para
        // no cruzarse con un lote simultaneo.
        Map<Long, BigDecimal> porMateria = new TreeMap<>();
        Map<Long, MateriaPrima> materias = new HashMap<>();
        for (CompraItem item : compra.getItems()) {
            if (item.getCantidadRecibida().signum() > 0) {
                porMateria.merge(item.getMateriaPrima().getId(), item.getCantidadRecibida(), BigDecimal::add);
                materias.put(item.getMateriaPrima().getId(), item.getMateriaPrima());
            }
        }
        LocalDate hoy = LocalDate.now();
        String nota = "Anulación de compra " + compra.codigo() + ": " + motivo;
        for (var e : porMateria.entrySet()) {
            MateriaPrima materiaPrima = inventarioService.bloquearMateriaPrima(e.getKey());
            BigDecimal disponible = inventarioService.saldoMateriaPrima(materiaPrima.getId());
            if (disponible.compareTo(e.getValue()) < 0) {
                throw new BusinessRuleException("No se puede anular: de " + materias.get(e.getKey()).getNombre()
                        + " entraron " + legible(e.getValue()) + " y en el almacén quedan " + legible(disponible)
                        + " (el resto ya se usó en producción). Corrígelo con un ajuste de inventario.");
            }
            inventarioService.registrarAjusteSalida(materiaPrima, e.getValue(), hoy, DocumentoOrigenTipo.COMPRA, compra.getId(), nota);
        }

        BigDecimal recibido = compra.totalRecibido();
        compra.setActivo(false);
        compra.setMotivoAnulacion(motivo);
        Compra guardada = compraRepository.save(compra);

        if (recibido.signum() > 0) {
            asientoContableService.registrar(
                    hoy,
                    "Anulación: compra " + guardada.codigo() + " a " + guardada.getProveedor().getNombre(),
                    "Por la reversión de lo recibido de la compra a " + guardada.getProveedor().getNombre() + ", documento "
                            + (guardada.getDocumento() != null ? guardada.getDocumento() : "s/n") + " (" + motivo + ").",
                    OrigenAsiento.ANULACION_COMPRA,
                    guardada.getId(),
                    List.of(LineaContable.debe("4212", recibido), LineaContable.haber("2411", recibido)));
        }

        auditoriaService.registrar(
                "Anulación de compra",
                ENTIDAD,
                guardada.codigo() + " · " + guardada.getProveedor().getNombre() + " · S/ " + guardada.total()
                        + (recibido.signum() > 0 ? " (recibido S/ " + recibido + ")" : " (sin recibir)") + " · " + motivo);
        return guardada;
    }

    /**
     * Debe materia prima/materiales auxiliares, haber cuentas por pagar, por lo RECIBIDO: el
     * inventario contable entra cuando entra el fisico. Se asume compra a credito porque {@code Compra}
     * no distingue medio de pago todavia. El origen sigue siendo la compra, para que su historial
     * contable se lea junto.
     */
    private void registrarAsientoDeRecepcion(RecepcionCompra recepcion) {
        BigDecimal total = recepcion.total();
        if (total.signum() <= 0) {
            return;
        }
        Compra compra = recepcion.getCompra();
        asientoContableService.registrar(
                recepcion.getFecha(),
                "Compra de materia prima e insumos, " + compra.getProveedor().getNombre(),
                "Por la recepción " + recepcion.codigo() + " de la compra " + compra.codigo() + " a "
                        + compra.getProveedor().getNombre() + " (a credito), documento "
                        + (recepcion.getDocumento() != null ? recepcion.getDocumento() : "s/n") + ".",
                OrigenAsiento.COMPRA,
                compra.getId(),
                List.of(LineaContable.debe("2411", total), LineaContable.haber("4212", total)));
    }

    private static void exigirFechaNoFutura(LocalDate fecha, String que) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new BusinessRuleException(que + " no puede registrarse con fecha futura.");
        }
    }

    private static String blancoANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static String legible(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }
}
