package org.ramiro.huata.calidad;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CalidadService {

    private final ParametroCalidadRepository parametroCalidadRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final IncidenciaCalidadRepository incidenciaCalidadRepository;
    private final EvidenciaCalidadRepository evidenciaCalidadRepository;
    private final RecojoRepository recojoRepository;
    private final ProductorRepository productorRepository;
    private final AuditoriaService auditoriaService;

    // ---- Parametros configurables ----

    public List<ParametroCalidad> listarParametros() {
        return parametroCalidadRepository.findAll();
    }

    @Transactional
    public ParametroCalidad guardarParametro(Long id, ParametroCalidadRequest req) {
        ParametroCalidad parametro = id == null
                ? new ParametroCalidad()
                : parametroCalidadRepository.findById(id).orElseThrow(() -> NotFoundException.of("Parametro de calidad", id));
        parametro.setNombre(req.nombre());
        parametro.setValorMin(req.valorMin());
        parametro.setValorMax(req.valorMax());
        parametro.setUnidad(req.unidad());
        parametro.setDescripcion(req.descripcion());
        return parametroCalidadRepository.save(parametro);
    }

    // ---- Controles de calidad ----

    @Transactional
    public ControlCalidad registrarControl(ControlCalidadRequest req) {
        Recojo recojo = recojoRepository.findById(req.recojoId()).orElseThrow(() -> NotFoundException.of("Recojo", req.recojoId()));
        if (controlCalidadRepository.findByRecojoId(recojo.getId()).isPresent()) {
            throw new BusinessRuleException("Este recojo ya tiene un control de calidad registrado");
        }
        ControlCalidad control = new ControlCalidad();
        control.setRecojo(recojo);
        aplicarControl(control, req);
        return controlCalidadRepository.save(control);
    }

    @Transactional
    public ControlCalidad actualizarControl(Long id, ControlCalidadRequest req) {
        ControlCalidad control = controlCalidadRepository.findById(id).orElseThrow(() -> NotFoundException.of("Control de calidad", id));
        aplicarControl(control, req);
        return controlCalidadRepository.save(control);
    }

    private void aplicarControl(ControlCalidad control, ControlCalidadRequest req) {
        control.setDensidad(req.densidad());
        control.setPuntoCongelacion(req.puntoCongelacion());
        control.setAcidez(req.acidez());
        control.setTemperatura(req.temperatura());
        control.setGrasa(req.grasa());
        control.setSolidosNoGrasos(req.solidosNoGrasos());
        control.setSolidosTotales(req.solidosTotales());
        control.setRevisionVisual(req.revisionVisual());
        control.setResultado(req.resultado());
        control.setEquipoUtilizado(req.equipoUtilizado());
        control.setResponsable(req.responsable());
    }

    /** Historial de calidad de un proveedor (boton "Historial de calidad" del UI/UX). */
    public List<ControlCalidad> historialPorProductor(Long productorId) {
        return controlCalidadRepository.findByRecojo_ProductorIdOrderByCreatedAtDesc(productorId);
    }

    // ---- Incidencias / seguimiento ----

    @Transactional
    public IncidenciaCalidad registrarIncidencia(IncidenciaCalidadRequest req) {
        Productor productor = productorRepository.findById(req.productorId()).orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));
        IncidenciaCalidad incidencia = new IncidenciaCalidad();
        incidencia.setProductor(productor);
        incidencia.setTipo(req.tipo());
        incidencia.setDescripcion(req.descripcion());
        incidencia.setFecha(req.fecha() != null ? req.fecha() : java.time.Instant.now());
        if (req.recojoId() != null) {
            Recojo recojo = recojoRepository.findById(req.recojoId()).orElseThrow(() -> NotFoundException.of("Recojo", req.recojoId()));
            incidencia.setRecojo(recojo);
        }
        IncidenciaCalidad guardada = incidenciaCalidadRepository.save(incidencia);
        auditoriaService.registrar("Registro", "Calidad", productor.getCodigo() + " · " + req.tipo());
        return guardada;
    }

    @Transactional
    public IncidenciaCalidad cambiarEstadoIncidencia(Long id, CambiarEstadoIncidenciaRequest req) {
        IncidenciaCalidad incidencia = incidenciaCalidadRepository.findById(id).orElseThrow(() -> NotFoundException.of("Incidencia", id));
        incidencia.setEstado(req.estado());
        return incidenciaCalidadRepository.save(incidencia);
    }

    /** Seguimiento de incidencias/adulteraciones por proveedor. */
    public List<IncidenciaCalidad> seguimientoPorProductor(Long productorId) {
        return incidenciaCalidadRepository.findByProductorIdOrderByFechaDesc(productorId);
    }

    public List<IncidenciaCalidad> listarIncidencias() {
        return incidenciaCalidadRepository.findAll();
    }

    // ---- Evidencia ----

    @Transactional
    public EvidenciaCalidad registrarEvidencia(EvidenciaCalidadRequest req) {
        EvidenciaCalidad evidencia = new EvidenciaCalidad();
        if (req.controlCalidadId() != null) {
            evidencia.setControlCalidad(controlCalidadRepository
                    .findById(req.controlCalidadId())
                    .orElseThrow(() -> NotFoundException.of("Control de calidad", req.controlCalidadId())));
        }
        if (req.incidenciaId() != null) {
            evidencia.setIncidencia(incidenciaCalidadRepository
                    .findById(req.incidenciaId())
                    .orElseThrow(() -> NotFoundException.of("Incidencia", req.incidenciaId())));
        }
        evidencia.setTipo(req.tipo());
        evidencia.setUrlArchivo(req.urlArchivo());
        evidencia.setEstado(EstadoEvidencia.UPLOADED);
        return evidenciaCalidadRepository.save(evidencia);
    }

    public List<EvidenciaCalidad> evidenciaDeControl(Long controlCalidadId) {
        return evidenciaCalidadRepository.findByControlCalidadId(controlCalidadId);
    }

    public List<EvidenciaCalidad> evidenciaDeIncidencia(Long incidenciaId) {
        return evidenciaCalidadRepository.findByIncidenciaId(incidenciaId);
    }
}
