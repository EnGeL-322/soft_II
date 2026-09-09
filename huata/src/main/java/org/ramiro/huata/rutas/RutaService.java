package org.ramiro.huata.rutas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
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
    private final RecojoRepository recojoRepository;

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
        asignacion.setMovilidad(req.movilidad());
        return asignacionRutaRepository.save(asignacion);
    }

    @Transactional
    public AsignacionRuta actualizarAsignacion(Long id, AsignacionActualizarRequest req) {
        AsignacionRuta asignacion = asignacionRutaRepository.findById(id).orElseThrow(() -> NotFoundException.of("Asignación", id));
        if (req.movilidad() != null) {
            asignacion.setMovilidad(req.movilidad());
        }
        if (req.observacion() != null) {
            asignacion.setObservacion(req.observacion());
        }
        return asignacionRutaRepository.save(asignacion);
    }

    /**
     * "Plan operativo de recojo": para cada ruta activa, quién la tiene asignada ese día (si
     * alguien) y lo que realmente se recogió — reemplaza la tabla DEMO del prototipo (movilidad y
     * testeo inventados) por datos reales de Ruta + AsignacionRuta + Recojo.
     */
    public List<PlanRutaDia> planOperativo(LocalDate fecha) {
        List<Ruta> rutas = rutaRepository.findAll(SearchSpecifications.<Ruta>onlyActive());
        List<AsignacionRuta> asignaciones = asignacionRutaRepository.findByFechaAndActivoTrue(fecha);

        Instant inicio = fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fin = fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<Recojo> recojosDelDia = recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);

        return rutas.stream()
                .map(ruta -> {
                    AsignacionRuta asignacion = asignaciones.stream()
                            .filter(a -> a.getRuta().getId().equals(ruta.getId()))
                            .findFirst()
                            .orElse(null);

                    List<Recojo> recojosRuta = recojosDelDia.stream()
                            .filter(r -> r.getRuta() != null && r.getRuta().getId().equals(ruta.getId()))
                            .toList();

                    double litros = recojosRuta.stream().mapToDouble(Recojo::getLitros).sum();
                    Instant ultimoRegistro =
                            recojosRuta.stream().map(Recojo::getFechaHora).max(Comparator.naturalOrder()).orElse(null);

                    EstadoPlanRuta estado = !recojosRuta.isEmpty()
                            ? EstadoPlanRuta.CON_REGISTROS
                            : (asignacion != null ? EstadoPlanRuta.ASIGNADA : EstadoPlanRuta.SIN_ASIGNAR);

                    return new PlanRutaDia(
                            ruta.getId(),
                            ruta.getNombre(),
                            ruta.getZona(),
                            asignacion != null ? asignacion.getId() : null,
                            asignacion != null ? asignacion.getAcopiador().getId() : null,
                            asignacion != null
                                    ? asignacion.getAcopiador().getNombres() + " " + asignacion.getAcopiador().getApellidos()
                                    : null,
                            asignacion != null ? asignacion.getMovilidad() : null,
                            asignacion != null ? asignacion.getObservacion() : null,
                            estado,
                            litros,
                            recojosRuta.size(),
                            ultimoRegistro);
                })
                .sorted(Comparator.comparing(PlanRutaDia::rutaNombre))
                .toList();
    }
}
