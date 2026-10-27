package org.ramiro.huata.produccion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record TrabajadorProduccionRequest(@NotBlank @Size(max = 150) String nombre) {}

record TrabajadorProduccionResponse(Long id, String nombre) {

    static TrabajadorProduccionResponse from(TrabajadorProduccion t) {
        return new TrabajadorProduccionResponse(t.getId(), t.getNombre());
    }
}
