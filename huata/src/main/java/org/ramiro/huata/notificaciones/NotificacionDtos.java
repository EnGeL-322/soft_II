package org.ramiro.huata.notificaciones;

record NotificacionResponse(
        Long id, String titulo, String mensaje, String entidadTipo, Long entidadId, boolean leida, java.time.Instant fecha) {
    static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(
                n.getId(), n.getTitulo(), n.getMensaje(), n.getEntidadTipo(), n.getEntidadId(), n.isLeida(), n.getCreatedAt());
    }
}
