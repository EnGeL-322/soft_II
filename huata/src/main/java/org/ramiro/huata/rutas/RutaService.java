package org.ramiro.huata.rutas;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maneja Ruta, CentroAcopio y AsignacionRuta: son un mismo bloque funcional ("Rutas y movilidad"). */
@Service
@RequiredArgsConstructor
public class RutaService {

    private final RutaRepository rutaRepository;
    private final CentroAcopioRepository centroAcopioRepository;
    private final AsignacionRutaRepository asignacionRutaRepository;
    private final AcopiadorRepository acopiadorRepository;

    // ---- Rutas ----

    public PageResponse<Ruta> buscarRutas(String q, Pageable pageable) {
        Specification<Ruta> spec = SearchSpecifications.<Ruta>textSearch(q, "nombre", "zona").and(SearchSpecifications.onlyActive());
        return PageResponse.from(rutaRepository.findAll(spec, pageable));
    }

    public Ruta obtenerRuta(Long id) {
        return rutaRepository.findById(id).filter(Ruta::isActivo).orElseThrow(() -> NotFoundException.of("Ruta", id));
    }

    @Transactional
    public Ruta crearRuta(RutaRequest req) {
        Ruta ruta = new Ruta();
        ruta.setNombre(req.nombre());
        ruta.setZona(req.zona());
        ruta.setDescripcion(req.descripcion());
        return rutaRepository.save(ruta);
    }

    @Transactional
    public Ruta actualizarRuta(Long id, RutaRequest req) {
        Ruta ruta = obtenerRuta(id);
        ruta.setNombre(req.nombre());
        ruta.setZona(req.zona());
        ruta.setDescripcion(req.descripcion());
        return rutaRepository.save(ruta);
    }

    @Transactional
    public void inactivarRuta(Long id) {
        Ruta ruta = obtenerRuta(id);
        ruta.setActivo(false);
        rutaRepository.save(ruta);
    }

    // ---- Centros de acopio ----

    public PageResponse<CentroAcopio> buscarCentros(String q, Pageable pageable) {
        Specification<CentroAcopio> spec = SearchSpecifications
                .<CentroAcopio>textSearch(q, "nombre", "ubicacion")
                .and(SearchSpecifications.onlyActive());
        return PageResponse.from(centroAcopioRepository.findAll(spec, pageable));
    }

    @Transactional
    public CentroAcopio crearCentro(CentroAcopioRequest req) {
        CentroAcopio centro = new CentroAcopio();
        aplicarCentro(centro, req);
        return centroAcopioRepository.save(centro);
    }

    @Transactional
    public CentroAcopio actualizarCentro(Long id, CentroAcopioRequest req) {
        CentroAcopio centro = centroAcopioRepository
                .findById(id)
                .filter(CentroAcopio::isActivo)
                .orElseThrow(() -> NotFoundException.of("Centro de acopio", id));
        aplicarCentro(centro, req);
        return centroAcopioRepository.save(centro);
    }

    @Transactional
    public void inactivarCentro(Long id) {
        CentroAcopio centro = centroAcopioRepository
                .findById(id)
                .orElseThrow(() -> NotFoundException.of("Centro de acopio", id));
        centro.setActivo(false);
        centroAcopioRepository.save(centro);
    }

    private void aplicarCentro(CentroAcopio centro, CentroAcopioRequest req) {
        centro.setNombre(req.nombre());
        centro.setUbicacion(req.ubicacion());
        if (req.rutaId() != null) {
            centro.setRuta(obtenerRuta(req.rutaId()));
        } else {
            centro.setRuta(null);
        }
    }

    // ---- Asignaciones ----

    public List<AsignacionRuta> asignacionesPorFecha(LocalDate fecha, Long acopiadorId) {
        if (acopiadorId != null) {
            return asignacionRutaRepository.findByAcopiadorIdAndFechaAndActivoTrue(acopiadorId, fecha);
        }
        return asignacionRutaRepository.findByFechaAndActivoTrue(fecha);
    }

    @Transactional
    public AsignacionRuta asignar(AsignacionRutaRequest req) {
        Ruta ruta = obtenerRuta(req.rutaId());
        Acopiador acopiador = acopiadorRepository
                .findById(req.acopiadorId())
                .filter(Acopiador::isActivo)
                .orElseThrow(() -> NotFoundException.of("Acopiador", req.acopiadorId()));

        boolean yaAsignado = !asignacionRutaRepository
                .findByAcopiadorIdAndFechaAndActivoTrue(acopiador.getId(), req.fecha())
                .isEmpty();
        if (yaAsignado) {
            throw new BusinessRuleException("El acopiador ya tiene una ruta asignada ese dia");
        }

        AsignacionRuta asignacion = new AsignacionRuta();
        asignacion.setRuta(ruta);
        asignacion.setAcopiador(acopiador);
        asignacion.setFecha(req.fecha());
        return asignacionRutaRepository.save(asignacion);
    }
}
