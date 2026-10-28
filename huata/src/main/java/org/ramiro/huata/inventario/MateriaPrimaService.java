package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NombresParecidos;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MateriaPrimaService {

    private final MateriaPrimaRepository materiaPrimaRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Inventario";

    /** Materias primas activas con su stock, en una sola consulta agregada (mismo patron que quesos). */
    public List<MateriaPrimaService.ConStock> listar() {
        return listar(false);
    }

    /**
     * Con {@code incluirInactivos}, tambien las dadas de baja: el recetario las necesita para avisar
     * "esta se reactivara" en vez de prometer un insumo nuevo que en realidad ya existe.
     */
    public List<MateriaPrimaService.ConStock> listar(boolean incluirInactivos) {
        List<MateriaPrima> materias = incluirInactivos
                ? materiaPrimaRepository.findAll(org.springframework.data.domain.Sort.by("nombre"))
                : materiaPrimaRepository.findByActivoTrueOrderByNombre();
        if (materias.isEmpty()) {
            return List.of();
        }
        var saldos = aMapaSaldos(materias);
        return materias.stream()
                .map(m -> new ConStock(m, saldos.getOrDefault(m.getId(), BigDecimal.ZERO)))
                .toList();
    }

    private java.util.Map<Long, BigDecimal> aMapaSaldos(List<MateriaPrima> materias) {
        var ids = materias.stream().map(MateriaPrima::getId).toList();
        java.util.Map<Long, BigDecimal> saldos = new java.util.HashMap<>();
        for (var fila : movimientoRepository.saldosMateriasPrimas(ids)) {
            saldos.put(fila.getItemId(), fila.getSaldo());
        }
        return saldos;
    }

    public record ConStock(MateriaPrima materiaPrima, BigDecimal stock) {}

    /**
     * Alta de un insumo. El nombre se compara sin mayusculas, tildes ni espacios de mas ("Azucar" y
     * "azúcar " son el mismo saco). Si ese nombre es de un insumo <b>dado de baja</b>, se reactiva en
     * vez de fallar: antes el sistema decia "Ya existe" de algo que ninguna pantalla mostraba, y la
     * unica salida era inventar otro nombre ("zal") y partir el kardex en dos.
     */
    @Transactional
    public MateriaPrima crear(MateriaPrimaRequest req) {
        String nombre = limpiar(req.nombre());
        MateriaPrima existente = buscarPorNombre(nombre).orElse(null);
        if (existente != null && existente.isActivo()) {
            throw new BusinessRuleException("Ya existe un insumo llamado \"" + existente.getNombre() + "\".");
        }
        if (existente != null) {
            return reactivar(existente, req.unidadMedida(), req.costoUnitarioReferencial());
        }
        exigirQueNoSeParezca(nombre, null);
        MateriaPrima m = new MateriaPrima();
        m.setNombre(nombre);
        m.setUnidadMedida(req.unidadMedida());
        m.setCostoUnitarioReferencial(req.costoUnitarioReferencial());
        MateriaPrima guardada = materiaPrimaRepository.save(m);
        auditoriaService.registrar(
                "Alta de materia prima", ENTIDAD, guardada.getNombre() + " · " + guardada.getUnidadMedida());
        return guardada;
    }

    /**
     * Para el recetario: el insumo activo con ese nombre, el dado de baja reactivado, o uno nuevo. Asi
     * escribir "sal" en una receta nunca crea un segundo "sal".
     */
    @Transactional
    public MateriaPrima obtenerOCrear(String nombre, UnidadMedida unidad) {
        MateriaPrima existente = buscarPorNombre(limpiar(nombre)).orElse(null);
        if (existente == null) {
            return crear(new MateriaPrimaRequest(nombre, unidad, null));
        }
        return existente.isActivo() ? existente : reactivar(existente, unidad, null);
    }

    /** Busca por nombre normalizado entre activos e inactivos (la tabla de insumos es corta). */
    public Optional<MateriaPrima> buscarPorNombre(String nombre) {
        String clave = clave(nombre);
        return materiaPrimaRepository.findAll().stream()
                .filter(m -> clave(m.getNombre()).equals(clave))
                // Si quedaron duplicados de antes de esta regla, manda el activo.
                .min(java.util.Comparator.comparing((MateriaPrima m) -> !m.isActivo()).thenComparing(MateriaPrima::getId));
    }

    /**
     * La unidad solo cambia si el insumo nunca tuvo movimientos: con kardex, pasar de kg a g haria que
     * los saldos viejos se lean mil veces mas chicos.
     */
    private MateriaPrima reactivar(MateriaPrima m, UnidadMedida unidad, BigDecimal costo) {
        m.setActivo(true);
        if (unidad != null && unidad != m.getUnidadMedida() && !movimientoRepository.existsByMateriaPrima_Id(m.getId())) {
            m.setUnidadMedida(unidad);
        }
        if (costo != null) {
            m.setCostoUnitarioReferencial(costo);
        }
        MateriaPrima guardada = materiaPrimaRepository.save(m);
        auditoriaService.registrar("Reactivacion de materia prima", ENTIDAD, guardada.getNombre());
        return guardada;
    }

    @Transactional
    public MateriaPrima actualizar(Long id, MateriaPrimaRequest req) {
        MateriaPrima m = materiaPrimaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Materia prima", id));
        String nombre = limpiar(req.nombre());
        buscarPorNombre(nombre)
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new BusinessRuleException(otra.isActivo()
                            ? "Ya existe un insumo llamado \"" + otra.getNombre() + "\"."
                            : "El nombre \"" + otra.getNombre() + "\" es de un insumo dado de baja. "
                                    + "Para volver a usarlo, crealo con ese nombre y se reactiva con su historial.");
                });
        exigirQueNoSeParezca(nombre, id);
        if (req.unidadMedida() != m.getUnidadMedida() && movimientoRepository.existsByMateriaPrima_Id(id)) {
            throw new BusinessRuleException(
                    "No se puede cambiar la unidad de \"" + m.getNombre() + "\": ya tiene movimientos en el kardex.");
        }
        m.setNombre(nombre);
        m.setUnidadMedida(req.unidadMedida());
        m.setCostoUnitarioReferencial(req.costoUnitarioReferencial());
        MateriaPrima guardada = materiaPrimaRepository.save(m);
        auditoriaService.registrar("Edicion de materia prima", ENTIDAD, guardada.getNombre());
        return guardada;
    }

    /**
     * Regla del cliente (2026-10-27): no se crea un insumo casi igual a otro ("zal" junto a "sal"),
     * activo o dado de baja. Se bloquea siempre; si de verdad es otro, se le pone un nombre que se
     * distinga.
     */
    private void exigirQueNoSeParezca(String nombre, Long idPropio) {
        materiaPrimaRepository.findAll().stream()
                .filter(m -> !m.getId().equals(idPropio))
                .filter(m -> NombresParecidos.parecidos(nombre, m.getNombre()))
                .findFirst()
                .ifPresent(m -> {
                    throw new BusinessRuleException("\"" + nombre + "\" es casi igual a \"" + m.getNombre()
                            + "\", que ya existe" + (m.isActivo() ? "" : " (dado de baja)") + ". Si es el mismo,"
                            + " usa \"" + m.getNombre() + "\"; si es otro, ponle un nombre que se distinga.");
                });
    }

    static String limpiar(String nombre) {
        return NombresParecidos.limpiar(nombre);
    }

    public static String clave(String nombre) {
        return NombresParecidos.clave(nombre);
    }

    @Transactional
    public void inactivar(Long id) {
        MateriaPrima m = materiaPrimaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Materia prima", id));
        if (m.isLecheAcopio()) {
            // Sin ella, lo que planta recibe de las rutas no tendria donde entrar.
            throw new BusinessRuleException(
                    "La leche del acopio no se puede dar de baja: es donde entra la leche que llega de las rutas.");
        }
        m.setActivo(false);
        materiaPrimaRepository.save(m);
        auditoriaService.registrar("Baja de materia prima", ENTIDAD, m.getNombre());
    }

    public BigDecimal saldoDe(Long materiaPrimaId) {
        return movimientoRepository.saldoMateriaPrima(materiaPrimaId);
    }

    // ---- Kardex ----
    //
    // Mapean a DTO dentro de la transaccion: MovimientoInventario.materiaPrima/.loteQueso son
    // @ManyToOne LAZY, y el response lee su nombre/codigo (no solo el id), lo que exige inicializar
    // el proxy. Devolver la entidad para mapearla en el controller revienta con
    // LazyInitializationException porque la sesion ya se cerro.

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> kardexDeMateriaPrima(Long materiaPrimaId, Pageable pageable) {
        return movimientoRepository.findByMateriaPrima_IdOrderByFechaDescIdDesc(materiaPrimaId, pageable)
                .map(MovimientoInventarioResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> kardexDeLote(Long loteQuesoId, Pageable pageable) {
        return movimientoRepository.findByLoteQueso_IdOrderByFechaDescIdDesc(loteQuesoId, pageable)
                .map(MovimientoInventarioResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<MovimientoInventarioResponse> kardex(Pageable pageable) {
        return movimientoRepository.findAllByOrderByFechaDescIdDesc(pageable).map(MovimientoInventarioResponse::from);
    }
}
