package org.ramiro.huata.mensajeria;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mensajeria")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MensajeriaController {
    private final MensajeriaService service;
    @GetMapping("/conversaciones") public List<MensajeriaDtos.ConversacionResponse> conversaciones(Authentication a, @RequestParam(required=false) String q) { return service.listar(a.getName(), q); }
    @GetMapping("/conversaciones/{productorId}") public MensajeriaDtos.ConversacionDetalleResponse conversacion(@PathVariable Long productorId, Authentication a) { return service.conversacionDeProveedor(productorId, a.getName()); }
    @PostMapping("/conversaciones/{productorId}/mensajes") public MensajeriaDtos.MensajeResponse enviar(@PathVariable Long productorId, @Valid @RequestBody MensajeriaDtos.EnviarMensajeRequest r, Authentication a) { return service.enviarAProveedor(productorId, r, a.getName()); }
    @PostMapping("/avisos") public MensajeriaDtos.AvisoEnviadoResponse enviarAviso(@Valid @RequestBody MensajeriaDtos.EnviarAvisoRequest r, Authentication a) { return service.enviarAviso(r, a.getName()); }
    @GetMapping("/mi-conversacion") public MensajeriaDtos.ConversacionDetalleResponse mia(Authentication a) { return service.miConversacion(a.getName()); }
    /** Resumen para la pantalla de inicio del proveedor: nuevos y ultimos mensajes, sin marcarlos como leidos. */
    @GetMapping("/mi-conversacion/bandeja") public MensajeriaDtos.ConversacionDetalleResponse miBandeja(Authentication a) { return service.miBandeja(a.getName()); }
    @PostMapping("/mi-conversacion/mensajes") public MensajeriaDtos.MensajeResponse enviarMio(@Valid @RequestBody MensajeriaDtos.EnviarMensajeRequest r, Authentication a) { return service.enviarComoProveedor(r, a.getName()); }
}
