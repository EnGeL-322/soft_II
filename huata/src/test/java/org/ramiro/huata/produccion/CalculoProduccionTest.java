package org.ramiro.huata.produccion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.ramiro.huata.inventario.MateriaPrima;

/** La aritmetica de "cuanto lleva y para cuanto alcanza", sin base de datos. */
class CalculoProduccionTest {

    private static MateriaPrima materia(long id, String nombre) {
        MateriaPrima m = new MateriaPrima();
        m.setId(id);
        m.setNombre(nombre);
        return m;
    }

    private static RecetaQueso receta(Object[]... items) {
        RecetaQueso receta = new RecetaQueso();
        for (Object[] i : items) {
            RecetaItem item = new RecetaItem();
            item.setReceta(receta);
            item.setMateriaPrima((MateriaPrima) i[0]);
            item.setCantidadPorUnidad(new BigDecimal((String) i[1]));
            receta.getItems().add(item);
        }
        return receta;
    }

    @Test
    void alcanzaParaRedondeaHaciaAbajoPorqueMediaUnidadNoSeVende() {
        assertThat(CalculoProduccion.alcanzaPara(new BigDecimal("0.75"), new BigDecimal("0.05"))).isEqualTo(15);
        assertThat(CalculoProduccion.alcanzaPara(new BigDecimal("0.79"), new BigDecimal("0.05"))).isEqualTo(15);
        assertThat(CalculoProduccion.alcanzaPara(new BigDecimal("100"), new BigDecimal("6"))).isEqualTo(16);
    }

    @Test
    void sinStockOConStockNegativoNoAlcanzaParaNinguna() {
        assertThat(CalculoProduccion.alcanzaPara(BigDecimal.ZERO, BigDecimal.ONE)).isZero();
        assertThat(CalculoProduccion.alcanzaPara(new BigDecimal("-3"), BigDecimal.ONE)).isZero();
        assertThat(CalculoProduccion.alcanzaPara(null, BigDecimal.ONE)).isZero();
    }

    @Test
    void unStockEnormeNoDesbordaElEntero() {
        assertThat(CalculoProduccion.alcanzaPara(new BigDecimal("1000000000"), new BigDecimal("0.0001")))
                .isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void elConsumoEsRecetaPorCantidad() {
        MateriaPrima leche = materia(1, "Leche");
        MateriaPrima sal = materia(2, "Sal");

        List<CalculoProduccion.Consumo> consumo =
                CalculoProduccion.consumoPara(receta(new Object[] {leche, "6"}, new Object[] {sal, "0.05"}), 20);

        assertThat(consumo).extracting(c -> c.materiaPrima().getNombre()).containsExactly("Leche", "Sal");
        assertThat(consumo.get(0).cantidad()).isEqualByComparingTo("120");
        assertThat(consumo.get(1).cantidad()).isEqualByComparingTo("1.00");
    }

    /**
     * Si la receta repite un insumo, se suma: contarlo dos veces por separado verificaria el stock de
     * cada mitad contra el total y dejaria pasar un lote que no alcanza.
     */
    @Test
    void unInsumoRepetidoEnLaRecetaSeSuma() {
        MateriaPrima sal = materia(2, "Sal");

        List<CalculoProduccion.Consumo> consumo =
                CalculoProduccion.consumoPara(receta(new Object[] {sal, "0.02"}, new Object[] {sal, "0.03"}), 10);

        assertThat(consumo).hasSize(1);
        assertThat(consumo.get(0).cantidad()).isEqualByComparingTo("0.50");
    }

    /** El orden de bloqueo es por id, no el de la receta: es el que evita bloqueos cruzados entre lotes. */
    @Test
    void elConsumoSaleOrdenadoPorIdDeMateriaPrima() {
        MateriaPrima sal = materia(9, "Sal");
        MateriaPrima leche = materia(3, "Leche");

        List<CalculoProduccion.Consumo> consumo =
                CalculoProduccion.consumoPara(receta(new Object[] {sal, "1"}, new Object[] {leche, "1"}), 1);

        assertThat(consumo).extracting(c -> c.materiaPrima().getId()).containsExactly(3L, 9L);
    }

    @Test
    void noSeCalculaConsumoParaCeroUnidades() {
        assertThatThrownBy(() -> CalculoProduccion.consumoPara(receta(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
