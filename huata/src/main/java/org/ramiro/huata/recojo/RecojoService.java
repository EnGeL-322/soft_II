package org.ramiro.huata.recojo;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
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

    public PageResponse<Recojo> buscar(
            Long productorId, Long rutaId, EstadoRecojo estado, Instant desde, Instant hasta, Pageable pageable) {
        Specification<Recojo> spec = SearchSpecifications.<Recojo>alwaysTrue()
                .and(productorId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("productor").get("id"), productorId))
                .and(rutaId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("ruta").get("id"), rutaId))
                .and(SearchSpecifications.equalsIfPresent("estado", estado))
                .and(desde == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("fechaHora"), desde))
                .and(hasta == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.lessThanOrEqualTo(root.get("fechaHora"), hasta));
        return PageResponse.from(recojoRepository.findAll(spec, pageable));
    }

    public Recojo obtener(Long id) {
        return recojoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Recojo", id));
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
