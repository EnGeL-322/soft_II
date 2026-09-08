package org.ramiro.huata.usuarios;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tabla "Roles" de Configuración: rol / responsabilidad / acceso (igual que en el prototipo). */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RolController {

    private final RolRepository rolRepository;

    @GetMapping
    public List<RolResponse> listar() {
        return rolRepository.findAll().stream().map(RolResponse::from).toList();
    }
}
