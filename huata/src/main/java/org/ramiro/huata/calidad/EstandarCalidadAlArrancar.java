package org.ramiro.huata.calidad;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Los analisis que quedaron pendientes antes de la aprobacion automatica (V50) se evaluan una vez al
 * arrancar. Es idempotente: despues de evaluarse, cada uno queda aprobado o con advertencia, y ya no
 * vuelve a entrar en la consulta.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class EstandarCalidadAlArrancar implements ApplicationRunner {

    private final CalidadService calidad;

    @Override
    public void run(ApplicationArguments args) {
        int evaluados = calidad.evaluarPendientesSinEvaluar();
        if (evaluados > 0) {
            log.info("Estandar de calidad: {} analisis pendientes evaluados.", evaluados);
        }
    }
}
