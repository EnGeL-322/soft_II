package org.ramiro.huata.compras;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProveedorCompraService {

    private final ProveedorCompraRepository repository;
    private final AuditoriaService auditoriaService;

    private static final String ENTIDAD = "Compras";

    public List<ProveedorCompra> listar() {
        return repository.findByActivoTrueOrderByNombre();
    }

    @Transactional
    public ProveedorCompra crear(ProveedorCompraRequest req) {
        ProveedorCompra p = new ProveedorCompra();
        p.setNombre(req.nombre().trim());
        p.setContacto(req.contacto());
        p.setDocumento(req.documento());
        p.setObservaciones(req.observaciones());
        // Provisional unico para no chocar con otra alta simultanea; el definitivo se deriva del id
        // (mismo motivo que LoteQueso.codigo: contar filas del anio y sumar uno tiene una carrera).
        p.setCodigo("TMP-" + UUID.randomUUID().toString().substring(0, 12));
        ProveedorCompra guardado = repository.save(p);
        guardado.setCodigo("PC-" + String.format("%04d", guardado.getId()));
        guardado = repository.save(guardado);
        auditoriaService.registrar("Alta de proveedor de compra", ENTIDAD, guardado.getCodigo() + " · " + guardado.getNombre());
        return guardado;
    }

    @Transactional
    public ProveedorCompra actualizar(Long id, ProveedorCompraRequest req) {
        ProveedorCompra p = repository.findById(id).orElseThrow(() -> NotFoundException.of("Proveedor de compra", id));
        p.setNombre(req.nombre().trim());
        p.setContacto(req.contacto());
        p.setDocumento(req.documento());
        p.setObservaciones(req.observaciones());
        ProveedorCompra guardado = repository.save(p);
        auditoriaService.registrar("Edicion de proveedor de compra", ENTIDAD, guardado.getCodigo());
        return guardado;
    }

    @Transactional
    public void inactivar(Long id) {
        ProveedorCompra p = repository.findById(id).orElseThrow(() -> NotFoundException.of("Proveedor de compra", id));
        p.setActivo(false);
        repository.save(p);
        auditoriaService.registrar("Baja de proveedor de compra", ENTIDAD, p.getCodigo());
    }
}
