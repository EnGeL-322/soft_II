package org.ramiro.huata.notificaciones;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService notificacionService;

    @GetMapping
    public List<NotificacionResponse> mias(Authentication authentication) {
        return notificacionService.paraUsuarioActual(authentication.getName()).stream()
                .map(NotificacionResponse::from)
                .toList();
    }

    @PatchMapping("/{id}/leer")
    public NotificacionResponse marcarLeida(@PathVariable Long id) {
        return NotificacionResponse.from(notificacionService.marcarLeida(id));
    }
}
