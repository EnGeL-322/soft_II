package org.ramiro.huata.auditoria;

import lombok.RequiredArgsConstructor;
import org.ramiro.huata.common.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Se inyecta en los modulos cuyas acciones deben quedar trazadas (login, calidad, exportaciones, quesos, liquidaciones). */
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final AuditoriaRepository auditoriaRepository;

    @Transactional
    public void registrar(String accion, String entidad, String detalle) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String actor = (auth != null && auth.isAuthenticated()) ? auth.getName() : "sistema";

        RegistroAuditoria registro = new RegistroAuditoria();
        registro.setActor(actor);
        registro.setAccion(accion);
        registro.setEntidad(entidad);
        registro.setDetalle(detalle);
        auditoriaRepository.save(registro);
    }

    public PageResponse<RegistroAuditoria> listar(Pageable pageable) {
        return PageResponse.from(auditoriaRepository.findAllByOrderByFechaDesc(pageable));
    }
}
