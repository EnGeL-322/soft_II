package org.ramiro.huata.produccion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.inventario.InventarioService;
import org.ramiro.huata.inventario.MateriaPrima;
import org.ramiro.huata.inventario.MateriaPrimaRepository;
import org.ramiro.huata.inventario.MateriaPrimaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receta/BOM por producto (queso, yogur...). Se versiona como {@code TarifaQueso}: crear una receta nunca edita
 * una version anterior, y la version vigente en una fecha es la mas reciente con
 * {@code vigenteDesde <= fecha} (misma consulta, mismo criterio: {@code vigenteHasta} es informativo,
 * no decide la vigencia).
 */
@Service
@RequiredArgsConstructor
public class RecetaService {

    private final RecetaQuesoRepository recetaRepository;
    private final MateriaPrimaRepository materiaPrimaRepository;
    private final MateriaPrimaService materiaPrimaService;
    private final InventarioService inventarioService;
    private final ProductoService productoService;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Produccion";

    // Los tres metodos de lectura mapean a DTO DENTRO de la transaccion (no en el controller, como
    // hace el resto del modulo): receta.getItems() es @OneToMany LAZY, y el response necesita
    // recorrerlo. Devolver la entidad y mapearla despues en el controller revienta con
    // LazyInitializationException porque la sesion de Hibernate ya se cerro al salir del metodo
    // @Transactional.

    @Transactional(readOnly = true)
    public List<RecetaResponse> listar() {
        return recetaRepository.findByActivoTrueOrderByProductoNombreAscVigenteDesdeDesc().stream()
                .map(RecetaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecetaResponse vigente(Long productoId, LocalDate fecha) {
        return RecetaResponse.from(vigenteEntidad(productoId, fecha));
    }

    /** Version de entidad, para uso interno (p.ej. si otro servicio necesitara la receta vigente). */
    RecetaQueso vigenteEntidad(Long productoId, LocalDate fecha) {
        return recetaRepository
                .findTopByActivoTrueAndProductoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(productoId, fecha)
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay una receta configurada para este producto vigente el " + fecha
                                + ". Configúrela en Producción antes de crear un lote."));
    }

    @Transactional
    public RecetaQueso crear(RecetaRequest req) {
        RecetaQueso receta = new RecetaQueso();
        receta.setProducto(productoService.activo(req.productoId()));
        receta.setVigenteDesde(req.vigenteDesde());
        receta.setVigenteHasta(req.vigenteHasta());
        receta.setRendimientoLitrosPorUnidad(req.rendimientoLitrosPorUnidad());
        receta.setObservaciones(req.observaciones());
        java.util.Set<Long> usados = new java.util.HashSet<>();
        for (RecetaItemRequest itemReq : req.items()) {
            MateriaPrima materiaPrima = resolverInsumo(itemReq);
            if (!usados.add(materiaPrima.getId())) {
                throw new BusinessRuleException("\"" + materiaPrima.getNombre()
                        + "\" esta dos veces en la receta. Dejalo una sola vez con la cantidad total.");
            }
            RecetaItem item = new RecetaItem();
            item.setReceta(receta);
            item.setMateriaPrima(materiaPrima);
            item.setCantidadPorUnidad(itemReq.cantidadPorUnidad());
            receta.getItems().add(item);
        }
        RecetaQueso guardada = recetaRepository.save(receta);
        auditoriaService.registrar(
                "Receta de queso",
                ENTIDAD,
                guardada.getProducto().getNombre() + " · vigente desde " + guardada.getVigenteDesde() + " · " + guardada.getItems().size()
                        + " insumo(s)");
        return guardada;
    }

    /**
     * Con id: el insumo tiene que existir y estar activo (uno dado de baja dejo de comprarse; para
     * volver a usarlo se escribe su nombre y se reactiva a proposito). Sin id: se busca por nombre y,
     * si no existe, se crea con la unidad indicada.
     */
    private MateriaPrima resolverInsumo(RecetaItemRequest itemReq) {
        if (itemReq.materiaPrimaId() != null) {
            MateriaPrima m = materiaPrimaRepository.findById(itemReq.materiaPrimaId())
                    .orElseThrow(() -> NotFoundException.of("Materia prima", itemReq.materiaPrimaId()));
            if (!m.isActivo()) {
                throw new NotFoundException("\"" + m.getNombre() + "\" fue dado de baja en Inventario. "
                        + "Quitalo de la receta o vuelve a escribir su nombre para reactivarlo.");
            }
            return m;
        }
        if (itemReq.nombre() == null || itemReq.nombre().isBlank() || itemReq.unidadMedida() == null) {
            throw new BusinessRuleException("Cada ingrediente nuevo necesita su nombre y su unidad de medida.");
        }
        return materiaPrimaService.obtenerOCrear(itemReq.nombre(), itemReq.unidadMedida());
    }

    /**
     * Cuanto haria falta de cada materia prima para producir {@code cantidadObjetivo} unidades, y si
     * el stock actual alcanza. No persiste nada (RF-66): es un calculo de planificacion, previo a
     * crear el lote real.
     */
    @Transactional(readOnly = true)
    public SimulacionResponse simular(Long recetaId, int cantidadObjetivo) {
        RecetaQueso receta = recetaRepository.findById(recetaId).orElseThrow(() -> NotFoundException.of("Receta", recetaId));
        var ids = receta.getItems().stream().map(i -> i.getMateriaPrima().getId()).toList();
        var stocks = inventarioService.saldosMateriasPrimas(ids);

        List<SimulacionItemResponse> filas = receta.getItems().stream()
                .map(item -> {
                    BigDecimal requerida = item.getCantidadPorUnidad().multiply(BigDecimal.valueOf(cantidadObjetivo));
                    BigDecimal stock = stocks.getOrDefault(item.getMateriaPrima().getId(), BigDecimal.ZERO);
                    return new SimulacionItemResponse(
                            item.getMateriaPrima().getId(),
                            item.getMateriaPrima().getNombre(),
                            item.getMateriaPrima().getUnidadMedida().name(),
                            requerida,
                            stock,
                            stock.compareTo(requerida) >= 0);
                })
                .toList();

        boolean alcanzaTodo = filas.stream().allMatch(SimulacionItemResponse::alcanza);
        return new SimulacionResponse(recetaId, cantidadObjetivo, filas, alcanzaTodo);
    }

    /**
     * Cuanto se puede producir de un producto con lo que hay hoy en el almacen: por cada insumo de la
     * receta vigente, para cuantas unidades alcanza su stock; el maximo es el menor de todos y ese
     * insumo es el limitante.
     *
     * <p>No lanza error si el producto no tiene receta: la pantalla tiene que poder decir "falta la
     * receta" sin quedarse en blanco. Tampoco persiste nada.
     */
    @Transactional(readOnly = true)
    public CapacidadProduccionResponse capacidad(Long productoId, LocalDate fecha) {
        BigDecimal lecheDisponible = materiaPrimaRepository
                .findFirstByLecheAcopioTrue()
                .map(leche -> inventarioService.saldoMateriaPrima(leche.getId()))
                .orElse(null);
        if (productoId == null) {
            return new CapacidadProduccionResponse(
                    null, null, null, null, false, false, null, null, null, lecheDisponible, List.of());
        }

        Producto producto = productoService.activo(productoId);
        RecetaQueso receta = recetaRepository
                .findTopByActivoTrueAndProductoIdAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(productoId, fecha)
                .orElse(null);
        if (receta == null) {
            return new CapacidadProduccionResponse(
                    producto.getId(), producto.getNombre(), producto.getUnidadVenta(), null, false, false,
                    null, null, null, lecheDisponible, List.of());
        }

        Map<MateriaPrima, BigDecimal> porUnidad = CalculoProduccion.porUnidad(receta);
        Map<Long, BigDecimal> stocks = inventarioService.saldosMateriasPrimas(
                porUnidad.keySet().stream().map(MateriaPrima::getId).toList());

        // El limitante es el primero con menos alcance, en el orden de la receta.
        MateriaPrima limitante = null;
        int maximo = Integer.MAX_VALUE;
        for (Map.Entry<MateriaPrima, BigDecimal> e : porUnidad.entrySet()) {
            int alcanza = CalculoProduccion.alcanzaPara(
                    stocks.getOrDefault(e.getKey().getId(), BigDecimal.ZERO), e.getValue());
            if (alcanza < maximo) {
                maximo = alcanza;
                limitante = e.getKey();
            }
        }
        Long limitanteId = limitante != null ? limitante.getId() : null;

        List<CapacidadItemResponse> items = porUnidad.entrySet().stream()
                .map(e -> {
                    MateriaPrima materia = e.getKey();
                    BigDecimal stock = stocks.getOrDefault(materia.getId(), BigDecimal.ZERO);
                    return new CapacidadItemResponse(
                            materia.getId(),
                            materia.getNombre(),
                            materia.getUnidadMedida().name(),
                            e.getValue(),
                            stock,
                            CalculoProduccion.alcanzaPara(stock, e.getValue()),
                            materia.getId().equals(limitanteId),
                            materia.isLecheAcopio());
                })
                .toList();

        return new CapacidadProduccionResponse(
                producto.getId(),
                producto.getNombre(),
                producto.getUnidadVenta(),
                receta.getId(),
                true,
                items.stream().anyMatch(CapacidadItemResponse::esLeche),
                limitante != null ? maximo : null,
                limitanteId,
                limitante != null ? limitante.getNombre() : null,
                lecheDisponible,
                items);
    }
}
