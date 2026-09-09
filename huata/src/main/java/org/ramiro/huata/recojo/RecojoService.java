package org.ramiro.huata.recojo;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.calidad.ControlCalidadRepository;
import org.ramiro.huata.calidad.ResultadoCalidad;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.rutas.Ruta;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecojoService {

    private final RecojoRepository recojoRepository;
    private final ProductorRepository productorRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final RutaRepository rutaRepository;
    private final ControlCalidadRepository controlCalidadRepository;

    public PageResponse<RecojoResponse> buscar(
            Long productorId,
            Long rutaId,
            Long acopiadorId,
            EstadoRecojo estado,
            Instant desde,
            Instant hasta,
            String q, FuenteRecojo fuente, String resultado,
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
                        : (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("fechaHora"), hasta));
        spec = spec.and(SearchSpecifications.equalsIfPresent("fuente", fuente));
        if (q != null && !q.isBlank()) {
            String texto = "%" + q.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> {
                var p = root.join("productor");
                var a = root.join("acopiador", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.or(cb.like(cb.lower(p.get("codigo")), texto),
                        cb.like(cb.lower(cb.concat(cb.concat(p.get("nombres"), " "), p.get("apellidos"))), texto),
                        cb.like(cb.lower(cb.concat(cb.concat(a.get("nombres"), " "), a.get("apellidos"))), texto));
            });
        }
        if (resultado != null && !resultado.isBlank()) {
            boolean pendiente = resultado.equals("PENDIENTE");
            ResultadoCalidad calidad;
            try { calidad = pendiente ? null : ResultadoCalidad.valueOf(resultado); }
            catch (IllegalArgumentException e) { throw new BusinessRuleException("Resultado de calidad inválido."); }
            spec = spec.and((root, cq, cb) -> {
                var sub = cq.subquery(Long.class);
                var c = sub.from(org.ramiro.huata.calidad.ControlCalidad.class);
                sub.select(c.get("id")).where(pendiente
                        ? cb.equal(c.get("recojo").get("id"), root.get("id"))
                        : cb.and(cb.equal(c.get("recojo").get("id"), root.get("id")), cb.equal(c.get("resultado"), calidad)));
                return pendiente ? cb.not(cb.exists(sub)) : cb.exists(sub);
            });
        }
        return PageResponse.from(recojoRepository.findAll(spec, pageable)).map(this::aRespuesta);
    }

    public Recojo obtener(Long id) {
        return recojoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Recojo", id));
    }

    public RecojoResponse obtenerRespuesta(Long id) {
        return aRespuesta(obtener(id));
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
            return existente.get();
        }

        Productor productor = productorRepository
                .findById(req.productorId())
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", req.productorId()));

        Recojo recojo = new Recojo();
        recojo.setClientUuid(clientUuid);
        recojo.setProductor(productor);
        recojo.setFechaHora(req.fechaHora());
        recojo.setLitros(req.litros());
        recojo.setTemperatura(req.temperatura());
        recojo.setEstado(EstadoRecojo.SYNCED);
        recojo.setFuente(req.fuente() != null ? req.fuente() : FuenteRecojo.RUTA);
        recojo.setNumeroEnvases(req.numeroEnvases());
        recojo.setCapacidadReferenciaL(req.capacidadReferenciaL());
        recojo.setResponsableRecepcion(req.responsableRecepcion());
        recojo.setLitrosAppMovil(req.litrosAppMovil());

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

        return recojoRepository.save(recojo);
    }

    @Transactional
    public Recojo cancelar(Long id, RecojoCancelRequest req) {
        Recojo recojo = obtener(id);
        if (recojo.getEstado() == EstadoRecojo.CANCELLED) {
            throw new BusinessRuleException("El recojo ya esta cancelado");
        }
        recojo.setEstado(EstadoRecojo.CANCELLED);
        recojo.setMotivoCancelacion(req.motivo());
        return recojoRepository.save(recojo);
    }
}
