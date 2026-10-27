package org.ramiro.huata.productores;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * "Es su propia ficha", para los endpoints que el proveedor usa desde su app con el id en la URL
 * (ubicacion, solicitud de cambio de zona). Sin esta comprobacion, isAuthenticated() dejaba a
 * cualquier cuenta —incluida la de otro proveedor— escribir sobre una ficha ajena cambiando el id.
 * Se usa desde @PreAuthorize como {@code @accesoProveedor.esPropio(#id, authentication)}.
 */
@Component("accesoProveedor")
@RequiredArgsConstructor
public class AccesoProveedor {

    private final ProductorRepository productorRepository;

    public boolean esPropio(Long productorId, Authentication authentication) {
        if (productorId == null || authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return productorRepository
                .findByUsuario_UsernameIgnoreCase(authentication.getName())
                .map(p -> productorId.equals(p.getId()))
                .orElse(false);
    }
}
