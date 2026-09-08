package org.ramiro.huata.quesos;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuesoService {

    private final QuesoRepository quesoRepository;
    private final AuditoriaService auditoriaService;

    public List<Queso> listar(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate inicio = desde != null ? desde : fin.minusMonths(1);
        return quesoRepository.findByFechaBetweenOrderByFechaDesc(inicio, fin);
    }

    @Transactional
    public Queso crear(QuesoRequest req) {
        Queso queso = new Queso();
        aplicar(queso, req);
        Queso guardado = quesoRepository.save(queso);
        auditoriaService.registrar("Registro", "Quesos", req.fuente() + " · " + req.producidosKg() + " kg");
        return guardado;
    }

    @Transactional
    public Queso actualizar(Long id, QuesoRequest req) {
        Queso queso = quesoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Registro de quesos", id));
        aplicar(queso, req);
        return quesoRepository.save(queso);
    }

    @Transactional
    public void eliminar(Long id) {
        Queso queso = quesoRepository.findById(id).orElseThrow(() -> NotFoundException.of("Registro de quesos", id));
        queso.setActivo(false);
        quesoRepository.save(queso);
    }

    private void aplicar(Queso queso, QuesoRequest req) {
        queso.setFecha(req.fecha());
        queso.setFuente(req.fuente());
        queso.setProducidosKg(req.producidosKg());
        queso.setVendidosKg(req.vendidosKg() != null ? req.vendidosKg() : 0.0);
        queso.setTotalVenta(req.totalVenta());
        queso.setObservaciones(req.observaciones());
    }
}
