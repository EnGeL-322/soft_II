package org.ramiro.huata.produccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.compras.CompraService;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.quesos.LoteQueso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ordenes de produccion (V61): planificar, ver si estan listas, sugerir compras y cumplirlas.
 *
 * <p><b>Reserva en orden.</b> Si estan "listas" no se mira orden por orden contra todo el almacen:
 * dos ordenes no pueden contar la misma leche. Se recorren en el orden en que se planificaron y cada
 * una reserva lo que necesita; la siguiente ve lo que queda. Asi "lista" significa que de verdad se
 * puede producir sin quitarle a una orden anterior.
 */
@Service
@RequiredArgsConstructor
public class OrdenProduccionService {

    private static final String ENTIDAD = "Ordenes de produccion";

    private final OrdenProduccionRepository ordenRepository;
    private final RecetaQuesoRepository recetaRepository;
    private final ProductoService productoService;
    private final InventarioService inventarioService;
    private final CompraService compraService;
    private final AuditoriaService auditoriaService;

    @Transactional(readOnly = true)
    public List<OrdenProduccionResponse> listar() {
        Map<Long, List<OrdenLineaResponse>> reservas = reservar(ordenRepository.findByEstadoOrderByFechaPlanAscIdAsc(
                OrdenProduccion.Estado.PENDIENTE));
        return ordenRepository.findAllByOrderByFechaPlanDescIdDesc().stream()
                .map(o -> responder(o, reservas.get(o.getId())))
                .toList();
    }

    @Transactional
    public OrdenProduccionResponse crear(OrdenProduccionRequest req) {
        Producto producto = productoService.activo(req.productoId());
        LocalDate fecha = req.fechaPlan() != null ? req.fechaPlan() : LocalDate.now();
        RecetaQueso receta = recetaRepository
                .findTopByActivoTrueAndProductoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(producto.getId(), fecha)
                .orElseThrow(() -> new BusinessRuleException(producto.getNombre()
                        + " no tiene receta vigente. Créala en Recetas antes de planificar su producción."));

        OrdenProduccion orden = new OrdenProduccion();
        orden.setProducto(producto);
        orden.setReceta(receta);
        orden.setCantidad(req.cantidad());
        orden.setFechaPlan(fecha);
        orden.setObservaciones(req.observaciones() == null || req.observaciones().isBlank() ? null : req.observaciones().trim());
        // El codigo se pone DESPUES de guardar, a partir del id, como en los lotes.
        orden.setCodigo("TMP-" + java.util.UUID.randomUUID().toString().substring(0, 12));
        OrdenProduccion guardada = ordenRepository.save(orden);
        guardada.setCodigo("OP" + fecha.getYear() + "-" + String.format("%04d", guardada.getId()));
        guardada = ordenRepository.save(guardada);

        auditoriaService.registrar("Orden de produccion", ENTIDAD,
                guardada.getCodigo() + " · " + guardada.getCantidad() + " " + producto.getNombre() + " para el " + fecha);
        return listar().stream().filter(o -> o.id().equals(orden.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public OrdenProduccionResponse anular(Long id, String motivoRecibido) {
        String motivo = motivoRecibido == null ? "" : motivoRecibido.trim();
        if (motivo.length() < 3) {
            throw new BusinessRuleException("Anular una orden exige un motivo.");
        }
        OrdenProduccion orden = ordenRepository.findById(id).orElseThrow(() -> NotFoundException.of("Orden de produccion", id));
        if (orden.getEstado() != OrdenProduccion.Estado.PENDIENTE) {
            throw new BusinessRuleException("La orden " + orden.getCodigo() + " ya está "
                    + (orden.getEstado() == OrdenProduccion.Estado.TERMINADA
                            ? "terminada: para deshacerla, anula su lote " + orden.getLote().getCodigo() + "."
                            : "anulada."));
        }
        orden.setEstado(OrdenProduccion.Estado.ANULADA);
        orden.setMotivoAnulacion(motivo);
        ordenRepository.save(orden);
        auditoriaService.registrar("Anulacion de orden de produccion", ENTIDAD, orden.getCodigo() + " · " + motivo);
        return responder(orden, null);
    }

    /**
     * La orden pendiente que va a cumplir este lote: del mismo producto. La llama el registro de
     * produccion antes de crear el lote.
     */
    @Transactional(readOnly = true)
    public OrdenProduccion pendienteParaLote(Long ordenId, Long productoId) {
        OrdenProduccion orden = ordenRepository.findById(ordenId)
                .orElseThrow(() -> NotFoundException.of("Orden de produccion", ordenId));
        if (orden.getEstado() != OrdenProduccion.Estado.PENDIENTE) {
            throw new BusinessRuleException("La orden " + orden.getCodigo() + " ya no está pendiente.");
        }
        if (!orden.getProducto().getId().equals(productoId)) {
            throw new BusinessRuleException("La orden " + orden.getCodigo() + " es de " + orden.getProducto().getNombre()
                    + ", no de este producto.");
        }
        return orden;
    }

    /** El lote cumplio la orden. */
    @Transactional
    public void cumplir(OrdenProduccion orden, LoteQueso lote) {
        orden.setEstado(OrdenProduccion.Estado.TERMINADA);
        orden.setLote(lote);
        ordenRepository.save(orden);
        auditoriaService.registrar("Orden de produccion cumplida", ENTIDAD, orden.getCodigo() + " · lote " + lote.getCodigo());
    }

    /** Se anulo el lote que la cumplia: la orden vuelve a esperar produccion. */
    @Transactional
    public void reabrirPorLoteAnulado(LoteQueso lote) {
        ordenRepository.findByLote_Id(lote.getId()).ifPresent(orden -> {
            orden.setEstado(OrdenProduccion.Estado.PENDIENTE);
            orden.setLote(null);
            ordenRepository.save(orden);
            auditoriaService.registrar("Orden de produccion reabierta", ENTIDAD,
                    orden.getCodigo() + " · se anuló el lote " + lote.getCodigo());
        });
    }

    /**
     * Lo que falta comprar para todas las ordenes pendientes: lo requerido menos el almacen menos lo
     * ya comprado que no llega. Solo lo que hay que comprar (la leche va aparte: llega del acopio).
     */
    @Transactional(readOnly = true)
    public List<NecesidadResponse> necesidades() {
        List<OrdenProduccion> pendientes = ordenRepository.findByEstadoOrderByFechaPlanAscIdAsc(OrdenProduccion.Estado.PENDIENTE);
        Map<Long, MateriaPrima> materias = new LinkedHashMap<>();
        Map<Long, BigDecimal> requerido = new HashMap<>();
        Map<Long, List<String>> ordenesPor = new HashMap<>();
        for (OrdenProduccion o : pendientes) {
            for (CalculoProduccion.Consumo c : CalculoProduccion.consumoPara(o.getReceta(), o.getCantidad())) {
                Long id = c.materiaPrima().getId();
                materias.putIfAbsent(id, c.materiaPrima());
                requerido.merge(id, c.cantidad(), BigDecimal::add);
                ordenesPor.computeIfAbsent(id, k -> new ArrayList<>()).add(o.getCodigo());
            }
        }
        if (materias.isEmpty()) {
            return List.of();
        }
        Map<Long, BigDecimal> stock = inventarioService.saldosMateriasPrimas(materias.keySet());
        Map<Long, BigDecimal> enCamino = compraService.enCaminoPorMateriaPrima();
        List<NecesidadResponse> out = new ArrayList<>();
        for (MateriaPrima m : materias.values()) {
            BigDecimal req = requerido.get(m.getId());
            BigDecimal hay = noNegativo(stock.getOrDefault(m.getId(), BigDecimal.ZERO));
            BigDecimal llega = enCamino.getOrDefault(m.getId(), BigDecimal.ZERO);
            BigDecimal aComprar = noNegativo(req.subtract(hay).subtract(llega));
            if (aComprar.signum() == 0 && !(m.isLecheAcopio() && req.compareTo(hay) > 0)) {
                continue;
            }
            out.add(new NecesidadResponse(
                    m.getId(), m.codigo(), m.getNombre(), m.getUnidadMedida().name(), req, hay, llega,
                    m.isLecheAcopio() ? noNegativo(req.subtract(hay)) : aComprar,
                    m.getCostoUnitarioReferencial(), m.isLecheAcopio(), ordenesPor.get(m.getId())));
        }
        return out;
    }

    /** Reserva el almacen orden por orden (la primera planificada, primero) y dice que le toca a cada una. */
    private Map<Long, List<OrdenLineaResponse>> reservar(List<OrdenProduccion> pendientes) {
        Map<Long, List<OrdenLineaResponse>> porOrden = new HashMap<>();
        if (pendientes.isEmpty()) {
            return porOrden;
        }
        List<Long> ids = pendientes.stream()
                .flatMap(o -> CalculoProduccion.porUnidad(o.getReceta()).keySet().stream())
                .map(MateriaPrima::getId)
                .distinct()
                .toList();
        Map<Long, BigDecimal> libre = new HashMap<>(inventarioService.saldosMateriasPrimas(ids));
        for (OrdenProduccion o : pendientes) {
            List<OrdenLineaResponse> lineas = new ArrayList<>();
            for (CalculoProduccion.Consumo c : CalculoProduccion.consumoPara(o.getReceta(), o.getCantidad())) {
                MateriaPrima m = c.materiaPrima();
                BigDecimal disponible = noNegativo(libre.getOrDefault(m.getId(), BigDecimal.ZERO));
                BigDecimal falta = noNegativo(c.cantidad().subtract(disponible));
                lineas.add(new OrdenLineaResponse(
                        m.getId(), m.codigo(), m.getNombre(), m.getUnidadMedida().name(), c.cantidad(), disponible, falta,
                        m.isLecheAcopio()));
                libre.put(m.getId(), disponible.subtract(c.cantidad().min(disponible)));
            }
            porOrden.put(o.getId(), lineas);
        }
        return porOrden;
    }

    private OrdenProduccionResponse responder(OrdenProduccion o, List<OrdenLineaResponse> lineas) {
        boolean pendiente = o.getEstado() == OrdenProduccion.Estado.PENDIENTE;
        Producto p = o.getProducto();
        return new OrdenProduccionResponse(
                o.getId(),
                o.getCodigo(),
                p.getId(),
                p.codigo(),
                p.getNombre(),
                p.getUnidadVenta(),
                o.getReceta().getId(),
                o.getCantidad(),
                o.getFechaPlan(),
                o.getEstado(),
                pendiente && lineas != null ? lineas.stream().allMatch(l -> l.falta().signum() == 0) : null,
                lineas != null ? lineas : List.of(),
                o.getLote() != null ? o.getLote().getId() : null,
                o.getLote() != null ? o.getLote().getCodigo() : null,
                o.getObservaciones(),
                o.getMotivoAnulacion(),
                o.getCreatedBy());
    }

    private static BigDecimal noNegativo(BigDecimal v) {
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }
}
