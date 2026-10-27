package org.ramiro.huata.rutas;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.acopiadores.Acopiador;
import org.ramiro.huata.acopiadores.AcopiadorRepository;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.BusinessRuleException;
import org.ramiro.huata.common.GeoUtils;
import org.ramiro.huata.common.NotFoundException;
import org.ramiro.huata.common.PageResponse;
import org.ramiro.huata.common.SearchSpecifications;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.productores.ProductorUbicacion;
import org.ramiro.huata.productores.ProductorUbicacionService;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.ramiro.huata.recojo.Recojo;
import org.ramiro.huata.recojo.RecojoRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Maneja Ruta y AsignacionRuta: son un mismo bloque funcional ("Rutas y movilidad"). */
@Service
@RequiredArgsConstructor
public class RutaService {

    private final RutaRepository rutaRepository;
    private final AsignacionRutaRepository asignacionRutaRepository;
    private final AcopiadorRepository acopiadorRepository;
    private final RecojoRepository recojoRepository;
    private final RutaPuntoRepository rutaPuntoRepository;
    private final ProductorRepository productorRepository;
    private final ZonaPoligonoRepository zonaPoligonoRepository;
    private final ZonaPoligonoPuntoRepository zonaPoligonoPuntoRepository;
    private final ProductorUbicacionService productorUbicacionService;
    /** Copia de cada limite antes de cambiarlo o borrarlo: lo borrado por accidente se restaura (V56). */
    private final ZonaPoligonoVersionRepository zonaPoligonoVersionRepository;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;
    /** Solo demostraciones: al dibujar un limite, sus proveedores sin punto reciben uno dentro. */
    private final org.ramiro.huata.productores.UbicacionSimuladaService ubicacionSimulada;

    private static final String ENTIDAD = "Rutas y mapa";

    // ---- Rutas ----

    public PageResponse<Ruta> buscarRutas(String q, Pageable pageable) {
        Specification<Ruta> spec = SearchSpecifications.<Ruta>textSearch(q, "nombre", "zona").and(SearchSpecifications.onlyActive());
        return PageResponse.from(rutaRepository.findAll(spec, pageable));
    }

    /** Vista territorial canónica: la ruta une una zona con quienes recogen y quienes entregan. */
    @Transactional(readOnly = true)
    public List<ZonaAsignacionResponse> zonasAsignadas() {
        return rutaRepository.findByActivoTrueOrderByZonaAscNombreAsc().stream()
                .filter(ruta -> ruta.getZona() != null && !ruta.getZona().isBlank())
                .map(ruta -> new ZonaAsignacionResponse(
                        ruta.getZona().trim(),
                        ruta.getId(),
                        ruta.getNombre(),
                        ruta.getDescripcion(),
                        productorRepository.findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(ruta.getId()).stream()
                                .map(ProveedorDeZonaResponse::from)
                                .toList(),
                        acopiadorRepository.findByRuta_IdAndActivoTrueOrderByNombresAsc(ruta.getId()).stream()
                                .map(AcopiadorDeZonaResponse::from)
                                .toList()))
                .toList();
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
        Ruta guardada = rutaRepository.save(ruta);
        auditoriaService.registrar("Ruta creada", ENTIDAD, guardada.getNombre() + " · zona " + guardada.getZona());
        return guardada;
    }

    @Transactional
    public Ruta actualizarRuta(Long id, RutaRequest req) {
        Ruta ruta = obtenerRuta(id);
        String antes = ruta.getNombre() + " · zona " + ruta.getZona();
        ruta.setNombre(req.nombre());
        ruta.setZona(req.zona());
        ruta.setDescripcion(req.descripcion());
        Ruta guardada = rutaRepository.save(ruta);
        auditoriaService.registrar("Ruta editada", ENTIDAD,
                antes + " -> " + guardada.getNombre() + " · zona " + guardada.getZona());
        return guardada;
    }

    @Transactional
    public void inactivarRuta(Long id) {
        Ruta ruta = obtenerRuta(id);
        ruta.setActivo(false);
        rutaRepository.save(ruta);
        auditoriaService.registrar("Ruta dada de baja", ENTIDAD, ruta.getNombre() + " · zona " + ruta.getZona());
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

        // El dia de la jornada es el de Lima; en UTC lo recogido despues de las 19:00 caia al dia siguiente.
        ZoneId lima = ZoneId.of("America/Lima");
        Instant inicio = fecha.atStartOfDay(lima).toInstant();
        Instant fin = fecha.plusDays(1).atStartOfDay(lima).toInstant();
        List<Recojo> recojosDelDia = recojoRepository.findByFechaHoraBetweenAndEstadoNot(inicio, fin, EstadoRecojo.CANCELLED);
        // Sin asignacion del dia, responde el acopiador habitual de la ruta: es la misma regla con la
        // que la app del acopiador arma su jornada, asi web y app muestran al mismo responsable.
        List<Acopiador> habituales = acopiadorRepository.findByActivoTrueOrderByNombresAsc();

        return rutas.stream()
                .map(ruta -> {
                    AsignacionRuta asignacion = asignaciones.stream()
                            .filter(a -> a.getRuta().getId().equals(ruta.getId()))
                            .findFirst()
                            .orElse(null);
                    Acopiador responsable = asignacion != null
                            ? asignacion.getAcopiador()
                            : habituales.stream()
                                    .filter(a -> a.getRuta() != null && a.getRuta().getId().equals(ruta.getId()))
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
                            : (responsable != null ? EstadoPlanRuta.ASIGNADA : EstadoPlanRuta.SIN_ASIGNAR);

                    return new PlanRutaDia(
                            ruta.getId(),
                            ruta.getNombre(),
                            ruta.getZona(),
                            asignacion != null ? asignacion.getId() : null,
                            responsable != null ? responsable.getId() : null,
                            responsable != null ? responsable.getNombres() + " " + responsable.getApellidos() : null,
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

    // ---- Geometria de la ruta (2.1.3-2.1.5) ----

    /**
     * Se valida que la ruta exista igual que en {@link #proveedoresDeRuta}: antes, pedir los puntos
     * de una ruta inexistente devolvia 200 con una lista vacia, que es indistinguible de una ruta
     * real todavia sin trazado. El cliente no podia saber si el id estaba mal o si faltaba dibujar.
     */
    public List<RutaPunto> puntosDeRuta(Long rutaId) {
        obtenerRuta(rutaId);
        return rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId);
    }

    /**
     * Reemplaza TODA la geometria de la ruta de una vez: borrar e insertar de nuevo es mas simple y
     * mas seguro que intentar diffear puntos sueltos, y es exactamente como se edita un poligono en
     * un mapa (se mueve/agrega/quita un punto y se guarda el trazo completo).
     */
    @Transactional
    public List<RutaPunto> reemplazarPuntos(Long rutaId, RutaPuntosRequest req) {
        Ruta ruta = obtenerRuta(rutaId);
        rutaPuntoRepository.deleteByRuta_Id(rutaId);
        List<RutaPunto> puntos = req.puntos().stream()
                .map(p -> {
                    RutaPunto punto = new RutaPunto();
                    punto.setRuta(ruta);
                    punto.setSecuencia(p.secuencia());
                    punto.setLatitud(p.latitud());
                    punto.setLongitud(p.longitud());
                    return punto;
                })
                .toList();
        List<RutaPunto> guardados = rutaPuntoRepository.saveAll(puntos);
        auditoriaService.registrar("Recorrido de ruta guardado", ENTIDAD,
                ruta.getNombre() + " · " + guardados.size() + " punto(s)");
        return guardados;
    }

    @Transactional
    public void eliminarPuntos(Long rutaId) {
        Ruta ruta = obtenerRuta(rutaId);
        rutaPuntoRepository.deleteByRuta_Id(rutaId);
        auditoriaService.registrar("Recorrido de ruta borrado", ENTIDAD, ruta.getNombre());
    }

    // ---- Punto de acopio dinamico + reordenamiento por cercania ----

    /**
     * Agrega un punto de acopio al trazo SIN reemplazar los demas (a diferencia de
     * {@link #reemplazarPuntos}, que reescribe todo el poligono) y reordena a los proveedores de la
     * ruta usando ese punto como origen: es el "cambio de destino" que decide el administrador, y
     * que la app movil del acopiador debe ver reflejado en el orden de visita al refrescar su ruta.
     */
    @Transactional
    public PuntoAcopioAgregadoResponse agregarPuntoAcopio(Long rutaId, PuntoAcopioRequest req) {
        Ruta ruta = obtenerRuta(rutaId);
        int siguienteSecuencia = rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId).stream()
                .mapToInt(RutaPunto::getSecuencia)
                .max()
                .orElse(0)
                + 1;
        RutaPunto punto = new RutaPunto();
        punto.setRuta(ruta);
        punto.setSecuencia(siguienteSecuencia);
        punto.setLatitud(req.latitud());
        punto.setLongitud(req.longitud());
        rutaPuntoRepository.save(punto);

        List<ProveedorDeRutaResponse> proveedores = reordenarDesde(rutaId, req.latitud(), req.longitud());
        List<RutaPuntoResponse> puntos = puntosDeRuta(rutaId).stream().map(RutaPuntoResponse::from).toList();
        return new PuntoAcopioAgregadoResponse(puntos, proveedores);
    }

    /**
     * Reordena los proveedores activos de la ruta sin tocar el trazo, partiendo de un origen
     * explicito o, si no se indica, del primer punto guardado de la ruta.
     */
    @Transactional
    public List<ProveedorDeRutaResponse> reordenarPorCercania(Long rutaId, ReordenarRutaRequest req) {
        obtenerRuta(rutaId);
        Double lat = req == null ? null : req.latitud();
        Double lon = req == null ? null : req.longitud();
        if (lat == null || lon == null) {
            RutaPunto primero = rutaPuntoRepository.findByRuta_IdOrderBySecuenciaAsc(rutaId).stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "La ruta no tiene puntos ni se indicó un origen: no hay desde dónde calcular cercanía."));
            lat = primero.getLatitud();
            lon = primero.getLongitud();
        }
        return reordenarDesde(rutaId, lat, lon);
    }

    /**
     * Vecino mas cercano (heuristico de caminos minimos para N paradas): en cada paso visita el
     * proveedor mas cercano al punto actual, no Dijkstra puro — Dijkstra resuelve la ruta mas corta
     * entre DOS puntos sobre un grafo vial, y aqui el problema es en que orden visitar N proveedores
     * partiendo de un origen. Con pocas decenas de paradas por ruta el optimo exacto (TSP) no
     * compensa su costo computacional frente a esta aproximacion, que ya reduce el recorrido frente
     * al orden manual.
     *
     * <p>Los proveedores sin ubicacion registrada no se pueden ordenar por distancia: conservan su
     * orden relativo entre si y quedan al final, en vez de tratarse como si estuvieran en (0,0), que
     * los pondria primero por error.
     */
    private List<ProveedorDeRutaResponse> reordenarDesde(Long rutaId, double origenLat, double origenLon) {
        List<Productor> proveedores =
                productorRepository.findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(rutaId);
        Map<Long, ProductorUbicacion> ubicaciones = productorUbicacionService.ultimasPorProductor(
                proveedores.stream().map(Productor::getId).toList());

        List<Productor> pendientes = proveedores.stream()
                .filter(p -> ubicaciones.get(p.getId()) != null)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        List<Productor> sinUbicacion =
                proveedores.stream().filter(p -> ubicaciones.get(p.getId()) == null).toList();

        List<Productor> ordenados = new java.util.ArrayList<>(pendientes.size());
        double actualLat = origenLat;
        double actualLon = origenLon;
        while (!pendientes.isEmpty()) {
            Productor masCercano = null;
            double mejorDistanciaKm = Double.MAX_VALUE;
            for (Productor candidato : pendientes) {
                ProductorUbicacion ubicacion = ubicaciones.get(candidato.getId());
                double distanciaKm =
                        GeoUtils.distanciaKm(actualLat, actualLon, ubicacion.getLatitud(), ubicacion.getLongitud());
                if (distanciaKm < mejorDistanciaKm) {
                    mejorDistanciaKm = distanciaKm;
                    masCercano = candidato;
                }
            }
            pendientes.remove(masCercano);
            ordenados.add(masCercano);
            ProductorUbicacion ubicacionElegida = ubicaciones.get(masCercano.getId());
            actualLat = ubicacionElegida.getLatitud();
            actualLon = ubicacionElegida.getLongitud();
        }
        ordenados.addAll(sinUbicacion);

        for (int i = 0; i < ordenados.size(); i++) {
            ordenados.get(i).setOrdenEnRuta(i + 1);
        }
        productorRepository.saveAll(ordenados);

        return ordenados.stream().map(p -> ProveedorDeRutaResponse.from(p, ubicaciones.get(p.getId()))).toList();
    }

    // ---- Proveedores de la ruta, en orden de visita (2.1.6) ----

    /** "A que proveedores va" un acopiador: los de esta ruta, ordenados. Es lo que consulta la app movil. */
    public List<ProveedorDeRutaResponse> proveedoresDeRuta(Long rutaId) {
        obtenerRuta(rutaId);
        List<Productor> proveedores =
                productorRepository.findByRuta_IdAndActivoTrueOrderByOrdenEnRutaAscNombresAsc(rutaId);
        Map<Long, ProductorUbicacion> ubicaciones = productorUbicacionService.ultimasPorProductor(
                proveedores.stream().map(Productor::getId).toList());
        return proveedores.stream()
                .map(p -> ProveedorDeRutaResponse.from(p, ubicaciones.get(p.getId())))
                .toList();
    }

    // ---- Poligono de zona (mapa de Rutas y movilidad) ----

    @Transactional(readOnly = true)
    public List<ZonaPoligonoResponse> poligonosDeZona() {
        return zonaPoligonoRepository.findByActivoTrueOrderByZonaAsc().stream().map(this::aZonaPoligonoResponse).toList();
    }

    private ZonaPoligonoResponse aZonaPoligonoResponse(ZonaPoligono poligono) {
        List<ZonaPoligonoPuntoResponse> puntos = zonaPoligonoPuntoRepository
                .findByZonaPoligono_IdOrderBySecuenciaAsc(poligono.getId()).stream()
                .map(ZonaPoligonoPuntoResponse::from)
                .toList();
        return new ZonaPoligonoResponse(poligono.getId(), poligono.getZona(), poligono.getColor(), puntos);
    }

    /**
     * Crea o reemplaza el poligono completo de una zona (texto libre, sin catalogo fijo: decision
     * C-05). Igual que reemplazarPuntos: borrar e insertar de nuevo es mas simple que diffear.
     */
    @Transactional
    public ZonaPoligonoResponse guardarPoligonoDeZona(ZonaPoligonoRequest req) {
        List<double[]> vertices = req.puntos().stream()
                .sorted(Comparator.comparing(ZonaPoligonoPuntoRequest::secuencia))
                .map(p -> GeoUtils.punto(p.latitud(), p.longitud()))
                .toList();
        ZonaPoligonoResponse guardado =
                escribirPoligono(req.zona().trim(), req.color(), vertices, MOTIVO_MODIFICADO, "Límite de zona guardado");
        ubicacionSimulada.completarFaltantes(guardado.zona());
        return guardado;
    }

    @Transactional
    public void eliminarPoligonoDeZona(Long id) {
        ZonaPoligono poligono =
                zonaPoligonoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Poligono de zona", id));
        int vertices = guardarVersion(poligono, MOTIVO_ELIMINADO);
        zonaPoligonoPuntoRepository.deleteByZonaPoligono_Id(poligono.getId());
        zonaPoligonoRepository.delete(poligono);
        auditoriaService.registrar("Límite de zona eliminado", ENTIDAD,
                poligono.getZona() + " · " + vertices + " vértice(s). Se puede restaurar desde su historial.");
    }

    // ---- Historial del limite: deshacer cambios y borrados por accidente (V56) ----

    static final String MOTIVO_MODIFICADO = "MODIFICADO";
    static final String MOTIVO_ELIMINADO = "ELIMINADO";
    static final String MOTIVO_ZONA_ELIMINADA = "ZONA_ELIMINADA";
    static final String MOTIVO_RESTAURADO = "RESTAURADO";

    /** Las ultimas versiones guardadas del limite de una zona, de la mas reciente a la mas antigua. */
    @Transactional(readOnly = true)
    public List<ZonaPoligonoVersionResponse> historialDeLimite(String zona) {
        String nombre = zona == null ? "" : zona.trim();
        return zonaPoligonoVersionRepository.findTop20ByZonaIgnoreCaseOrderByCreatedAtDescIdDesc(nombre).stream()
                .map(v -> new ZonaPoligonoVersionResponse(
                        v.getId(),
                        v.getZona(),
                        v.getColor(),
                        leerVertices(v.getVertices()).stream()
                                .map(p -> new ZonaPoligonoPuntoResponse(null, p[0], p[1]))
                                .toList(),
                        v.getMotivo(),
                        v.getCreatedAt(),
                        v.getCreatedBy()))
                .toList();
    }

    /**
     * Vuelve a poner una version guardada como limite vigente de su zona. Lo que habia antes tambien
     * queda en el historial, asi que restaurar tampoco pierde nada.
     */
    @Transactional
    public ZonaPoligonoResponse restaurarLimite(Long versionId) {
        ZonaPoligonoVersion version = zonaPoligonoVersionRepository.findById(versionId)
                .orElseThrow(() -> NotFoundException.of("Version del limite", versionId));
        List<double[]> vertices = leerVertices(version.getVertices());
        if (vertices.size() < 3) {
            throw new BusinessRuleException("Esa version no tiene vertices suficientes para formar un limite.");
        }
        return escribirPoligono(
                version.getZona(), version.getColor(), vertices, MOTIVO_RESTAURADO, "Límite de zona restaurado");
    }

    /** Guarda (crea o reemplaza) el limite, tomando antes la copia del que habia. */
    private ZonaPoligonoResponse escribirPoligono(
            String zona, String color, List<double[]> vertices, String motivoCopia, String accion) {
        Optional<ZonaPoligono> existente = zonaPoligonoRepository.findByZonaIgnoreCase(zona);
        int antes = existente.map(p -> guardarVersion(p, motivoCopia)).orElse(0);
        ZonaPoligono poligono = existente.orElseGet(() -> {
            ZonaPoligono nuevo = new ZonaPoligono();
            nuevo.setZona(zona);
            return nuevo;
        });
        poligono.setColor(color);
        ZonaPoligono guardado = zonaPoligonoRepository.save(poligono);

        zonaPoligonoPuntoRepository.deleteByZonaPoligono_Id(guardado.getId());
        List<ZonaPoligonoPunto> puntos = new ArrayList<>();
        for (int i = 0; i < vertices.size(); i++) {
            ZonaPoligonoPunto punto = new ZonaPoligonoPunto();
            punto.setZonaPoligono(guardado);
            punto.setSecuencia(i + 1);
            punto.setLatitud(vertices.get(i)[0]);
            punto.setLongitud(vertices.get(i)[1]);
            puntos.add(punto);
        }
        zonaPoligonoPuntoRepository.saveAll(puntos);
        auditoriaService.registrar(accion, ENTIDAD, guardado.getZona() + " · "
                + (existente.isPresent() ? antes + " -> " : "nuevo, ") + vertices.size() + " vértice(s)");
        return aZonaPoligonoResponse(guardado);
    }

    /** Copia el limite tal como esta ahora; devuelve cuantos vertices tenia. */
    private int guardarVersion(ZonaPoligono poligono, String motivo) {
        List<double[]> vertices = zonaPoligonoPuntoRepository
                .findByZonaPoligono_IdOrderBySecuenciaAsc(poligono.getId()).stream()
                .map(p -> GeoUtils.punto(p.getLatitud(), p.getLongitud()))
                .toList();
        if (vertices.isEmpty()) return 0;
        ZonaPoligonoVersion version = new ZonaPoligonoVersion();
        version.setZona(poligono.getZona());
        version.setColor(poligono.getColor());
        version.setVertices(objectMapper.writeValueAsString(vertices));
        version.setMotivo(motivo);
        zonaPoligonoVersionRepository.save(version);
        return vertices.size();
    }

    private List<double[]> leerVertices(String json) {
        return List.of(objectMapper.readValue(json, double[][].class));
    }

    // ---- La zona como tal: renombrar y dar de baja ----

    /**
     * Renombra la zona en las dos tablas que guardan su nombre (Ruta.zona y ZonaPoligono.zona) en
     * una sola transaccion. Hacerlo con varias llamadas desde el navegador dejaria rutas apuntando
     * a un nombre y el poligono a otro si una de ellas falla.
     */
    @Transactional
    public void renombrarZona(ZonaRenombrarRequest req) {
        String actual = req.zona().trim();
        String nuevo = req.nuevoNombre().trim();
        if (nuevo.isBlank()) {
            throw new BusinessRuleException("El nuevo nombre de la zona no puede estar vacio");
        }
        List<Ruta> rutas = rutaRepository.findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(actual);
        Optional<ZonaPoligono> poligono = zonaPoligonoRepository.findByZonaIgnoreCase(actual);
        if (rutas.isEmpty() && poligono.isEmpty()) {
            throw new NotFoundException("No existe la zona " + actual);
        }
        // Cambiar solo mayusculas/minusculas del mismo nombre si es valido; fusionar dos zonas
        // distintas no lo es: dejaria dos poligonos para el mismo nombre y nadie pidio fusionar.
        if (!actual.equalsIgnoreCase(nuevo) && existeZona(nuevo)) {
            throw new BusinessRuleException("Ya existe una zona llamada " + nuevo);
        }
        rutas.forEach(ruta -> ruta.setZona(nuevo));
        rutaRepository.saveAll(rutas);
        poligono.ifPresent(p -> {
            p.setZona(nuevo);
            zonaPoligonoRepository.save(p);
        });
        auditoriaService.registrar("Zona renombrada", ENTIDAD, actual + " -> " + nuevo);
    }

    /**
     * Da de baja la zona. Solo borra su poligono: una zona "existe" mientras alguna ruta activa la
     * nombre, y vaciar Ruta.zona dejaria rutas sin zona con las que despues no se puede ni guardar
     * un acopiador (AcopiadorService exige que su ruta tenga zona). Se pide mover o dar de baja sus
     * rutas primero.
     */
    @Transactional
    public void eliminarZona(String zona) {
        String nombre = zona == null ? "" : zona.trim();
        if (nombre.isBlank()) {
            throw new BusinessRuleException("Indica la zona que quieres eliminar");
        }
        List<Ruta> rutas = rutaRepository.findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(nombre);
        if (!rutas.isEmpty()) {
            throw new BusinessRuleException("La zona " + nombre + " todavia tiene " + rutas.size()
                    + " ruta(s) activa(s). Muevelas a otra zona o dalas de baja antes de eliminarla.");
        }
        ZonaPoligono poligono = zonaPoligonoRepository
                .findByZonaIgnoreCase(nombre)
                .orElseThrow(() -> new NotFoundException("No existe la zona " + nombre));
        int vertices = guardarVersion(poligono, MOTIVO_ZONA_ELIMINADA);
        zonaPoligonoPuntoRepository.deleteByZonaPoligono_Id(poligono.getId());
        zonaPoligonoRepository.delete(poligono);
        auditoriaService.registrar("Zona eliminada", ENTIDAD,
                nombre + " · su límite (" + vertices + " vértice(s)) queda en el historial para restaurarlo.");
    }

    private boolean existeZona(String zona) {
        return zonaPoligonoRepository.findByZonaIgnoreCase(zona).isPresent()
                || !rutaRepository.findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(zona).isEmpty();
    }
}
