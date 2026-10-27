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
import org.ramiro.huata.liquidaciones.TarifaLeche;
import org.ramiro.huata.liquidaciones.TarifaLecheRepository;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final DecisionRecepcionService decisionRecepcionService;
    private final TarifaLecheRepository tarifaLecheRepository;
    private final RetiroPorMalaCalidadService retiroPorMalaCalidad;
    private final org.ramiro.huata.recojo.RecepcionLecheService recepcionLeche;
    private final org.ramiro.huata.notificaciones.NotificacionService notificaciones;

    /** Quien figura como aprobador cuando el analisis cumple el estandar (V50). */
    public static final String APROBADO_POR_SISTEMA = "SISTEMA";

    /** Nombre del parametro de calidad que define la banda de densidad aceptable. */
    private static final String PARAMETRO_DENSIDAD = "Densidad";

    private static final ZoneId ZONA_HORARIA = ZoneId.of("America/Lima");

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
        if (req.recojoId() == null) {
            return registrarControlSinRecojo(req);
        }
        Recojo recojo = recojoRepository.findById(req.recojoId()).orElseThrow(() -> NotFoundException.of("Recojo", req.recojoId()));
        if (controlCalidadRepository.findByRecojoId(recojo.getId()).isPresent()) {
            throw new BusinessRuleException("Este recojo ya tiene un control de calidad registrado");
        }
        ControlCalidad control = new ControlCalidad();
        control.setRecojo(recojo);
        control.setProductor(recojo.getProductor());
        control.setFechaHora(req.fechaHora() != null ? req.fechaHora() : java.time.Instant.now());
        // Una entrega directa se analiza en planta: no hay otro lugar posible. En un recojo de ruta
        // el lugar lo declara quien registra (campo o planta); si no lo declara, queda sin dato.
        control.setLugarAnalisis(recojo.getFuente() == org.ramiro.huata.recojo.FuenteRecojo.PLANTA
                ? LugarAnalisis.PLANTA
                : req.lugarAnalisis());
        aplicarControl(control, req);
        return despuesDeGuardar(guardarConEstandar(control, true));
    }

    /**
     * El testeador analiza en casa del proveedor ANTES de que pase el acopiador (V47): todavia no
     * hay recojo. El control queda del proveedor y del dia, y RecojoService lo vincula cuando el
     * acopiador registra el recojo.
     *
     * <p>Si ese proveedor ya tiene un control de campo de ese dia sin vincular, se actualiza en vez
     * de crear otro: un reenvio de la cola offline o un segundo analisis del mismo dia no deben dejar
     * dos controles compitiendo por el mismo recojo.
     */
    private ControlCalidad registrarControlSinRecojo(ControlCalidadRequest req) {
        if (req.productorId() == null) {
            throw new BusinessRuleException("Indica el recojo o el proveedor cuya leche se analizó.");
        }
        Productor productor = productorRepository
                .findById(req.productorId())
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));
        LugarAnalisis lugar = req.lugarAnalisis() != null ? req.lugarAnalisis() : LugarAnalisis.CAMPO;
        java.time.Instant fechaHora = req.fechaHora() != null ? req.fechaHora() : java.time.Instant.now();
        LocalDate dia = fechaHora.atZone(ZONA_HORARIA).toLocalDate();
        ControlCalidad control = controlCalidadRepository
                .findFirstByProductor_IdAndRecojoIsNullAndLugarAnalisisAndFechaHoraBetweenOrderByFechaHoraDesc(
                        productor.getId(),
                        lugar,
                        dia.atStartOfDay(ZONA_HORARIA).toInstant(),
                        dia.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant())
                .orElseGet(ControlCalidad::new);
        boolean nuevo = control.getId() == null;
        if (!nuevo) quitarAprobacion(control);
        control.setProductor(productor);
        control.setFechaHora(fechaHora);
        control.setLugarAnalisis(lugar);
        aplicarControl(control, req);
        ControlCalidad guardado = guardarConEstandar(control, true);
        auditoriaService.registrar(
                nuevo ? "Registro" : "Actualizacion",
                "Calidad",
                productor.getCodigo() + " · control de " + lugar.name().toLowerCase() + " sin recojo · "
                        + guardado.getResultado());
        return guardado;
    }

    /**
     * Vincula al recojo recien registrado el control de campo de ese proveedor y dia que esperaba
     * al acopiador. Lo llama RecojoService en la misma transaccion. Devuelve el control vinculado,
     * si habia uno.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public java.util.Optional<ControlCalidad> vincularControlDeCampo(Recojo recojo) {
        if (controlCalidadRepository.findByRecojoId(recojo.getId()).isPresent()) {
            return java.util.Optional.empty();
        }
        LocalDate dia = recojo.getFechaHora().atZone(ZONA_HORARIA).toLocalDate();
        return controlCalidadRepository
                .findFirstByProductor_IdAndRecojoIsNullAndLugarAnalisisAndFechaHoraBetweenOrderByFechaHoraDesc(
                        recojo.getProductor().getId(),
                        LugarAnalisis.CAMPO,
                        dia.atStartOfDay(ZONA_HORARIA).toInstant(),
                        dia.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant())
                .map(control -> {
                    control.setRecojo(recojo);
                    return despuesDeGuardar(controlCalidadRepository.save(control));
                });
    }

    // ---- Aprobacion en la web (V48) ----

    /**
     * Aprueba un analisis registrado con la app. La aprobacion no cambia el resultado ni el precio:
     * deja constancia de que otra persona lo reviso. Aprobar dos veces no es un error, pero tampoco
     * cambia quien aprobo primero.
     */
    @Transactional
    public ControlCalidad aprobar(Long id, String usuario) {
        ControlCalidad control = controlCalidadRepository.findById(id).orElseThrow(() -> NotFoundException.of("Control de calidad", id));
        if (control.getAprobadoEn() != null) {
            return control;
        }
        control.setAprobadoPor(usuario);
        control.setAprobadoEn(java.time.Instant.now());
        ControlCalidad guardado = controlCalidadRepository.save(control);
        auditoriaService.registrar("Aprobacion", "Calidad",
                guardado.proveedor().getCodigo() + " · analisis " + guardado.getId() + " · " + guardado.getResultado());
        return guardado;
    }

    /** Analisis sin aprobar de los ultimos {@code dias} dias (incluido hoy). */
    public List<ControlCalidad> porAprobar(int dias) {
        LocalDate hoy = LocalDate.now(ZONA_HORARIA);
        int rango = Math.max(1, Math.min(dias, 60));
        return controlCalidadRepository.findByAprobadoEnIsNullAndFechaHoraBetweenOrderByFechaHoraDesc(
                hoy.minusDays(rango - 1L).atStartOfDay(ZONA_HORARIA).toInstant(),
                hoy.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant());
    }

    /**
     * Analisis de los ultimos {@code dias} dias (incluido hoy), aprobados o no: los que se aprobaron
     * solos (SISTEMA) tambien se ven, para que la app no muestre una pantalla vacia cuando todo cumple.
     */
    public List<ControlCalidad> recientes(int dias) {
        LocalDate hoy = LocalDate.now(ZONA_HORARIA);
        int rango = Math.max(1, Math.min(dias, 60));
        return controlCalidadRepository.findByFechaHoraBetweenOrderByFechaHoraDesc(
                hoy.minusDays(rango - 1L).atStartOfDay(ZONA_HORARIA).toInstant(),
                hoy.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant());
    }

    /**
     * Evalua el analisis contra el estandar (V50) y lo guarda. Si lo cumple se aprueba solo, como
     * SISTEMA; si no, queda pendiente con su advertencia y se avisa (una vez por advertencia nueva).
     * Una aprobacion manual ya hecha sobre los mismos valores no se pisa.
     */
    private ControlCalidad guardarConEstandar(ControlCalidad control, boolean avisar) {
        String antes = control.getAdvertencia();
        List<String> fallas = EstandarCalidad.fallas(
                control, parametroCalidadRepository.findAll().stream().filter(ParametroCalidad::isActivo).toList());
        if (fallas.isEmpty()) {
            control.setAdvertencia(null);
            if (control.getAprobadoEn() == null) {
                control.setAprobadoPor(APROBADO_POR_SISTEMA);
                control.setAprobadoEn(java.time.Instant.now());
            }
        } else {
            String advertencia = String.join(" · ", fallas);
            control.setAdvertencia(advertencia.length() > 500 ? advertencia.substring(0, 497) + "..." : advertencia);
            if (APROBADO_POR_SISTEMA.equals(control.getAprobadoPor())) quitarAprobacion(control);
        }
        ControlCalidad guardado = controlCalidadRepository.save(control);
        if (avisar && guardado.getAdvertencia() != null && !guardado.getAdvertencia().equals(antes)) {
            notificaciones.crear(
                    null,
                    "Análisis de calidad con advertencia",
                    guardado.proveedor().getCodigo() + " · " + guardado.getAdvertencia()
                            + ". Requiere aprobación en la app.",
                    "CONTROL_CALIDAD",
                    guardado.getId());
        }
        return guardado;
    }

    /**
     * Evalua contra el estandar los analisis pendientes que nunca se evaluaron (anteriores a V50).
     * Se corre al arrancar; no avisa, para no llenar de notificaciones con datos historicos.
     */
    @Transactional
    public int evaluarPendientesSinEvaluar() {
        List<ControlCalidad> pendientes = controlCalidadRepository.findByAprobadoEnIsNullAndAdvertenciaIsNull();
        pendientes.forEach(c -> guardarConEstandar(c, false));
        return pendientes.size();
    }

    private static void quitarAprobacion(ControlCalidad control) {
        control.setAprobadoPor(null);
        control.setAprobadoEn(null);
    }

    /** Controles sin recojo de los ultimos {@code dias} dias (incluido hoy). */
    public List<ControlCalidad> controlesSinRecojo(int dias) {
        LocalDate hoy = LocalDate.now(ZONA_HORARIA);
        int rango = Math.max(1, Math.min(dias, 60));
        return controlCalidadRepository.findByRecojoIsNullAndFechaHoraBetweenOrderByFechaHoraDesc(
                hoy.minusDays(rango - 1L).atStartOfDay(ZONA_HORARIA).toInstant(),
                hoy.plusDays(1).atStartOfDay(ZONA_HORARIA).toInstant());
    }

    @Transactional
    public ControlCalidad actualizarControl(Long id, ControlCalidadRequest req) {
        ControlCalidad control = controlCalidadRepository.findById(id).orElseThrow(() -> NotFoundException.of("Control de calidad", id));
        aplicarControl(control, req);
        // Un analisis corregido ya no es el que se aprobo: se vuelve a evaluar contra el estandar.
        quitarAprobacion(control);
        return despuesDeGuardar(guardarConEstandar(control, true));
    }

    /**
     * Lo que el resultado de calidad cambia fuera de calidad (V33): cuanta leche apta queda en el
     * almacen (una rechazada sale sola) y si el proveedor alcanzo el limite de mala calidad y queda
     * retirado. En la misma transaccion: si algo falla, el control tampoco se guarda.
     */
    private ControlCalidad despuesDeGuardar(ControlCalidad control) {
        controlCalidadRepository.flush();
        // Sin recojo todavia no hay leche en planta ni entrega que contar: el inventario y el retiro
        // se recalculan cuando el control se vincule a su recojo.
        if (control.getRecojo() == null) return control;
        recepcionLeche.alCambiar(control.getRecojo());
        retiroPorMalaCalidad.evaluar(control.getRecojo().getProductor());
        return control;
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
        control.setAguaAnadida(req.aguaAnadida());
        control.setLugol(req.lugol());
        control.setOlor(req.olor());
        control.setAspecto(req.aspecto());
        control.setMotivoRechazo(req.motivoRechazo());

        // La calidad la decide el sistema (regla 7.1), no quien registra la muestra. Antes se
        // guardaba req.resultado() tal cual, de modo que era posible declarar apta una leche con
        // agua confirmada. Lo que el operador declara entra como una senal mas —puede empeorar la
        // decision, nunca mejorarla— y el resultado sale de aqui.
        DecisionRecepcionService.Decision decision = decisionRecepcionService.decidir(
                new DecisionRecepcionService.Muestra(
                        req.aguaAnadida(), req.lugol(), req.resultado(), req.densidad() == null ? 0 : req.densidad()),
                control.proveedor() != null && control.proveedor().isExcelencia(),
                parametrosVigentes());
        control.setResultado(decision.resultado());
        if (!decision.aceptada() && (req.motivoRechazo() == null || req.motivoRechazo().isBlank())) {
            // Si nadie escribio el motivo, queda el que calculo la regla: una recepcion rechazada
            // sin motivo es indefendible frente al proveedor al que se le devuelve la leche.
            control.setMotivoRechazo(decision.motivo());
        }
    }

    /**
     * Banda de densidad y tarifas vigentes hoy.
     *
     * <p>La densidad se lee de parametros_calidad y los precios de la tarifa vigente, es decir: son
     * configurables sin desplegar, que es lo que pide 7.4 ("parametrizable"). Si falta el parametro
     * de densidad se usa una banda imposible (0, 0) que nunca dispara baja calidad: es preferible no
     * penalizar a nadie a inventar limites que la planta no aprobo.
     */
    private DecisionRecepcionService.ParametrosDecision parametrosVigentes() {
        ParametroCalidad densidad =
                parametroCalidadRepository.findByNombreIgnoreCase(PARAMETRO_DENSIDAD).orElse(null);
        TarifaLeche tarifa = tarifaLecheRepository
                .findTopByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(LocalDate.now(ZONA_HORARIA))
                .orElse(null);

        double min = densidad != null && densidad.getValorMin() != null ? densidad.getValorMin() : 0;
        double max = densidad != null && densidad.getValorMax() != null ? densidad.getValorMax() : 0;
        double normal = tarifa != null && tarifa.getPrecioNormal() != null ? tarifa.getPrecioNormal() : 0;
        double reducido = tarifa != null && tarifa.getPrecioObservada() != null ? tarifa.getPrecioObservada() : 0;
        Double excelencia = tarifa != null ? tarifa.getPrecioExcelencia() : null;
        return new DecisionRecepcionService.ParametrosDecision(min, max, normal, reducido, excelencia);
    }

    /** Historial de calidad de un proveedor (boton "Historial de calidad" del UI/UX). */
    public List<ControlCalidad> historialPorProductor(Long productorId) {
        // Incluye los controles de campo que todavia esperan su recojo (V47).
        return controlCalidadRepository.findByProductor_IdOrderByFechaHoraDesc(productorId);
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
