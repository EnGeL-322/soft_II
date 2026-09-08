package org.ramiro.huata.recojo;

/**
 * Estados recomendados en 03_ARQUITECTURA_Y_MODELO_SUGERIDO.md. Los que vienen de la app movil
 * offline (Fase 2) llegaran como SAVED_OFFLINE/PENDING_SYNC/SYNCED; lo que se crea directamente
 * desde la web (sin escenario offline) nace en SYNCED.
 */
public enum EstadoRecojo {
    DRAFT,
    SAVED_OFFLINE,
    PENDING_SYNC,
    SYNCED,
    CANCELLED
}
