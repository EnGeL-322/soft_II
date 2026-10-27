package org.ramiro.huata.calidad;

import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.ramiro.huata.notificaciones.NotificacionService;
import org.ramiro.huata.parametros.ReglasLecheService;
import org.ramiro.huata.parametros.ReglasLecheService.ReglasLeche;
import org.ramiro.huata.productores.EstadoProductor;
import org.ramiro.huata.productores.Productor;
import org.ramiro.huata.productores.ProductorRepository;
import org.ramiro.huata.recojo.EstadoRecojo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retiro del proveedor por mala calidad (RN-07), decision del cliente del 2026-09-27: "se paga una
 * cantidad baja por la leche de mala calidad y se le retira como proveedor".
 *
 * <p>La leche de mala calidad ya se pagaba menos (OBSERVADA a tarifa reducida, RECHAZADA a cero:
 * {@code LiquidacionService.precioDe}). Lo que faltaba era el retiro, que hasta aqui era
 * inalcanzable a la espera de esta decision.
 *
 * <p>Es configurable ("Reglas de la leche"): con cuantas detecciones se retira (0 = nunca), y si la
 * OBSERVADA cuenta como deteccion (la RECHAZADA siempre cuenta). Se cuentan los controles de sus
 * entregas no anuladas; recalcularlo cada vez hace que corregir un control no cuente dos veces.
 *
 * <p>Retirar es terminal: el proveedor deja de poder entregar ({@code exigirQuePuedeEntregar}). Queda
 * en la bitacora y se avisa en la campana.
 */
@Service
@RequiredArgsConstructor
public class RetiroPorMalaCalidadService {

    private final ControlCalidadRepository controles;
    private final ProductorRepository productores;
    private final ReglasLecheService reglasLeche;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    /** Resultados que cuentan como deteccion de mala calidad con las reglas dadas. */
    public static Set<ResultadoCalidad> malaCalidad(ReglasLeche reglas) {
        return reglas.observadaEsMalaCalidad()
                ? EnumSet.of(ResultadoCalidad.RECHAZADA, ResultadoCalidad.OBSERVADA)
                : EnumSet.of(ResultadoCalidad.RECHAZADA);
    }

    public long detecciones(Productor productor, ReglasLeche reglas) {
        return controles.countByRecojo_ProductorIdAndResultadoInAndRecojo_EstadoNot(
                productor.getId(), malaCalidad(reglas), EstadoRecojo.CANCELLED);
    }

    /**
     * Tras registrar o corregir un control: si el proveedor alcanzo el limite, queda Retirado.
     * Devuelve true si se le retiro en esta llamada.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean evaluar(Productor productor) {
        if (productor == null || productor.getEstado() == EstadoProductor.RETIRADO) {
            return false;
        }
        ReglasLeche reglas = reglasLeche.vigentes();
        int limite = reglas.deteccionesParaRetiro();
        if (limite <= 0) {
            return false;
        }
        long cuenta = detecciones(productor, reglas);
        if (cuenta < limite) {
            return false;
        }
        EstadoProductor anterior = productor.getEstado();
        productor.setEstado(EstadoProductor.RETIRADO);
        String motivo = "Retiro automático: " + cuenta + " detección(es) de mala calidad (límite " + limite + ").";
        String notaPrevia = productor.getNotas() == null ? "" : productor.getNotas() + "\n";
        productor.setNotas(notaPrevia + "[RETIRADO] " + motivo);
        productores.save(productor);
        String quien = productor.getCodigo() + " · " + productor.getNombres() + " " + productor.getApellidos();
        auditoria.registrar("Cambio de estado", "Proveedores", quien + " · " + anterior + " -> RETIRADO · " + motivo);
        notificaciones.crear(
                null,
                "Proveedor retirado por mala calidad",
                quien + " quedó retirado: " + cuenta + " detección(es) de mala calidad. Ya no puede entregar leche.",
                "PRODUCTOR",
                productor.getId());
        return true;
    }
}
