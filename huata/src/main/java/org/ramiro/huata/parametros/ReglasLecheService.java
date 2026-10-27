package org.ramiro.huata.parametros;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.ramiro.huata.auditoria.AuditoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas configurables de la leche (V33): cuando se retira a un proveedor por mala calidad, si la
 * leche observada cuenta como mala calidad y si entra al inventario, y cuanto cuesta la leche para
 * producir.
 *
 * <p>Se guardan en {@code parametros_negocio}, pero se leen y escriben TIPADAS: un valor mal escrito
 * ("dos" en vez de 2) no puede llegar a la regla que retira proveedores. Si una clave falta o tiene
 * basura, se usa el valor por defecto, que es la regla vigente (RN-02, RN-07).
 */
@Service
@RequiredArgsConstructor
public class ReglasLecheService {

    static final String DETECCIONES_RETIRO = "MALA_CALIDAD_DETECCIONES_RETIRO";
    static final String INCLUYE_OBSERVADA = "MALA_CALIDAD_INCLUYE_OBSERVADA";
    static final String OBSERVADA_A_INVENTARIO = "LECHE_OBSERVADA_A_INVENTARIO";
    static final String COSTO_LECHE = "COSTO_LECHE_LITRO";

    /** Por defecto: RN-07, retiro a la segunda deteccion. */
    static final int DETECCIONES_POR_DEFECTO = 2;

    private final ParametroNegocioRepository repository;
    private final AuditoriaService auditoriaService;

    /**
     * @param deteccionesParaRetiro con cuantas detecciones de mala calidad el proveedor queda
     *     Retirado; 0 = nunca de forma automatica
     * @param observadaEsMalaCalidad si una leche OBSERVADA cuenta como deteccion (la RECHAZADA siempre)
     * @param observadaEntraAInventario si la leche OBSERVADA entra al almacen (la RECHAZADA nunca)
     * @param costoLecheLitro costo por litro para producir; null = tarifa normal vigente del dia
     */
    public record ReglasLeche(
            @NotNull @Min(0) @Max(20) Integer deteccionesParaRetiro,
            @NotNull Boolean observadaEsMalaCalidad,
            @NotNull Boolean observadaEntraAInventario,
            @Positive BigDecimal costoLecheLitro) {}

    public ReglasLeche vigentes() {
        return new ReglasLeche(
                entero(DETECCIONES_RETIRO, DETECCIONES_POR_DEFECTO),
                siNo(INCLUYE_OBSERVADA, true),
                siNo(OBSERVADA_A_INVENTARIO, true),
                decimal(COSTO_LECHE));
    }

    @Transactional
    public ReglasLeche guardar(ReglasLeche reglas) {
        ReglasLeche antes = vigentes();
        escribir(DETECCIONES_RETIRO, String.valueOf(reglas.deteccionesParaRetiro()),
                "Detecciones de mala calidad con las que el proveedor queda Retirado automaticamente. 0 = nunca.");
        escribir(INCLUYE_OBSERVADA, reglas.observadaEsMalaCalidad() ? "SI" : "NO",
                "Si la leche OBSERVADA cuenta como deteccion de mala calidad (la RECHAZADA siempre cuenta).");
        escribir(OBSERVADA_A_INVENTARIO, reglas.observadaEntraAInventario() ? "SI" : "NO",
                "Si la leche OBSERVADA entra al inventario. La RECHAZADA nunca entra.");
        escribir(COSTO_LECHE, reglas.costoLecheLitro() == null ? "" : reglas.costoLecheLitro().toPlainString(),
                "Costo por litro de la leche del acopio para producir. Vacio = tarifa normal vigente del dia.");
        ReglasLeche despues = vigentes();
        // Cambian quien sigue entregando leche y cuanto cuesta producir: se audita con el antes y el despues.
        auditoriaService.registrar("Reglas de la leche", "Parametros", antes + " -> " + despues);
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
            return v >= 0 ? v : porDefecto;
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

    private BigDecimal decimal(String clave) {
        try {
            String v = texto(clave);
            if (v.isEmpty()) return null;
            BigDecimal d = new BigDecimal(v);
            return d.signum() > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
