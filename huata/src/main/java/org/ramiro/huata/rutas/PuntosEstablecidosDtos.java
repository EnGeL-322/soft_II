package org.ramiro.huata.rutas;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * "Puntos establecidos" (pedido del cliente): puntos de referencia que administracion crea a mano en
 * el mapa, por ruta, con acopiador y proveedor asociados opcionales. Ver {@link PuntoEstablecido}.
 */
record PuntoEstablecidoRequest(
        @NotBlank String nombre,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitud,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitud,
        @NotNull Long rutaId,
        Long acopiadorId,
        Long proveedorId,
        String observacion) {}

record PuntoEstablecidoResponse(
        Long id,
        String nombre,
        Double latitud,
        Double longitud,
        Long rutaId,
        String rutaNombre,
        String zona,
        Long acopiadorId,
        String acopiadorNombre,
        Long proveedorId,
        String proveedorCodigo,
        String proveedorNombre,
        /**
         * Estado del proveedor enlazado. La app movil lo necesita para decidir si este punto es una
         * parada mas de la ruta del dia (y con que numero pintarlo) o solo una referencia en el mapa:
         * antes el punto llegaba suelto y el mapa lo dibujaba sin numero aunque su proveedor si
         * estuviera en la lista.
         */
        org.ramiro.huata.productores.EstadoProductor proveedorEstado,
        /** Orden de visita del proveedor enlazado, o null si el punto no tiene proveedor. */
        Integer proveedorOrdenEnRuta,
        String observacion,
        boolean activo,
        String creadoPor,
        java.time.Instant fechaCreacion,
        Long version,
        java.time.Instant actualizadoEn,
        ProveedorPunto proveedor) {

    static PuntoEstablecidoResponse from(PuntoEstablecido p) {
        return new PuntoEstablecidoResponse(
                p.getId(),
                p.getNombre(),
                p.getLatitud(),
                p.getLongitud(),
                p.getRuta().getId(),
                p.getRuta().getNombre(),
                p.getRuta().getZona(),
                p.getAcopiador() != null ? p.getAcopiador().getId() : null,
                p.getAcopiador() != null ? p.getAcopiador().getNombres() + " " + p.getAcopiador().getApellidos() : null,
                p.getProveedor() != null ? p.getProveedor().getId() : null,
                p.getProveedor() != null ? p.getProveedor().getCodigo() : null,
                p.getProveedor() != null ? p.getProveedor().getNombres() + " " + p.getProveedor().getApellidos() : null,
                p.getProveedor() != null ? p.getProveedor().getEstado() : null,
                p.getProveedor() != null ? p.getProveedor().getOrdenEnRuta() : null,
                p.getObservacion(),
                p.isActivo(),
                p.getCreatedBy(),
                p.getCreatedAt(),
                p.getVersion(),
                p.getUpdatedAt(),
                p.getProveedor() == null ? null : new ProveedorPunto(p.getProveedor().getId(),
                        p.getProveedor().getCodigo(),
                        p.getProveedor().getNombres() + " " + p.getProveedor().getApellidos()));
    }
}

record ProveedorPunto(Long id, String codigo, String nombre) {}

record EstadoPuntoRequest(@NotNull Boolean activo) {}

/** Lista activa y bajas versionadas, obtenidas en la misma transacción para resincronizar. */
record SnapshotPuntosResponse(java.util.List<PuntoEstablecidoResponse> puntos,
                             java.util.List<PuntoEstablecidoResponse> bajas) {}
