package org.ramiro.huata.produccion;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TrabajadorProduccionService {

    private final TrabajadorProduccionRepository repository;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Produccion";

    public List<TrabajadorProduccion> listar() {
        return repository.findByActivoTrueOrderByNombre();
    }

    @Transactional
    public TrabajadorProduccion crear(TrabajadorProduccionRequest req) {
        TrabajadorProduccion t = new TrabajadorProduccion();
        t.setNombre(req.nombre().trim());
        TrabajadorProduccion guardado = repository.save(t);
        auditoriaService.registrar("Alta de trabajador de produccion", ENTIDAD, guardado.getNombre());
        return guardado;
    }

    @Transactional
    public void inactivar(Long id) {
        TrabajadorProduccion t = repository.findById(id).orElseThrow(() -> NotFoundException.of("Trabajador", id));
        t.setActivo(false);
        repository.save(t);
        auditoriaService.registrar("Baja de trabajador de produccion", ENTIDAD, t.getNombre());
    }
}
