package org.ramiro.huata.productores;

import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.rutas.CentroAcopioRepository;
import org.ramiro.huata.rutas.RutaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductorService {

    private final ProductorRepository productorRepository;
    private final RutaRepository rutaRepository;
    private final CentroAcopioRepository centroAcopioRepository;

    public PageResponse<Productor> buscar(String q, EstadoProductor estado, Long rutaId, Pageable pageable) {
        Specification<Productor> spec = SearchSpecifications.<Productor>textSearch(q, "codigo", "nombres", "apellidos")
                .and(SearchSpecifications.onlyActive())
                .and(SearchSpecifications.equalsIfPresent("estado", estado))
                .and(rutaId == null
                        ? SearchSpecifications.alwaysTrue()
                        : (root, cq, cb) -> cb.equal(root.get("ruta").get("id"), rutaId));
        return PageResponse.from(productorRepository.findAll(spec, pageable));
    }

    public Productor obtener(Long id) {
        return productorRepository
                .findById(id)
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", id));
    }

    @Transactional
    public Productor crear(ProductorRequest req) {
        if (productorRepository.existsByCodigoIgnoreCase(req.codigo())) {
            throw new BusinessRuleException("Ya existe un productor con el codigo " + req.codigo());
        }
        Productor productor = new Productor();
        productor.setCodigo(req.codigo());
        aplicarDatos(productor, req);
        return productorRepository.save(productor);
    }

    @Transactional
    public Productor actualizar(Long id, ProductorRequest req) {
        Productor productor = obtener(id);
        aplicarDatos(productor, req);
        return productorRepository.save(productor);
    }

    @Transactional
    public Productor cambiarEstado(Long id, ProductorEstadoRequest req) {
        Productor productor = obtener(id);
        productor.setEstado(req.estado());
        if (req.motivo() != null && !req.motivo().isBlank()) {
            String notaPrevia = productor.getNotas() == null ? "" : productor.getNotas() + "\n";
            productor.setNotas(notaPrevia + "[" + req.estado() + "] " + req.motivo());
        }
        return productorRepository.save(productor);
    }

    @Transactional
    public void inactivar(Long id) {
        Productor productor = obtener(id);
        productor.setActivo(false);
        productorRepository.save(productor);
    }

    private void aplicarDatos(Productor productor, ProductorRequest req) {
        productor.setNombres(req.nombres());
        productor.setApellidos(req.apellidos());
        productor.setDocumentoIdentidad(req.documentoIdentidad());
        productor.setTelefono(req.telefono());
        productor.setNotas(req.notas());
        productor.setRuta(req.rutaId() != null
                ? rutaRepository.findById(req.rutaId()).orElseThrow(() -> NotFoundException.of("Ruta", req.rutaId()))
                : null);
        productor.setCentroAcopio(req.centroAcopioId() != null
                ? centroAcopioRepository
                        .findById(req.centroAcopioId())
                        .orElseThrow(() -> NotFoundException.of("Centro de acopio", req.centroAcopioId()))
                : null);
    }
}
