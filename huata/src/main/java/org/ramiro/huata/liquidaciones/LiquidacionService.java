package org.ramiro.huata.liquidaciones;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
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

    private final LiquidacionRepository liquidacionRepository;
    private final LiquidacionDetalleRepository liquidacionDetalleRepository;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final ProductorRepository productorRepository;
    private final RecojoRepository recojoRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final AuditoriaService auditoriaService;

    public PageResponse<Liquidacion> buscar(Long productorId, EstadoLiquidacion estado, Pageable pageable) {
        Specification<Liquidacion> spec = SearchSpecifications.<Liquidacion>alwaysTrue()
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

    @Transactional
    public Liquidacion calcular(LiquidacionCalcularRequest req) {
        Productor productor = productorRepository
                .findById(req.productorId())
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));

        TarifaLeche tarifa = tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now())
                .orElseThrow(() -> new BusinessRuleException(
                        "No hay una tarifa de leche configurada. Configure una en Parametros antes de calcular liquidaciones."));

        List<Recojo> recojos = recojoRepository.findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                productor.getId(),
                req.periodoInicio().atStartOfDay(ZoneOffset.UTC).toInstant(),
                req.periodoFin().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                EstadoRecojo.CANCELLED);

        Liquidacion liquidacion = liquidacionRepository
                .findByProductorIdAndPeriodoInicioAndPeriodoFin(productor.getId(), req.periodoInicio(), req.periodoFin())
                .filter(l -> l.getEstado() != EstadoLiquidacion.CLOSED)
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

        for (Recojo recojo : recojos) {
            boolean aceptada = controlCalidadRepository
                    .findByRecojoId(recojo.getId())
                    .map(control -> control.getResultado() == ResultadoCalidad.ACEPTADA)
                    .orElse(true);
            double precio = aceptada ? tarifa.getPrecioNormal() : tarifa.getPrecioObservada();
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
        Liquidacion resultado = liquidacionRepository.save(liquidacionGuardada);
        auditoriaService.registrar(
                "Cálculo", "Liquidación", productor.getCodigo() + " · " + req.periodoInicio() + " a " + req.periodoFin());
        return resultado;
    }

    @Transactional
    public Liquidacion cambiarEstado(Long id, CambiarEstadoLiquidacionRequest req) {
        Liquidacion liquidacion = obtener(id);
        liquidacion.setEstado(req.estado());
        return liquidacionRepository.save(liquidacion);
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
        tarifa.setVigenteDesde(req.vigenteDesde());
        tarifa.setVigenteHasta(req.vigenteHasta());
        return tarifaLecheRepository.save(tarifa);
    }
}
