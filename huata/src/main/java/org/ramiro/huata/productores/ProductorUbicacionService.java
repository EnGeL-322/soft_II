package org.ramiro.huata.productores;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ubicacion del proveedor (2.6): su vivienda o su punto actual, guardado desde la app. */
@Service
@RequiredArgsConstructor
public class ProductorUbicacionService {

    private final ProductorUbicacionRepository repository;
    private final ProductorRepository productorRepository;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Proveedores";

    /** Usa la misma validación y el mismo historial que el formulario de ubicación. */
    @Transactional
    public ProductorUbicacion registrarPuntoDeRecojo(Long productorId, double latitud, double longitud) {
        return registrar(productorId, new ProductorUbicacionRequest(latitud, longitud, null, TipoUbicacion.ACTUAL));
    }

    public List<ProductorUbicacion> historial(Long productorId) {
        return repository.findByProductor_IdOrderByFechaDesc(productorId);
    }

    /**
     * Ultima ubicacion conocida de cada proveedor, para pintarlos en el mapa. Se resuelve en una
     * sola consulta para los N proveedores de la ruta y no una por proveedor (N+1).
     */
    public Map<Long, ProductorUbicacion> ultimasPorProductor(List<Long> productorIds) {
        if (productorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProductorUbicacion> ultimas = new HashMap<>();
        // La consulta ya viene de la mas reciente a la mas antigua: la primera de cada proveedor gana.
        for (ProductorUbicacion ubicacion : repository.findByProductor_IdInOrderByFechaDescIdDesc(productorIds)) {
            ultimas.putIfAbsent(ubicacion.getProductor().getId(), ubicacion);
        }
        return ultimas;
    }

    /**
     * Vivienda simulada para demostraciones (ver {@link UbicacionSimuladaService}). La bitácora lo
     * dice con todas sus letras para que nadie la tome por una ubicación tomada en campo.
     */
    @Transactional
    public ProductorUbicacion registrarSimulada(Productor productor, double latitud, double longitud) {
        ProductorUbicacion ubicacion = new ProductorUbicacion();
        ubicacion.setProductor(productor);
        ubicacion.setLatitud(latitud);
        ubicacion.setLongitud(longitud);
        ubicacion.setTipo(TipoUbicacion.CASA);
        ubicacion.setFecha(LocalDate.now());
        ProductorUbicacion guardada = repository.save(ubicacion);

        auditoriaService.registrar(
                "Ubicación SIMULADA de proveedor",
                ENTIDAD,
                productor.getCodigo() + " · vivienda al azar dentro de " + productor.getZonaPrincipal() + " (demostración)");
        return guardada;
    }

    @Transactional
    public ProductorUbicacion registrar(Long productorId, ProductorUbicacionRequest req) {
        Productor productor = productorRepository
                .findById(productorId)
                .filter(Productor::isActivo)
                .orElseThrow(() -> NotFoundException.of("Productor", productorId));

        ProductorUbicacion ubicacion = new ProductorUbicacion();
        ubicacion.setProductor(productor);
        ubicacion.setLatitud(req.latitud());
        ubicacion.setLongitud(req.longitud());
        ubicacion.setPrecisionMetros(req.precisionMetros());
        ubicacion.setTipo(req.tipo());
        ubicacion.setFecha(LocalDate.now());
        ProductorUbicacion guardada = repository.save(ubicacion);

        auditoriaService.registrar(
                "Ubicación de proveedor", ENTIDAD, productor.getCodigo() + " · " + req.tipo() + " registrada");
        return guardada;
    }
}
