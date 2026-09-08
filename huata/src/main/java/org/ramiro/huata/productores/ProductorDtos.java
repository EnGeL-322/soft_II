package org.ramiro.huata.productores;

import jakarta.validation.constraints.NotBlank;

record ProductorRequest(
        @NotBlank String codigo,
        @NotBlank String nombres,
        @NotBlank String apellidos,
        String documentoIdentidad,
        String telefono,
        Long rutaId,
        Long centroAcopioId,
        String notas) {}

record ProductorEstadoRequest(EstadoProductor estado, String motivo) {}

record ProductorResponse(
        Long id,
        String codigo,
        String nombres,
        String apellidos,
        String documentoIdentidad,
        String telefono,
        Long rutaId,
        String rutaNombre,
        Long centroAcopioId,
        String centroAcopioNombre,
        EstadoProductor estado,
        String notas,
        boolean activo) {

    static ProductorResponse from(Productor p) {
        return new ProductorResponse(
                p.getId(),
                p.getCodigo(),
                p.getNombres(),
                p.getApellidos(),
                p.getDocumentoIdentidad(),
                p.getTelefono(),
                p.getRuta() != null ? p.getRuta().getId() : null,
                p.getRuta() != null ? p.getRuta().getNombre() : null,
                p.getCentroAcopio() != null ? p.getCentroAcopio().getId() : null,
                p.getCentroAcopio() != null ? p.getCentroAcopio().getNombre() : null,
                p.getEstado(),
                p.getNotas(),
                p.isActivo());
    }
}
