package org.ramiro.huata.parametros;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de ubicacion del acopio (V57): si el acopiador debe estar dentro del limite de su zona
 * para empezar el recorrido y registrar leche (geocerca), y si debe estar cerca del proveedor.
 *
 * <p>Existen para poder apagarlas en una demostracion sin perderlas (pedido del cliente,
 * 2026-10-25) y volver a encenderlas desde "Precios y calidad" sin tocar codigo. Mismo patron que
 * {@link ReglasLecheService}: se guardan en {@code parametros_negocio} pero se leen TIPADAS, y si una
 * clave falta o tiene basura rige la regla estricta (encendida), que es la vigente.
 */
@Service
@RequiredArgsConstructor
public class ReglasAcopioService {

    static final String EXIGIR_GEOCERCA = "ACOPIO_EXIGIR_GEOCERCA";
    static final String EXIGIR_DISTANCIA = "ACOPIO_EXIGIR_DISTANCIA";
    static final String DISTANCIA_MAXIMA = "RECOJO_DISTANCIA_MAXIMA_METROS";

    /** RN-25: 200 m entre el GPS del acopiador y la ubicacion del proveedor. */
    public static final int DISTANCIA_POR_DEFECTO = 200;

    private final ParametroNegocioRepository repository;
    private final AuditoriaService auditoriaService;

    /**
     * @param exigirGeocerca solo se empieza el recorrido y se registra leche dentro del limite de la zona
     * @param exigirDistancia la app no deja registrar lejos del proveedor (con conexion); el backend igual
     *     marca para revision el recojo que supera la distancia
     * @param distanciaMaximaMetros hasta cuantos metros del proveedor se considera "en su punto"
     */
    public record ReglasAcopio(
            @NotNull Boolean exigirGeocerca,
            @NotNull Boolean exigirDistancia,
            @NotNull @Min(20) @Max(5000) Integer distanciaMaximaMetros) {}

    public ReglasAcopio vigentes() {
        return new ReglasAcopio(
                siNo(EXIGIR_GEOCERCA, true), siNo(EXIGIR_DISTANCIA, true), entero(DISTANCIA_MAXIMA, DISTANCIA_POR_DEFECTO));
    }

    @Transactional
    public ReglasAcopio guardar(ReglasAcopio reglas) {
        ReglasAcopio antes = vigentes();
        escribir(EXIGIR_GEOCERCA, reglas.exigirGeocerca() ? "SI" : "NO",
                "Si el acopiador debe estar dentro del limite de su zona para empezar el recorrido y registrar leche.");
        escribir(EXIGIR_DISTANCIA, reglas.exigirDistancia() ? "SI" : "NO",
                "Si la app impide registrar leche lejos del proveedor (mas de RECOJO_DISTANCIA_MAXIMA_METROS).");
        escribir(DISTANCIA_MAXIMA, String.valueOf(reglas.distanciaMaximaMetros()),
                "Distancia maxima (metros) entre el GPS del acopiador y la ultima ubicacion del proveedor.");
        ReglasAcopio despues = vigentes();
        auditoriaService.registrar("Reglas de acopio", "Parametros", antes + " -> " + despues);
        return despues;
    }

    private void escribir(String clave, String valor, String descripcion) {
        ParametroNegocio p = repository.findByClave(clave).orElseGet(ParametroNegocio::new);
        p.setClave(clave);
        p.setValor(valor);
        if (p.getDescripcion() == null) {
            p.setDescripcion(descripcion);
        }
        repository.save(p);
    }

    private String texto(String clave) {
        return repository.findByClave(clave).map(ParametroNegocio::getValor).map(String::trim).orElse("");
    }

    private int entero(String clave, int porDefecto) {
        try {
            int v = Integer.parseInt(texto(clave));
            return v > 0 ? v : porDefecto;
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private boolean siNo(String clave, boolean porDefecto) {
        String v = texto(clave).toUpperCase();
        if (v.equals("SI") || v.equals("SÍ") || v.equals("TRUE")) return true;
        if (v.equals("NO") || v.equals("FALSE")) return false;
        return porDefecto;
    }
}
