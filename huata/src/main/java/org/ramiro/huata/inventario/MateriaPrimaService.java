package org.ramiro.huata.inventario;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
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
        List<MateriaPrima> materias = materiaPrimaRepository.findByActivoTrueOrderByNombre();
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

    @Transactional
    public MateriaPrima crear(MateriaPrimaRequest req) {
        if (materiaPrimaRepository.existsByNombreIgnoreCase(req.nombre().trim())) {
            throw new BusinessRuleException("Ya existe una materia prima llamada \"" + req.nombre().trim() + "\".");
        }
        MateriaPrima m = new MateriaPrima();
        m.setNombre(req.nombre().trim());
        m.setUnidadMedida(req.unidadMedida());
        m.setCostoUnitarioReferencial(req.costoUnitarioReferencial());
        MateriaPrima guardada = materiaPrimaRepository.save(m);
        auditoriaService.registrar(
                "Alta de materia prima", ENTIDAD, guardada.getNombre() + " · " + guardada.getUnidadMedida());
        return guardada;
    }

    @Transactional
    public MateriaPrima actualizar(Long id, MateriaPrimaRequest req) {
        MateriaPrima m = materiaPrimaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Materia prima", id));
        m.setNombre(req.nombre().trim());
        m.setUnidadMedida(req.unidadMedida());
        m.setCostoUnitarioReferencial(req.costoUnitarioReferencial());
        MateriaPrima guardada = materiaPrimaRepository.save(m);
        auditoriaService.registrar("Edicion de materia prima", ENTIDAD, guardada.getNombre());
        return guardada;
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
