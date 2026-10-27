package org.ramiro.huata.recojo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ramiro.huata.tiemporeal.AcopioTiempoRealHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Empuja a "Conciliacion planta" la fila recalculada de la ruta que acaba de moverse, para que el
 * acopio que el acopiador registra desde la app aparezca en la web sin recargar (RF-53, RF-54).
 *
 * <p><b>AFTER_COMMIT y no el retorno del servicio.</b> Publicar dentro de la transaccion mostraria
 * litros de una escritura que todavia puede revertirse; el navegador no tiene forma de enterarse de
 * un rollback y se quedaria con un total que no existe en la base.
 *
 * <p><b>Un fallo aqui no rompe la escritura.</b> El recojo ya esta guardado y confirmado: si el
 * socket falla, lo unico que se pierde es la actualizacion instantanea, y la pantalla la recupera en
 * su siguiente carga. Propagar la excepcion no desharia nada (el commit ya paso) y solo devolveria
 * un error al acopiador por un recojo que si se guardo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AcopioTiempoRealPublisher {

    private final ConciliacionRutaService conciliacionRutaService;
    private final AcopioTiempoRealHandler canal;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void alCambiarElAcopio(AcopioCambioEvent evento) {
        if (evento.rutaId() == null) {
            // Recepcion en planta sin ruta: no pertenece a ninguna fila de la conciliacion.
            return;
        }
        try {
            conciliacionRutaService
                    .deFechaYRuta(evento.fecha(), evento.rutaId())
                    .ifPresent(fila -> canal.publicar(new EventoAcopioTiempoReal(evento.tipo(), fila)));
        } catch (RuntimeException e) {
            log.warn(
                    "No se pudo difundir el cambio de acopio de la ruta {} del {}: {}",
                    evento.rutaId(),
                    evento.fecha(),
                    e.toString());
        }
    }
}
