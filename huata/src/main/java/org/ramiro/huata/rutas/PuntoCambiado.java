package org.ramiro.huata.rutas;

/** Incluye los destinatarios anteriores para retirar un punto que cambia de ruta/asignación. */
record PuntoCambiado(String tipo, PuntoEstablecidoResponse punto, Long rutaAnterior,
                     String usuarioAnterior, String usuarioActual) {}
