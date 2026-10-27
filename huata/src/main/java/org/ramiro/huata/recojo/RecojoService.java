package org.ramiro.huata.recojo;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.ramiro.huata.calidad.ControlCalidad;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.GeoUtils;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.parametros.ParametroNegocioRepository;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorService;
import org.ramiro.huata.productores.ProductorUbicacion;
import org.ramiro.huata.productores.ProductorUbicacionService;
import org.ramiro.huata.rutas.PuntoEstablecido;
import org.ramiro.huata.rutas.PuntoEstablecidoRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.ramiro.huata.rutas.ZonaPoligonoRepository;
import org.ramiro.huata.rutas.ZonaPoligonoPuntoRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecojoService {

    private static final ZoneId ZONA_HUATA = ZoneId.of("America/Lima");

    /** RECOJO_DISTANCIA_MAXIMA_METROS por defecto si no hay parametro configurado o es invalido. */
    private static final double DISTANCIA_MAXIMA_METROS_DEFECTO = 200.0;

    private final org.ramiro.huata.ingesta.InstrumentoService instrumentoService;
    private final RecojoRepository recojoRepository;
    private final ProductorRepository productorRepository;
    private final ProductorService productorService;
    private final ProductorUbicacionService productorUbicacionService;
    private final AcopiadorRepository acopiadorRepository;
    private final RutaRepository rutaRepository;
    private final ControlCalidadRepository controlCalidadRepository;
    private final ParametroNegocioRepository parametroNegocioRepository;
    /** Geocerca encendida o apagada (V57): se apaga para demostraciones sin perder la regla. */
    private final org.ramiro.huata.parametros.ReglasAcopioService reglasAcopio;
    private final PuntoEstablecidoRepository puntoEstablecidoRepository;
    private final ZonaPoligonoRepository zonaPoligonoRepository;
    private final ZonaPoligonoPuntoRepository zonaPoligonoPuntoRepository;

    /**
     * Toda escritura sobre una recepcion queda trazada: los litros registrados son la base de la
     * liquidacion del proveedor, y una cancelacion le quita dinero. Sin autor registrado no hay
     * forma de responder "quien anulo esta entrega" (DESARROLLO.md, criterio de salida Fase 1).
     */
    private final AuditoriaService auditoriaService;

    /**
     * Avisa a "Conciliacion planta" que la fila de esa ruta cambio, para que el acopio que se acaba
     * de registrar en el camino aparezca en la web al instante (RF-53/RF-54). Se publica como evento
     * y no llamando al WebSocket aqui para que el envio ocurra despues del commit y para que este
     * servicio no dependa del transporte: ver {@link AcopioTiempoRealPublisher}.
     */
    private final ApplicationEventPublisher eventos;
    private final RecepcionLecheService recepcionLeche;
    private final org.ramiro.huata.calidad.CalidadService calidadService;

    private static final String ENTIDAD = "Acopio";

    public PageResponse<RecojoResponse> buscar(
            Long productorId,
            Long rutaId,
            Long acopiadorId,
            EstadoRecojo estado,
            Instant desde,
            Instant hasta,
            Pageable pageable) {
        return buscar(productorId, rutaId, acopiadorId, estado, desde, hasta, null, null, null, pageable);
    }

    /**
     * Igual que la búsqueda base, más los filtros de la pantalla de acopio: texto (proveedor o
     * acopiador), fuente y resultado de calidad. Se aplican en la consulta, sobre todos los
     * registros, para que la paginación cuente y muestre solo lo filtrado.
     *
     * @param resultado ACEPTADA, OBSERVADA, RECHAZADA o PENDIENTE (sin control de calidad todavía).
     */
    public PageResponse<RecojoResponse> buscar(
            Long productorId,
            Long rutaId,
            Long acopiadorId,
            EstadoRecojo estado,
            Instant desde,
            Instant hasta,
            String texto,
            FuenteRecojo fuente,
            String resultado,
            Pageable pageable) {
        Specification<Recojo> spec = SearchSpecifications.<Recojo>alwaysTrue()
                .and(productorId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("productor").get("id"), productorId))
                .and(rutaId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("ruta").get("id"), rutaId))
                .and(acopiadorId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("acopiador").get("id"), acopiadorId))
                .and(SearchSpecifications.equalsIfPresent("estado", estado))
                .and(desde == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("fechaHora"), desde))
                .and(hasta == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("fechaHora"), hasta))
                .and(SearchSpecifications.equalsIfPresent("fuente", fuente))
                .and(porTexto(texto))
                .and(porResultadoCalidad(resultado));
        return PageResponse.from(recojoRepository.findAll(spec, pageable)).map(this::aRespuesta);
    }

    /** "Mis entregas" para la app del proveedor (7.1): resuelve el productor desde su login. */
    public PageResponse<RecojoResponse> misRecojos(String username, Instant desde, Instant hasta, Pageable pageable) {
        Productor productor = productorService.obtenerPorUsuario(username);
        return buscar(productor.getId(), null, null, null, desde, hasta, pageable);
    }

    @Transactional(readOnly = true)
    public ReporteAcopiadorResponse reportePropioAcopiador(
            String username, PeriodoReporteAcopiador periodo, LocalDate fecha) {
        Acopiador acopiador = acopiadorRepository
                .findByUsuario_UsernameIgnoreCase(username)
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> NotFoundException.of("Acopiador para el usuario", username));
        LocalDate referencia = fecha != null ? fecha : LocalDate.now(ZONA_HUATA);
        LocalDate desde = periodo == PeriodoReporteAcopiador.SEMANA
                ? referencia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : referencia;
        LocalDate hasta = periodo == PeriodoReporteAcopiador.SEMANA ? desde.plusDays(6) : desde;
        return construirReporte(acopiador, desde, hasta, recojosDelPeriodo(desde, hasta).stream()
                .filter(r -> r.getAcopiador() != null && r.getAcopiador().getId().equals(acopiador.getId()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<ReporteAcopiadorResponse> reporteAcopiadores(LocalDate desde, LocalDate hasta, Long acopiadorId) {
        if (hasta.isBefore(desde)) {
            throw new BusinessRuleException("La fecha final no puede ser anterior a la fecha inicial");
        }
        if (hasta.isAfter(desde.plusYears(1))) {
            throw new BusinessRuleException("El reporte no puede superar un ano");
        }
        List<Acopiador> acopiadores = acopiadorId == null
                ? acopiadorRepository.findByActivoTrueOrderByNombresAsc()
                : List.of(acopiadorRepository
                        .findById(acopiadorId)
                        .filter(Acopiador::isActivo)
                        .orElseThrow(() -> NotFoundException.of("Acopiador", acopiadorId)));
        List<Recojo> recojos = recojosDelPeriodo(desde, hasta);
        return acopiadores.stream()
                .map(a -> construirReporte(a, desde, hasta, recojos.stream()
                        .filter(r -> r.getAcopiador() != null && r.getAcopiador().getId().equals(a.getId()))
                        .toList()))
                .toList();
    }

    private List<Recojo> recojosDelPeriodo(LocalDate desde, LocalDate hasta) {
        Instant inicio = desde.atStartOfDay(ZONA_HUATA).toInstant();
        Instant finExclusivo = hasta.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant();
        return recojoRepository.findByFechaHoraGreaterThanEqualAndFechaHoraLessThanAndEstadoNot(
                inicio, finExclusivo, EstadoRecojo.CANCELLED);
    }

    private ReporteAcopiadorResponse construirReporte(
            Acopiador acopiador, LocalDate desde, LocalDate hasta, List<Recojo> recojos) {
        List<ReporteAcopiadorDia> dias = recojos.stream()
                .collect(java.util.stream.Collectors.groupingBy(r -> r.getFechaHora().atZone(ZONA_HUATA).toLocalDate()))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(java.util.Map.Entry<LocalDate, List<Recojo>>::getKey).reversed())
                .map(e -> resumenDia(e.getKey(), e.getValue()))
                .toList();
        var ruta = acopiador.getRuta();
        return new ReporteAcopiadorResponse(
                acopiador.getId(),
                acopiador.getNombres() + " " + acopiador.getApellidos(),
                ruta != null ? ruta.getId() : null,
                ruta != null ? ruta.getNombre() : null,
                ruta != null ? ruta.getZona() : null,
                desde,
                hasta,
                recojos.stream().mapToDouble(Recojo::getLitros).sum(),
                recojos.size(),
                recojos.stream().map(r -> r.getProductor().getId()).distinct().count(),
                recojos.stream().filter(r -> r.getEstadoVisita() == EstadoVisita.NO_ENTREGADO).count(),
                dias);
    }

    private ReporteAcopiadorDia resumenDia(LocalDate fecha, List<Recojo> recojos) {
        return new ReporteAcopiadorDia(
                fecha,
                recojos.stream().mapToDouble(Recojo::getLitros).sum(),
                recojos.size(),
                recojos.stream().map(r -> r.getProductor().getId()).distinct().count(),
                recojos.stream().filter(r -> r.getEstadoVisita() == EstadoVisita.NO_ENTREGADO).count());
    }

    public Recojo obtener(Long id) {
        return recojoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Recojo", id));
    }

    public RecojoResponse obtenerRespuesta(Long id) {
        return aRespuesta(obtener(id));
    }

    /** Nombre o código del proveedor, o nombre del acopiador; sin distinguir mayúsculas. */
    private static Specification<Recojo> porTexto(String texto) {
        if (texto == null || texto.isBlank()) return SearchSpecifications.alwaysTrue();
        String patron = "%" + texto.trim().toLowerCase() + "%";
        return (root, cq, cb) -> {
            var productor = root.join("productor");
            var acopiador = root.join("acopiador", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(cb.concat(cb.concat(productor.get("nombres"), " "), productor.get("apellidos"))), patron),
                    cb.like(cb.lower(productor.get("codigo")), patron),
                    cb.like(cb.lower(cb.concat(cb.concat(acopiador.get("nombres"), " "), acopiador.get("apellidos"))), patron));
        };
    }

    /** Resultado del control de calidad del recojo; PENDIENTE = todavía sin control. */
    private static Specification<Recojo> porResultadoCalidad(String resultado) {
        if (resultado == null || resultado.isBlank()) return SearchSpecifications.alwaysTrue();
        boolean pendiente = "PENDIENTE".equalsIgnoreCase(resultado.trim());
        ResultadoCalidad buscado;
        try {
            buscado = pendiente ? null : ResultadoCalidad.valueOf(resultado.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Resultado de calidad no válido: " + resultado);
        }
        return (root, cq, cb) -> {
            Subquery<Long> control = cq.subquery(Long.class);
            Root<ControlCalidad> cc = control.from(ControlCalidad.class);
            control.select(cc.get("id"));
            if (pendiente) {
                control.where(cb.equal(cc.get("recojo"), root));
                return cb.not(cb.exists(control));
            }
            control.where(cb.equal(cc.get("recojo"), root), cb.equal(cc.get("resultado"), buscado));
            return cb.exists(control);
        };
    }

    private RecojoResponse aRespuesta(Recojo r) {
        ResultadoCalidad resultado = controlCalidadRepository
                .findByRecojoId(r.getId())
                .map(c -> c.getResultado())
                .orElse(null);
        return RecojoResponse.from(r, resultado);
    }

    /**
     * Registra un recojo. Si llega un clientUuid ya conocido, devuelve el existente en vez de
     * duplicarlo: es el mismo criterio de idempotencia que usara la sincronizacion movil (Fase 2).
     */
    @Transactional
    public Recojo registrar(RecojoRequest req) {
        String clientUuid = (req.clientUuid() == null || req.clientUuid().isBlank())
                ? UUID.randomUUID().toString()
                : req.clientUuid();

        var existente = recojoRepository.findByClientUuid(clientUuid);
        if (existente.isPresent()) {
            // Reintento del cliente movil sobre un recojo ya guardado: no se audita, porque no hubo
            // escritura. Auditarlo llenaria la bitacora de altas que nunca ocurrieron.
            return existente.get();
        }

        Productor productor = productorRepository
                .findById(req.productorId())
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));
        // 7.3: se valida aqui y no en el formulario. Ocultar el boton en la interfaz no impide que
        // llegue la peticion.
        productorService.exigirQuePuedeEntregar(productor);

        // 6.3: blanco/verde/rojo. NO_ENTREGADO exige motivo (regla del negocio, no de la anotacion,
        // porque el minimo de litros depende de este valor); ACOPIADO (o sin estado, para no romper
        // el flujo de planta que no manda estadoVisita) exige litros > 0: un recojo "acopiado" con
        // cero litros es un formulario mal llenado, no una entrega.
        EstadoVisita estadoVisita = req.estadoVisita();
        if (estadoVisita == EstadoVisita.NO_ENTREGADO) {
            if (req.motivoNoEntrega() == null || req.motivoNoEntrega().isBlank()) {
                throw new BusinessRuleException("Marcar \"No entregado\" exige un motivo.");
            }
        } else if (req.litros() <= 0) {
            throw new BusinessRuleException("Un recojo acopiado debe registrar litros mayores a cero.");
        }

        // Una entrega diaria por proveedor: un NO_ENTREGADO (litros 0, solo deja constancia de la
        // visita) no cuenta contra esta regla, pero dos entregas reales el mismo dia calendario
        // duplicarian litros e importe en la planilla y en la liquidacion.
        if (estadoVisita != EstadoVisita.NO_ENTREGADO) {
            LocalDate diaEntrega = req.fechaHora().atZone(ZONA_HUATA).toLocalDate();
            boolean yaEntregoEseDia = recojoRepository
                    .findByProductorIdAndFechaHoraBetweenAndEstadoNotOrderByFechaHoraAsc(
                            productor.getId(),
                            diaEntrega.atStartOfDay(ZONA_HUATA).toInstant(),
                            diaEntrega.plusDays(1).atStartOfDay(ZONA_HUATA).toInstant(),
                            EstadoRecojo.CANCELLED)
                    .stream()
                    .anyMatch(r -> r.getEstadoVisita() != EstadoVisita.NO_ENTREGADO);
            if (yaEntregoEseDia) {
                throw new BusinessRuleException("El proveedor " + productor.getCodigo()
                        + " ya tiene una entrega registrada el " + diaEntrega + ". Un proveedor entrega leche una "
                        + "sola vez al día; corrija el recojo existente en vez de crear otro.");
            }
        }

        // Quien recoge en ruta es siempre un acopiador; la entrega directa la trae el propio proveedor
        // a planta, sin acopiador (confirmado con el cliente, 2026-10-14). Sin esta regla un recojo
        // "de ruta" registrado desde planta no tiene a quien atribuirse en la conciliacion.
        FuenteRecojo fuente = req.fuente() != null ? req.fuente() : FuenteRecojo.RUTA;
        if (fuente == FuenteRecojo.RUTA && req.acopiadorId() == null) {
            throw new BusinessRuleException(
                    "Un recojo en ruta lo registra el acopiador que lo hizo. Si el proveedor trajo la leche a planta, "
                            + "regístralo como entrega directa.");
        }
        if (fuente == FuenteRecojo.PLANTA && req.acopiadorId() != null) {
            throw new BusinessRuleException(
                    "Una entrega directa en planta la trae el propio proveedor: no lleva acopiador.");
        }

        Recojo recojo = new Recojo();
        recojo.setClientUuid(clientUuid);
        recojo.setProductor(productor);
        recojo.setFechaHora(req.fechaHora());
        recojo.setLitros(req.litros());
        recojo.setTemperatura(req.temperatura());
        recojo.setEstado(EstadoRecojo.SYNCED);
        recojo.setFuente(fuente);
        recojo.setNumeroEnvases(req.numeroEnvases());
        recojo.setCapacidadReferenciaL(req.capacidadReferenciaL());
        recojo.setResponsableRecepcion(req.responsableRecepcion());
        recojo.setLitrosAppMovil(req.litrosAppMovil());
        recojo.setEstadoVisita(estadoVisita);
        recojo.setMotivoNoEntrega(estadoVisita == EstadoVisita.NO_ENTREGADO ? req.motivoNoEntrega().trim() : null);
        recojo.setLatitud(req.latitud());
        recojo.setLongitud(req.longitud());
        calcularDistanciaAlProveedor(recojo, productor, req.latitud(), req.longitud());

        if (req.acopiadorId() != null) {
            Acopiador acopiador = acopiadorRepository
                    .findById(req.acopiadorId())
                    .orElseThrow(() -> NotFoundException.of("Acopiador", req.acopiadorId()));
            recojo.setAcopiador(acopiador);
        }
        if (req.rutaId() != null) {
            Ruta ruta = rutaRepository.findById(req.rutaId()).orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()));
            recojo.setRuta(ruta);
        } else if (productor.getRuta() != null) {
            recojo.setRuta(productor.getRuta());
        }

        exigirDentroDeSuZona(recojo, req.latitud(), req.longitud());

        Recojo guardado = recojoRepository.save(recojo);
        // V31: la leche que el proveedor lleva el mismo a planta ya esta en planta: entra al
        // inventario ahora. La de ruta entra cuando planta anota el total recibido de la ruta.
        if (guardado.getFuente() == FuenteRecojo.PLANTA) {
            recepcionLeche.sincronizarEntregaDirecta(guardado);
        } else {
            // El testeador pudo analizar esta leche en campo antes de que pasara el acopiador (V47).
            calidadService.vincularControlDeCampo(guardado);
        }
        // El ticket del analizador, si el operador uso "Llamar datos". Va aparte del recojo porque
        // es una entidad propia (medicion_instrumento, V7).
        instrumentoService.guardarMedicion(guardado, req.medicion());
        auditoriaService.registrar("Registro de recepcion", ENTIDAD, describir(guardado));
        avisarConciliacion(guardado);
        return guardado;
    }

    @Transactional
    public Recojo cancelar(Long id, RecojoCancelRequest req) {
        Recojo recojo = obtener(id);
        if (recojo.getEstado() == EstadoRecojo.CANCELLED) {
            throw new BusinessRuleException("El recojo ya esta cancelado");
        }
        recojo.setEstado(EstadoRecojo.CANCELLED);
        recojo.setMotivoCancelacion(req.motivo());
        Recojo guardado = recojoRepository.save(recojo);
        // Si ya habia entrado al almacen (entrega directa, o leche no apta de una ruta recibida), se
        // recalcula: la entrada no se borra, sale la diferencia como ajuste.
        recepcionLeche.alCambiar(guardado);
        auditoriaService.registrar(
                "Cancelacion de recepcion",
                ENTIDAD,
                describir(guardado)
                        + (req.motivo() == null || req.motivo().isBlank()
                                ? " \u00b7 sin motivo declarado"
                                : " \u00b7 " + req.motivo()));
        // Cancelar baja el total recolectado de la ruta: la conciliacion tiene que reflejarlo igual
        // que un alta, o quedaria mostrando leche que ya se anulo.
        avisarConciliacion(guardado);
        return guardado;
    }

    /**
     * Corrige el recojo existente en vez de crear uno nuevo (pedido del cliente, 2026-09-24). Un
     * proveedor entrega leche una sola vez al dia (regla de {@link #registrar}), asi que reintentar
     * "registrar de nuevo" para arreglar un litros mal tecleado siempre choca contra esa regla; esta
     * es la via oficial para corregirlo. Exige motivo -- no hay correccion silenciosa de un numero que
     * alimenta la liquidacion -- y el recojo queda {@code observado} para que calidad/administracion
     * revise por que cambio, sin bloquear el pago mientras tanto (mismo criterio que gpsFueraDeRango).
     */
    @Transactional
    public Recojo corregir(Long id, RecojoCorreccionRequest req) {
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw new BusinessRuleException("Corregir un recojo exige indicar el motivo.");
        }
        Recojo recojo = obtener(id);
        if (recojo.getEstado() == EstadoRecojo.CANCELLED) {
            throw new BusinessRuleException("No se puede corregir un recojo cancelado.");
        }
        double litrosAnteriores = recojo.getLitros();
        recojo.setLitros(req.litros());
        recojo.setObservado(true);
        recojo.setMotivoObservacion(req.motivo().trim());
        Recojo guardado = recojoRepository.save(recojo);
        auditoriaService.registrar(
                "Correccion de recepcion",
                ENTIDAD,
                describir(guardado) + " · de " + litrosAnteriores + " L a " + req.litros() + " L · "
                        + req.motivo().trim());
        // El total recolectado de la ruta cambio: la conciliacion tiene que reflejar el litros
        // corregido, no el original.
        avisarConciliacion(guardado);
        return guardado;
    }

    /**
     * La fecha de la fila es el dia <b>local</b> del recojo, el mismo criterio con que
     * {@link ConciliacionRutaService} corta la jornada. Tomarla en UTC mandaria el aviso a una fila
     * distinta de la que suma ese recojo.
     */
    private void avisarConciliacion(Recojo recojo) {
        if (recojo.getRuta() == null) {
            return;
        }
        eventos.publishEvent(new AcopioCambioEvent(
                recojo.getFechaHora().atZone(ZONA_HUATA).toLocalDate(),
                recojo.getRuta().getId(),
                EventoAcopioTiempoReal.RECOJO));
    }

    /** Identifica la recepcion por proveedor y litros, que es lo que se audita: cuanto y a quien. */
    private static String describir(Recojo recojo) {
        Productor productor = recojo.getProductor();
        String proveedor = productor == null ? "sin proveedor" : productor.getCodigo();
        String base = "Recepcion #" + recojo.getId() + " \u00b7 " + proveedor + " \u00b7 " + recojo.getLitros() + " L";
        if (recojo.isGpsFueraDeRango()) {
            StringBuilder detalle = new StringBuilder(" \u00b7 GPS fuera de rango (revisar)");
            if (recojo.getDistanciaProveedorM() != null) {
                detalle.append(" \u00b7 a ").append(Math.round(recojo.getDistanciaProveedorM())).append("m del proveedor");
            }
            if (recojo.getDistanciaPuntoEstablecidoM() != null) {
                detalle.append(" \u00b7 a ").append(Math.round(recojo.getDistanciaPuntoEstablecidoM()))
                        .append("m del punto establecido");
            }
            base += detalle;
        }
        return base;
    }

    /**
     * Distancia entre el GPS del acopiador y (a) la ultima ubicacion conocida del proveedor y (b), si
     * existe, el punto establecido asociado a ese proveedor (V43, Opcion B1 acordada con el cliente
     * 2026-09-30: el punto establecido complementa la regla existente, no la reemplaza). El GPS de
     * campo no es confiable (pedido del cliente, 2026-09-24), asi que esto NO rechaza el recojo \u2014 solo
     * lo marca para que calidad/administracion lo revise, igual con cualquiera de las dos distancias.
     * Rechazarlo borraria una entrega real (y ya pagable) por una lectura de GPS mala o un pin mal
     * puesto. El bloqueo real en el momento ("no puede registrar") lo hace la app movil cuando esta en
     * linea y puede avisar al acopiador de inmediato; ver huata_app/pintar... RutaDiaScreen._abrirFormulario.
     *
     * <p>Sin GPS del acopiador, o sin el dato con el que comparar (ubicacion del proveedor / punto
     * establecido), el campo correspondiente queda en null y no en 0: no se sugiere una cercania que
     * no se verifico.
     */
    private void calcularDistanciaAlProveedor(Recojo recojo, Productor productor, Double lat, Double lon) {
        if (lat == null || lon == null) {
            return;
        }
        double maximo = distanciaMaximaMetros();
        boolean fueraDeRango = false;

        ProductorUbicacion ubicacion =
                productorUbicacionService.ultimasPorProductor(List.of(productor.getId())).get(productor.getId());
        if (ubicacion != null) {
            double distanciaM = GeoUtils.distanciaMetros(lat, lon, ubicacion.getLatitud(), ubicacion.getLongitud());
            recojo.setDistanciaProveedorM(distanciaM);
            fueraDeRango = distanciaM >= maximo;
        }

        PuntoEstablecido punto = puntoEstablecidoRepository
                .findByProveedor_IdAndActivoTrue(productor.getId())
                .stream()
                .findFirst()
                .orElse(null);
        if (punto != null) {
            double distanciaPuntoM = GeoUtils.distanciaMetros(lat, lon, punto.getLatitud(), punto.getLongitud());
            recojo.setDistanciaPuntoEstablecidoM(distanciaPuntoM);
            fueraDeRango = fueraDeRango || distanciaPuntoM >= maximo;
        }

        recojo.setGpsFueraDeRango(fueraDeRango);
    }

    /**
     * Geocerca de zona: un acopiador no puede registrar leche fuera del poligono de la zona de su
     * ruta. Solo aplica a una entrega real de ruta con GPS; una recepcion en planta, una visita "no
     * entregado" o un cliente sin coordenadas no se juzgan por zona, y una zona sin poligono
     * dibujado no se puede exigir. La app del acopiador avisa antes; esto es la ultima linea, porque
     * ocultar el boton no impide que la peticion llegue (mismo criterio que 7.3).
     */
    private void exigirDentroDeSuZona(Recojo recojo, Double latitud, Double longitud) {
        if (!reglasAcopio.vigentes().exigirGeocerca()) {
            return;
        }
        if (recojo.getFuente() != FuenteRecojo.RUTA
                || recojo.getEstadoVisita() == EstadoVisita.NO_ENTREGADO
                || latitud == null || longitud == null) {
            return;
        }
        Ruta ruta = recojo.getRuta();
        if (ruta == null || ruta.getZona() == null || ruta.getZona().isBlank()) {
            return;
        }
        var poligono = zonaPoligonoRepository.findByZonaIgnoreCase(ruta.getZona().trim()).orElse(null);
        if (poligono == null) {
            return;
        }
        List<double[]> vertices = zonaPoligonoPuntoRepository
                .findByZonaPoligono_IdOrderBySecuenciaAsc(poligono.getId()).stream()
                .map(p -> GeoUtils.punto(p.getLatitud(), p.getLongitud()))
                .toList();
        if (vertices.size() < 3) {
            return;
        }
        if (!GeoUtils.dentroDePoligono(latitud, longitud, vertices)) {
            throw new BusinessRuleException("Estas fuera de la zona " + ruta.getZona()
                    + ". Debes estar dentro de tu zona para registrar leche.");
        }
    }

    /** RECOJO_DISTANCIA_MAXIMA_METROS configurado en Parametros, o 200m (RN-25) si no hay ninguno valido. */
    private double distanciaMaximaMetros() {
        return parametroNegocioRepository
                .findByClave("RECOJO_DISTANCIA_MAXIMA_METROS")
                .map(p -> {
                    try {
                        return Double.parseDouble(p.getValor().trim());
                    } catch (NumberFormatException e) {
                        return DISTANCIA_MAXIMA_METROS_DEFECTO;
                    }
                })
                .orElse(DISTANCIA_MAXIMA_METROS_DEFECTO);
    }
}
