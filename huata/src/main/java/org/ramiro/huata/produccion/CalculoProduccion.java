package org.ramiro.huata.produccion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ramiro.huata.inventario.MateriaPrima;

/**
 * La aritmetica de "cuanto lleva y cuanto alcanza" de una receta, en un solo sitio: la usan tanto la
 * capacidad que ve la pantalla ({@link RecetaService#capacidad}) como el consumo que descuenta
 * {@code QueseriaService.crearLote}. Si cada uno hiciera su propia cuenta, la pantalla podria prometer
 * 15 quesos y el servidor descontar lo de 16.
 *
 * <p>Sin conversion de unidades a proposito: la receta guarda cuanto lleva una unidad <b>en la unidad
 * de su materia prima</b> (RecetaItem no tiene unidad propia) y el stock de esa materia prima esta en
 * la misma unidad, asi que dividir uno entre otro es directo.
 */
public final class CalculoProduccion {

    private CalculoProduccion() {}

    /** Lo que se descuenta de una materia prima. */
    public record Consumo(MateriaPrima materiaPrima, BigDecimal cantidad) {}

    /**
     * Cuanto lleva UNA unidad de cada materia prima, sumando si la receta la repite. En el orden en
     * que aparece en la receta, que es el que se muestra.
     */
    public static Map<MateriaPrima, BigDecimal> porUnidad(RecetaQueso receta) {
        Map<Long, MateriaPrima> materias = new LinkedHashMap<>();
        Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();
        for (RecetaItem item : receta.getItems()) {
            Long id = item.getMateriaPrima().getId();
            materias.putIfAbsent(id, item.getMateriaPrima());
            cantidades.merge(id, item.getCantidadPorUnidad(), BigDecimal::add);
        }
        Map<MateriaPrima, BigDecimal> resultado = new LinkedHashMap<>();
        materias.forEach((id, materia) -> resultado.put(materia, cantidades.get(id)));
        return resultado;
    }

    /**
     * Lo que se descuenta para producir {@code unidades}: receta x unidades. Ordenado por id de
     * materia prima, que es el orden en que se bloquean las filas al consumir (el mismo para todos los
     * lotes, asi dos lotes simultaneos no se bloquean en cruz).
     */
    public static List<Consumo> consumoPara(RecetaQueso receta, int unidades) {
        if (unidades <= 0) {
            throw new IllegalArgumentException("La cantidad a producir debe ser mayor a cero");
        }
        BigDecimal factor = BigDecimal.valueOf(unidades);
        return porUnidad(receta).entrySet().stream()
                .map(e -> new Consumo(e.getKey(), e.getValue().multiply(factor)))
                .sorted(Comparator.comparing(c -> c.materiaPrima().getId()))
                .toList();
    }

    /**
     * Para cuantas unidades completas alcanza un stock: se redondea hacia abajo porque media unidad
     * de queso no se puede vender. Un stock nulo, cero o negativo no alcanza para ninguna.
     */
    public static int alcanzaPara(BigDecimal stock, BigDecimal porUnidad) {
        if (stock == null || stock.signum() <= 0 || porUnidad == null || porUnidad.signum() <= 0) {
            return 0;
        }
        BigDecimal unidades = stock.divide(porUnidad, 0, RoundingMode.FLOOR);
        // Tope en vez de desbordar: un insumo que "alcanza para 2 mil millones" nunca es el limitante.
        return unidades.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : unidades.intValue();
    }
}
