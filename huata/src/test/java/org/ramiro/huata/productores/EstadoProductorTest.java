package org.ramiro.huata.productores;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Maquina de estados del proveedor (DESARROLLO.md 7.3), probada como logica pura.
 *
 * <p>Se recorre la matriz COMPLETA —los 25 pares posibles— y no solo los caminos felices. Un test
 * que solo comprueba lo que si se permite deja pasar el error que de verdad importa aqui: una
 * transicion de mas. Reactivar a un retirado o aprobar un alta ya inactiva son exactamente el tipo
 * de salto que un enum sin maquina de estados permite sin que nadie se entere.
 */
class EstadoProductorTest {

    /** Las unicas transiciones manuales permitidas. Cualquier otra debe estar prohibida. */
    private static final Set<List<EstadoProductor>> PERMITIDAS = Set.of(
            List.of(EstadoProductor.PENDIENTE_APROBACION, EstadoProductor.ACTIVO),
            List.of(EstadoProductor.PENDIENTE_APROBACION, EstadoProductor.INACTIVO),
            List.of(EstadoProductor.ACTIVO, EstadoProductor.EN_REVISION),
            List.of(EstadoProductor.ACTIVO, EstadoProductor.INACTIVO),
            List.of(EstadoProductor.EN_REVISION, EstadoProductor.ACTIVO),
            List.of(EstadoProductor.EN_REVISION, EstadoProductor.INACTIVO),
            List.of(EstadoProductor.INACTIVO, EstadoProductor.ACTIVO),
            // RN-07, decidido por el cliente el 2026-09-27: retiro manual por administracion.
            List.of(EstadoProductor.ACTIVO, EstadoProductor.RETIRADO),
            List.of(EstadoProductor.EN_REVISION, EstadoProductor.RETIRADO));

    static Stream<Arguments> todosLosPares() {
        return Stream.of(EstadoProductor.values())
                .flatMap(desde -> Stream.of(EstadoProductor.values()).map(hasta -> Arguments.of(desde, hasta)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("todosLosPares")
    void laMatrizCompletaDeTransicionesEsLaDeclarada(EstadoProductor desde, EstadoProductor hasta) {
        // Arrange
        boolean esperado = PERMITIDAS.contains(List.of(desde, hasta));

        // Act
        boolean real = desde.permiteIr(hasta);

        // Assert
        assertThat(real).as("%s -> %s", desde, hasta).isEqualTo(esperado);
    }

    @Test
    void soloElProveedorActivoPuedeEntregarLeche() {
        // 7.3: "un proveedor que no esta Activo no puede recibir nuevas recepciones".
        for (EstadoProductor estado : EstadoProductor.values()) {
            assertThat(estado.puedeEntregar())
                    .as("puedeEntregar de %s", estado)
                    .isEqualTo(estado == EstadoProductor.ACTIVO);
        }
    }

    @Test
    void unAltaNaceActivaYPuedeEntregar() {
        // Decision del cliente (2026-10-26): sin segundo paso de aprobacion.
        assertThat(EstadoProductor.inicial()).isEqualTo(EstadoProductor.ACTIVO);
        assertThat(EstadoProductor.inicial().puedeEntregar()).isTrue();
    }

    @Test
    void retiradoEsTerminal() {
        // Se llega por mala calidad (automatico) o por decision de administracion, pero no se sale.
        assertThat(EstadoProductor.RETIRADO.destinosPosibles()).isEmpty();
    }

    /**
     * RN-07, decidido por el cliente el 2026-09-27: administracion puede retirar a mano a quien esta
     * trabajando (ACTIVO) o en revision. Un alta pendiente o un proveedor inactivo no se retiran:
     * no llegaron a entregar o ya no entregan.
     */
    @Test
    void soloSeRetiraAQuienEstaActivoOEnRevision() {
        for (EstadoProductor desde : EstadoProductor.values()) {
            boolean esperado = desde == EstadoProductor.ACTIVO || desde == EstadoProductor.EN_REVISION;
            assertThat(desde.permiteIr(EstadoProductor.RETIRADO))
                    .as("%s -> RETIRADO", desde)
                    .isEqualTo(esperado);
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoProductor.class)
    void ningunEstadoTransitaHaciaSiMismo(EstadoProductor estado) {
        assertThat(estado.permiteIr(estado)).isFalse();
    }

    @Test
    void ningunEstadoUtilQuedaSinSalida() {
        // Un estado sin salida que no sea el terminal es una trampa: el proveedor se queda ahi para
        // siempre. Es lo que pasaria con EN_REVISION si se copiara el diagrama de 7.3 al pie de la
        // letra, porque no dibuja como se sale de el.
        for (EstadoProductor estado : EstadoProductor.values()) {
            if (estado != EstadoProductor.RETIRADO) {
                assertThat(estado.destinosPosibles()).as("salidas de %s", estado).isNotEmpty();
            }
        }
    }

    @Test
    void todoEstadoDistintoDeRetiradoPuedeVolverAActivo() {
        // Si un estado no pudiera volver nunca a ACTIVO, el proveedor quedaria excluido de por vida
        // por una decision administrativa reversible, que no es lo que describe 7.3.
        for (EstadoProductor estado : EstadoProductor.values()) {
            if (estado != EstadoProductor.RETIRADO && estado != EstadoProductor.ACTIVO) {
                assertThat(estado.permiteIr(EstadoProductor.ACTIVO))
                        .as("%s -> ACTIVO", estado)
                        .isTrue();
            }
        }
    }
}
