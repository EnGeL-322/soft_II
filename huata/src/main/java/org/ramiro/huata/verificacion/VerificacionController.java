package org.ramiro.huata.verificacion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.usuarios.Permiso;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pide el codigo de verificacion de 6 digitos para una accion sensible.
 *
 * <p>El permiso exigido depende del {@code proposito}, no es uno solo para todo el endpoint: pedir
 * el codigo es el primer paso de la accion protegida, asi que quien no puede ejecutar esa accion
 * tampoco tiene por que poder disparar el correo que la habilita. Antes de que existiera mas de un
 * proposito esto era un {@code @PreAuthorize} fijo en {@code parametros.write}; con un segundo
 * proposito (reapertura de pagos) eso hubiera dejado que cualquiera con permiso de precios pidiera
 * tambien codigos para reabrir un pago, que es una accion de otro modulo con su propio permiso.
 *
 * <p>La respuesta nunca trae el codigo, solo a donde se envio y cuando vence. Devolverlo convertiria
 * toda esta comprobacion en un adorno: bastaria leer la respuesta de la propia peticion.
 */
@RestController
@RequestMapping("/api/v1/verificacion")
@RequiredArgsConstructor
public class VerificacionController {

    private final VerificacionService verificacionService;

    record SolicitarCodigoRequest(@NotNull PropositoVerificacion proposito) {}

    @PostMapping("/codigo")
    @PreAuthorize("isAuthenticated()")
    public VerificacionService.CodigoEmitido solicitar(@Valid @RequestBody SolicitarCodigoRequest request) {
        exigirPermisoDelProposito(request.proposito());
        return verificacionService.solicitar(request.proposito());
    }

    /** Cada proposito nuevo se agrega aqui junto con su permiso, ver {@link PropositoVerificacion}. */
    private void exigirPermisoDelProposito(PropositoVerificacion proposito) {
        String permisoRequerido =
                switch (proposito) {
                    case PRECIOS -> Permiso.PARAMETROS_WRITE;
                    case REAPERTURA_PAGO -> Permiso.PAGOS_REABRIR;
                };
        boolean autorizado = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permisoRequerido));
        if (!autorizado) {
            throw new AccessDeniedException("No tiene permiso para pedir un código con este propósito.");
        }
    }
}
